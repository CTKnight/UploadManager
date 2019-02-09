/*
 * Copyright (c) 2019. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager.internal

import android.app.job.JobParameters
import android.content.Context
import android.database.sqlite.SQLiteClosable
import android.net.ConnectivityManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.core.content.getSystemService
import com.zhy.http.okhttp.request.CountingRequestBody
import me.ctknight.uploadmanager.UploadContract
import me.ctknight.uploadmanager.UploadContract.UploadStatus.*
import me.ctknight.uploadmanager.UploadDatabase
import me.ctknight.uploadmanager.UploadJobService
import me.ctknight.uploadmanager.UploadRecord
import me.ctknight.uploadmanager.util.LogUtils
import me.ctknight.uploadmanager.util.OkHttpUtils
import okhttp3.*
import okhttp3.internal.Util
import java.io.FileNotFoundException
import java.io.IOException

internal class UploadThread(
    private val mJobService: UploadJobService,
    private val mClient: OkHttpClient,
    private val mParams: JobParameters,
    @Volatile private var mInfo: UploadRecord.Impl
) : Thread(), CountingRequestBody.Listener {
  private val mContext: Context = mJobService
  private val mNotifier: UploadNotifier = UploadNotifier.getInstance(mContext)
  private val mDatabase: UploadDatabase = Database.getInstance(mContext)
  private val mSystemFacade: SystemFacade = SystemFacade.Impl.getInstance(mContext)
  private val mId: Long = mInfo._ID
  private val connectivityManager: ConnectivityManager = mContext.getSystemService()!!
  // global setting
  private lateinit var mCall: Call
  //  TODO: use this list to record unclosed fds and close them at appropriate time
  private val fdList = ArrayList<ParcelFileDescriptor>(1)
  // upload has started or not
  private var mMadeProgress = false
  private var mLastUpdateBytes: Long = 0
  private var mLastUpdateTime: Long = 0
  //TYPE_NONE
  private var mNetworkType = ConnectivityManager.TYPE_DUMMY
  private var mSpeed: Long = 0
  private var mSpeedSampleStart: Long = 0
  private var mSpeedSampleBytes: Long = 0

  @Volatile
  private var mShutdownRequested: Boolean = false

  override fun run() {
    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
//    connectivityManager.registerDefaultNetworkCallback(object: )
    if (mInfo.Status == SUCCESS) {
      logDebug("run: skipping finished item id: $mId")
      return
    }
    try {
      val info = mSystemFacade.getActiveNetworkInfo()
      if (info != null) {
        mNetworkType = info.type
      }

      executeUpload()

      mInfo = mInfo.copy(Status = SUCCESS)

      if (mInfo.TotalBytes == -1L) {
        mInfo = mInfo.copy(TotalBytes = mInfo.CurrentBytes)
      }
    } catch (e: StopRequestException) {
      if (!mCall.isCanceled) {
        mCall.cancel()
      }
      mInfo = mInfo.copy(ErrorMsg = e.message, Status = e.finalStatus)

      logWarning("run: Stop uploading with Status: ${mInfo.Status}, ErrorMsg: ${mInfo.ErrorMsg}")

      // Nobody below our level should request retries, since we handle
      // failure counts at this level.
      if (mInfo.Status == WAITING_TO_RETRY) {
        throw IllegalStateException("Execution should always throw final error codes")
      }

      if (mInfo.Status.isRetryable()) {
        mInfo = if (mMadeProgress) {
          mInfo.copy(NumFailed = 1)
        } else {
          mInfo.copy(NumFailed = mInfo.NumFailed + 1)
        }

        mInfo = if (mInfo.NumFailed < UploadContract.Constants.MAX_RETRIES) {
          val info = mSystemFacade.getActiveNetworkInfo()
          if (info != null && info.type == mNetworkType && info.isConnected) {
            // Underlying network is still intact, use normal backoff
            mInfo.copy(Status = WAITING_TO_RETRY)
          } else {
            // Network changed, retry on any next available
            mInfo.copy(Status = WAITING_FOR_NETWORK)
          }
        } else {
          mInfo.copy(Status = CAN_NOT_RESUME)
        }
      } else {
        mInfo = mInfo.copy(Status = CAN_NOT_RESUME)
      }

      if (mInfo.Status == WAITING_FOR_NETWORK && mInfo.isMeteredAllowed(mContext)) {
        mInfo = mInfo.copy(Status = WAITING_FOR_WIFI)
      }

    } catch (t: Throwable) {
      mInfo = mInfo.copy(Status = UNKNOWN_ERROR, ErrorMsg = t.toString())
      logError("Failed: ${mInfo.ErrorMsg}", t)
    } finally {
      logDebug("Finished with status ${mInfo.Status}")
      mNotifier.notifyUploadSpeed(mId, 0)
      mInfo.partialUpdate(mDatabase)
      closeFds()
    }

    var needsReschedule = false
    if (mInfo.Status in listOf(WAITING_FOR_WIFI, WAITING_FOR_NETWORK, WAITING_TO_RETRY)) {
      needsReschedule = true
    }
    mJobService.jobFinishedInternal(mParams, needsReschedule)
  }

  @Throws(StopRequestException::class)
  private fun getFileDescriptor(fileUri: Uri): ParcelFileDescriptor {
    try {
      return mContext.contentResolver.openFileDescriptor(fileUri, "r")
          ?: throw FileNotFoundException("The return of openFileDescriptor($fileUri) is null")
    } catch (e: FileNotFoundException) {
      logError("getFileDescriptor: ", e)
      throw StopRequestException(FILE_NOT_FOUND, e)
    }
  }

  override fun onRequestProgress(bytesWritten: Long, contentLength: Long) {
    mMadeProgress = true
    mInfo = mInfo.copy(CurrentBytes = bytesWritten, TotalBytes = contentLength)

    updateProgress()

    if (mShutdownRequested) {
      if (!mCall.isCanceled) {
        mCall.cancel()
      }
    }
  }

  @Throws(StopRequestException::class)
  private fun executeUpload() {
    checkConnectivity()
    uploadData().use { response ->
      val responseMsg = response.body()?.string()
      recordResponse(responseMsg)
      if (!response.isSuccessful) {
        StopRequestException.throwUnhandledHttpError(response.code(), response.message())
      }
    }
  }

  @Throws(StopRequestException::class)
  private fun checkConnectivity() {
    val info = mSystemFacade.getActiveNetworkInfo()
    if (info == null || !info.isConnected) {
      throw StopRequestException(WAITING_FOR_NETWORK, "Network is not connected")
    }
    if (info.isRoaming && !mInfo.isRoamingAllowed(mContext)) {
      throw StopRequestException(WAITING_FOR_NETWORK, "Waiting for not roaming Network")
    }
    if (connectivityManager.isActiveNetworkMetered && !mInfo.isMeteredAllowed(mContext)) {
      throw StopRequestException(WAITING_FOR_NETWORK, "Waiting for un-metered Wifi")
    }
  }

  @Throws(StopRequestException::class)
  private fun buildRequestBody(): RequestBody {
    val builder = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
    mInfo.Parts.forEach {
      val fileInfo = it.fileInfo
      if (fileInfo != null) {
        val fileDescriptor = getFileDescriptor(fileInfo.fileUri)
        fdList.add(fileDescriptor)
        val fileRequestBody = OkHttpUtils.createRequestFromFile(fileInfo.mimeType, fileDescriptor)
        builder.addFormDataPart(it.name, fileInfo.fileName, fileRequestBody)
      } else {
        builder.addFormDataPart(it.name, it.value!!)
      }
    }

    val realBody = builder.build()
    try {
      setTotalBytes(realBody.contentLength())
    } catch (e: IOException) {
      Log.e(TAG, "buildRequestBody: ", e)
      setTotalBytes(-1)
    }

    return CountingRequestBody(realBody, this)
  }

  private fun buildRequest(): Request {
    val builder = Request.Builder()
    val headers = mInfo.Headers
    if (headers != null) {
      builder.headers(headers)
    }
    val userAgent = mInfo.UserAgent
    if (userAgent != null) {
      builder.header("User-Agent", userAgent)
    }
    return builder
        .url(mInfo.TargeUrl)
        .post(buildRequestBody())
        .build()
  }

  @Throws(StopRequestException::class)
  private fun uploadData(): Response {
    mCall = mClient.newCall(buildRequest())
    try {
      return mCall.execute()
    } catch (e: IOException) {
      throw StopRequestException(HTTP_DATA_ERROR, e)
    } catch (e: IllegalStateException) {
      throw StopRequestException(HTTP_DATA_ERROR, e)
    }
  }

  private fun setTotalBytes(totalBytes: Long) {
    mInfo = mInfo.copy(TotalBytes = totalBytes)
    mInfo.partialUpdate(mDatabase)
  }

  private fun recordResponse(responseMsg: String?) {
    logDebug("executeUpload: $responseMsg")
    mInfo = mInfo.copy(ServerResponse = responseMsg)
    mInfo.partialUpdate(mDatabase)
  }

  private fun updateProgress() {
    val now = SystemClock.elapsedRealtime()
    val currentBytes = mInfo.CurrentBytes

    val sampleDelta = now - mSpeedSampleStart
    if (sampleDelta > 500) {
      val sampleSpeed = (currentBytes - mSpeedSampleBytes) * 1000 / sampleDelta

      mSpeed = if (mSpeed == 0L) {
        sampleSpeed
      } else {
        (mSpeed * 3 + sampleSpeed) / 4
      }

      //only notify after a full sample window time (sampleDelta)
      if (mSpeedSampleStart != 0L) {
        mNotifier.notifyUploadSpeed(mId, mSpeed)
      }
      mSpeedSampleStart = now
      mSpeedSampleBytes = currentBytes
    }

    val bytesDelta = currentBytes - mLastUpdateBytes
    val timeDelta = now - mLastUpdateTime

    if (bytesDelta > UploadContract.Constants.MIN_PROGRESS_STEP && timeDelta > UploadContract.Constants.MIN_PROGRESS_TIME) {
      mInfo.partialUpdate(mDatabase)
      mLastUpdateBytes = currentBytes
      mLastUpdateTime = now
    }
  }

  private fun closeFds() {
    fdList.forEach { Util.closeQuietly(it) }
    fdList.clear()
  }

  internal fun requestShutdown() {
    mShutdownRequested = true
  }

  private fun logDebug(msg: String) {
    Log.d(TAG, "[$mId] $msg")
  }

  private fun logWarning(msg: String) {
    Log.w(TAG, "[$mId] $msg")
  }

  private fun logError(msg: String, t: Throwable) {
    Log.e(TAG, "[$mId] $msg", t)
  }

  companion object {
    private val TAG = LogUtils.makeTag<UploadThread>()
  }
}
