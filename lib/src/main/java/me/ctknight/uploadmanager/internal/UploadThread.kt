/*
 * Copyright (c) 2019. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager.internal

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.zhy.http.okhttp.request.CountingRequestBody
import me.ctknight.uploadmanager.*
import me.ctknight.uploadmanager.UploadContract.UploadStatus.*
import me.ctknight.uploadmanager.util.LogUtils
import me.ctknight.uploadmanager.util.OkHttpUtils
import okhttp3.*
import java.io.FileNotFoundException
import java.io.IOException

internal class UploadThread(
    context: Context,
    private val mNotifier: UploadNotifier,
    private var mInfo: UploadRecord.Impl,
    private val mDatabase: UploadDatabase,
    private val mClient: OkHttpClient
) : Runnable, CountingRequestBody.Listener {
  private val mContext: Context = context.applicationContext
  private val mId: Long = mInfo._ID
  // global setting
  private lateinit var mCall: Call
  //  TODO: use this list to record unclosed fds and close them at appropriate time
  private val fdList = ArrayList<ParcelFileDescriptor>(1)
  // upload has started or not
  private var mMadeProgress = false
  private var mLastUpdateBytes: Long = 0
  private var mLastUpdateTime: Long = 0
  //TYPE_NONE
  private var mNetworkType = -1
  private var mSpeed: Long = 0
  private var mSpeedSampleStart: Long = 0
  private var mSpeedSampleBytes: Long = 0

  override fun run() {
    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
    val connectivityManager = mContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    if (mInfo.Status == SUCCESS) {
      if (BuildConfig.DEBUG) {
        Log.d("UploadThread", "run: skipping finished item id: $mId")
      }
      return
    }

    var wakelock: PowerManager.WakeLock? = null
    val pm = mContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    try {
      wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UploadThread$mId")
      wakelock.acquire()

      val info = connectivityManager.activeNetworkInfo
      if (info != null) {
        mNetworkType = info.type
      }
      if (mInfo.Status.isDeletedOrCanceled() || mInfo.Status.isRetryable()) {
        executeUpload()
      }
      mInfo = mInfo.copy(Status = SUCCESS)

      if (mInfo.TotalBytes == -1L) {
        mInfo = mInfo.copy(TotalBytes = mInfo.CurrentBytes)
      }
    } catch (e: FileNotFoundException) {
      Log.e(TAG, "executeUpload: ", e)
      mInfo = mInfo.copy(Status = FILE_NOT_FOUND, ErrorMsg = e.message)
    } catch (e: UploadException) {
      mInfo = mInfo.copy(ErrorMsg = e.message)

      Log.w(TAG, "run: Stop uploading with Status: ${mInfo.Status}, ErrorMsg: ${mInfo.ErrorMsg}")

      //only we can request retry
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
          val info = connectivityManager.activeNetworkInfo
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

    } catch (t: Throwable) {
      mInfo = mInfo.copy(Status = UNKNOWN_ERROR, ErrorMsg = t.toString())
      Log.e(TAG, "Failed: ${mInfo.ErrorMsg}", t)
    } finally {

      mNotifier.notifyUploadSpeed(mId, 0)


      if (mInfo.Status.isComplete()) {
        if (mInfo.Visibility === UploadContract.Visibility.VISIBLE) {
          mInfo = mInfo.copy(Visibility = UploadContract.Visibility.VISIBLE_COMPLETE)
        }
        mInfo.sendIntentIfRequested(mContext)
      }

      mInfo.partialUpdate(mDatabase)
      closeFds()
      wakelock?.release()
    }
  }

  @Throws(IOException::class)
  private fun getFileDescriptor(fileUri: Uri): ParcelFileDescriptor {
    try {
      return mContext.contentResolver.openFileDescriptor(fileUri, "r")
          ?: throw FileNotFoundException("The return of openFileDescriptor($fileUri) is null")

    } catch (e: FileNotFoundException) {
      Log.e("UploadThread", "getFileDescriptor: ", e)
      throw e
    }
  }

  override fun onRequestProgress(bytesWritten: Long, contentLength: Long) {
    mMadeProgress = true
    mInfo = mInfo.copy(CurrentBytes = bytesWritten, TotalBytes = contentLength)
    try {
      updateProgress()
      if (mInfo.Status.isDeletedOrCanceled()) {
        throw UploadCancelException("Upload canceled")
      }

    } catch (e: IOException) {
      mCall.cancel()
      Log.e(TAG, "transferred: ", e)
    } catch (e: UploadException) {
      mCall.cancel()
      Log.e(TAG, "transferred: ", e)
    }
  }

  @Throws(UploadException::class, IOException::class)
  private fun executeUpload() {

    try {
      checkConnectivity()
      uploadData()
    } catch (e: Exception) {
      if (e is FileNotFoundException) {
        throw e
      } else {
        throw UploadNetworkException(e)
      }
    }

  }

  @Throws(UploadException::class)
  private fun checkConnectivity() {
    val networkState = mInfo.checkNetworkState(mContext)
    if (networkState != UploadContract.NetworkState.OK) {
      var status = UploadContract.UploadStatus.WAITING_FOR_NETWORK
      if (networkState == UploadContract.NetworkState.CANNOT_USE_ROAMING) {
        status = UploadContract.UploadStatus.WAITING_FOR_WIFI
        mInfo.notifyQueryForNetwork(true)
      } else if (networkState == UploadContract.NetworkState.NO_CONNECTION) {
        status = UploadContract.UploadStatus.WAITING_FOR_WIFI
        mInfo.notifyQueryForNetwork(false)
      }
      throw UploadException("current status: $status, network state: ${networkState.name}}")
    }
  }

  @Throws(IOException::class)
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

    val wrapper = CountingRequestBody(realBody, this)
    return wrapper
  }

  @Throws(IOException::class)
  private fun buildRequest(): Request {
    val builder = Request.Builder()
    val headers = mInfo.Headers
    if (headers != null) {
      builder.headers(headers)
    }
    return builder
        .url(mInfo.TargeUrl)
        .post(buildRequestBody())
        .build()
  }

  @Throws(IOException::class)
  private fun uploadData() {
    synchronized(mMonitor) {
      mCall = mClient.newCall(buildRequest())
    }
    val response = mCall.execute()
    val responseMsg = response.body()?.string()
    recordResponse(responseMsg)
  }

  private fun setTotalBytes(totalBytes: Long) {
    mInfo = mInfo.copy(TotalBytes = totalBytes)
    mInfo.partialUpdate(mDatabase)
  }

  private fun recordResponse(responseMsg: String?) {
    if (BuildConfig.DEBUG) {
      Log.d(TAG, "executeUpload: $responseMsg")
    }
    if (responseMsg == null) {
      return
    }
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
        //From AOSP, but why??
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
    fdList.forEach {
      try {
        it.close()
      } catch (e: RuntimeException) {
        // rethrow runtime
        throw e
      } catch (e: java.lang.Exception) {
        // close quietly
      }
    }
    fdList.clear()
  }

  companion object {
    private val TAG = LogUtils.makeTag(UploadThread::class.java)
    private val mMonitor = Any()
  }
}
