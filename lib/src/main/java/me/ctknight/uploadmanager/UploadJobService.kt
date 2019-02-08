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
import me.ctknight.uploadmanager.internal.Helpers
import me.ctknight.uploadmanager.internal.UploadNotifier
import me.ctknight.uploadmanager.internal.UploadThread
import me.ctknight.uploadmanager.util.LogUtils
import me.ctknight.uploadmanager.util.NetworkUtils

class UploadJobService : JobService() {
  private val mDatabase: UploadDatabase = Database.getInstance(this)
  @GuardedBy("mActiveThreads")
  private val mActiveThreads = SparseArray<UploadThread>()
  private lateinit var mNotificationQuery: Query<UploadRecord>

  private val mObserver = object : Query.Listener {
    override fun queryResultsChanged() {
      Helpers.sAsyncHandler.post { UploadNotifier.getInstance(this@UploadJobService).update() }
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
    val info = getRecordById(id) as UploadRecord.Impl?
    if (info == null) {
      Log.w(TAG, "Odd, no details found for upload $id")
      return false
    }

    val thread: UploadThread
    synchronized(mActiveThreads) {
      if (mActiveThreads.indexOfKey(id) >= 0) {
        Log.w(TAG, "Odd, already running upload $id")
        return false
      }
      // TODO: add API for custom networkClient
      thread = UploadThread(this, NetworkUtils.sNetworkClient, params, info)
      mActiveThreads.put(id, thread)
    }
    thread.start()

    return true
  }

  override fun onStopJob(params: JobParameters): Boolean {
    val id = params.jobId

    val thread: UploadThread?
    synchronized(mActiveThreads) {
      thread = mActiveThreads.get(id)
      mActiveThreads.delete(id)
    }
    if (thread != null) {
      // If the thread is still running, ask it to gracefully shutdown,
      // and reschedule ourselves to resume in the future.
      thread.requestShutdown()

      Helpers.scheduleJob(this, getRecordById(id))
    }
    return false
  }

  fun jobFinishedInternal(params: JobParameters, needsReschedule: Boolean) {
    val id = params.jobId

    synchronized(mActiveThreads) {
      mActiveThreads.remove(params.jobId)
    }
    if (needsReschedule) {
      Helpers.scheduleJob(this, getRecordById(id))
    }

    // Update notifications one last time while job is protecting us
    mObserver.queryResultsChanged()

    // We do our own rescheduling above
    jobFinished(params, false)
  }

  private fun getRecordById(id: Int) =
      mDatabase.uploadManagerQueries.selectById(id.toLong()).executeAsOneOrNull()

  companion object {
    private val TAG = LogUtils.makeTag<UploadJobService>()
  }
}
