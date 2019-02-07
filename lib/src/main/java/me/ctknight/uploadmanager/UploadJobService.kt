/*
 * Copyright (c) 2019. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import android.util.SparseArray
import androidx.annotation.GuardedBy
import com.squareup.sqldelight.Query
import me.ctknight.uploadmanager.internal.Database
import me.ctknight.uploadmanager.internal.UploadNotifier
import me.ctknight.uploadmanager.internal.UploadThread
import me.ctknight.uploadmanager.util.AsyncUtils

class UploadJobService : JobService() {
  private val mDatabase: UploadDatabase = Database.getInstance(this)
  @GuardedBy("mActiveThreads")
  private val mActiveThreads = SparseArray<UploadThread>()
  private lateinit var mNotificationQuery: Query<UploadRecord>

  private val mObserver = object : Query.Listener {
    override fun queryResultsChanged() {
      AsyncUtils.sAsyncHandler.post { UploadNotifier.getInstance(this@UploadJobService).updateWith() }
    }
  }

  override fun onCreate() {
    super.onCreate()

    // While someone is bound to us, watch for database changes that should
    // trigger notification updates.
    mNotificationQuery = mDatabase
        .uploadManagerQueries
        .selectNotDeleted()

    mNotificationQuery.addListener(mObserver)
  }

  override fun onDestroy() {
    super.onDestroy()
    mNotificationQuery.removeListener(mObserver)
  }

  override fun onStartJob(params: JobParameters): Boolean {
    val id = params.jobId

    // Spin up thread to handle this download
    val info = DownloadInfo.queryDownloadInfo(this, id)
    if (info == null) {
      Log.w(TAG, "Odd, no details found for download $id")
      return false
    }

    val thread: DownloadThread
    synchronized(mActiveThreads) {
      if (mActiveThreads.indexOfKey(id) >= 0) {
        Log.w(TAG, "Odd, already running download $id")
        return false
      }
      thread = DownloadThread(this, params, info)
      mActiveThreads.put(id, thread)
    }
    thread.start()

    return true
  }

  override fun onStopJob(params: JobParameters): Boolean {
    val id = params.jobId

    val thread: DownloadThread?
    synchronized(mActiveThreads) {
      thread = mActiveThreads.removeReturnOld(id)
    }
    if (thread != null) {
      // If the thread is still running, ask it to gracefully shutdown,
      // and reschedule ourselves to resume in the future.
      thread!!.requestShutdown()

      Helpers.scheduleJob(this, DownloadInfo.queryDownloadInfo(this, id))
    }
    return false
  }

  fun jobFinishedInternal(params: JobParameters, needsReschedule: Boolean) {
    val id = params.jobId

    synchronized(mActiveThreads) {
      mActiveThreads.remove(params.jobId)
    }
    if (needsReschedule) {
      Helpers.scheduleJob(this, DownloadInfo.queryDownloadInfo(this, id))
    }

    // Update notifications one last time while job is protecting us
    mObserver.onChange(false)

    // We do our own rescheduling above
    jobFinished(params, false)
  }
}
