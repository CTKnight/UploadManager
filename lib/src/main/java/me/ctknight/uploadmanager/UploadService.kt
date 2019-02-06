/*
 * Copyright (c) 2018. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Process
import android.text.format.DateUtils.MINUTE_IN_MILLIS
import android.util.Log
import androidx.core.content.getSystemService
import com.squareup.sqldelight.Query
import me.ctknight.uploadmanager.internal.Database
import me.ctknight.uploadmanager.internal.UploadInfo
import me.ctknight.uploadmanager.internal.UploadNotifier
import me.ctknight.uploadmanager.internal.nextActionMillis
import me.ctknight.uploadmanager.util.LogUtils
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference

class UploadService : Service() {
  private var mAlarmManager: AlarmManager? = getSystemService<AlarmManager>()
  // don't use LongSparseArray, it can't get keys' collection
  private val mUploads: MutableMap<Long, UploadInfo> = ConcurrentHashMap(5)
  private val mDatabase: UploadDatabase = Database.getInstance(this)
  private lateinit var mNotifier: UploadNotifier
  private val mExecutor = buildUploadExecutor()
  private lateinit var mUpdateThread: HandlerThread
  private lateinit var mUpdateHandler: Handler
  private lateinit var mSelectAllQuery: Query<UploadRecord>
  private lateinit var mQueryListener: UploadManagerContentObserver
  @Volatile
  private var mLastStartId: Int = 0

  private val mUpdateCallback = Handler.Callback { msg ->
    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)

    val startId = msg.arg1
    //            Log.v(TAG, "Updating for startId " + startId);

    // Since database is current source of truth, our "active" status
    // depends on database state. We always get one final update pass
    // once the real actions have finished and persisted their state.

    val isActive: Boolean
    synchronized(mUploads) {
      isActive = updateFromDatabaseLocked(mDatabase)
    }

    if (msg.what == MSG_FINAL_UPDATE) {
      // Dump thread stacks belonging to pool
      for ((key, value) in Thread.getAllStackTraces()) {
        if (key.name.startsWith("pool")) {
          Log.d(TAG, "$key: ${Arrays.toString(value)}")
        }
      }

      // Dump speed and update details
      mNotifier.dumpSpeeds()

      Log.wtf(TAG, "Final update pass triggered, isActive=" + isActive
          + "; someone didn't update correctly.")
    }

    if (isActive) {
      // Still doing useful work, keep service alive. These active
      // tasks will trigger another update pass when they're finished.

      // Enqueue delayed update pass to catch finished operations that
      // didn't trigger an update pass; these are bugs.
      enqueueFinalUpdate()

    } else {
      // No active tasks, and any pending update messages can be
      // ignored, since any updates important enough to initiate tasks
      // will always be delivered with a new startId.

      if (stopSelfResult(startId)) {
        //                    Log.v(TAG, "Nothing left; stopped");
        mSelectAllQuery.removeListener(mQueryListener)
        mUpdateThread.quit()
      }
    }

    true
  }

  /**
   * Binding to this service is not allowed
   */
  override fun onBind(intent: Intent): IBinder? {
    throw UnsupportedOperationException("Not yet implemented")
  }

  override fun onCreate() {
    super.onCreate()
    Log.v("UploadService", "created")

    mUpdateThread = HandlerThread("UploadService-UpdateThread")
    mUpdateThread.start()
    mUpdateHandler = Handler(mUpdateThread.looper, mUpdateCallback)

    mNotifier = UploadNotifier(this)
    mNotifier.cancelAll()

    mQueryListener = UploadManagerContentObserver()
    mSelectAllQuery = mDatabase
        .uploadManagerQueries
        .selectAll()

    mSelectAllQuery.addListener(mQueryListener)
  }

  override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
    val returnValue = super.onStartCommand(intent, flags, startId)
    Log.v(TAG, "Service onStart")

    mLastStartId = startId
    enqueueUpdate()
    return returnValue
  }

  override fun onDestroy() {
    super.onDestroy()
    mSelectAllQuery.removeListener(mQueryListener)
    mUpdateThread.quit()
  }

  fun enqueueUpdate() {
    mUpdateHandler.removeMessages(MSG_UPDATE)
    mUpdateHandler.obtainMessage(MSG_UPDATE, mLastStartId, -1).sendToTarget()
  }

  /**
   * Enqueue an [.updateFromDatabaseLocked] pass to occur after delay, usually to
   * catch any finished operations that didn't trigger an update pass.
   */
  private fun enqueueFinalUpdate() {
    mUpdateHandler.removeMessages(MSG_FINAL_UPDATE)
    mUpdateHandler.sendMessageDelayed(
        mUpdateHandler.obtainMessage(MSG_FINAL_UPDATE, mLastStartId, -1),
        5 * MINUTE_IN_MILLIS)
  }


  // update the upload info from database
  private fun updateFromDatabaseLocked(database: UploadDatabase): Boolean {
    val now = System.currentTimeMillis()

    var isActive = false
    var nextActionMillis = Long.MAX_VALUE

    val activeList = database
        .uploadManagerQueries
        .selectExceptVisibility(UploadContract.Visibility.HIDDEN_COMPLETE)
        .executeAsList()
    // remove stale ids
    mUploads.keys.removeAll(activeList.map { it._ID })
    activeList.forEach {
      val id = it._ID
      val storedInfo = mUploads.getOrPut(id) {
        UploadInfo(AtomicReference(it), AtomicReference(null))
      }
      // update the uploadInfo with those from database
      storedInfo.uploadRecord.set(it)

      if (it.Status == UploadContract.UploadStatus.DELETED) {
        mDatabase.uploadManagerQueries.deleteById(id)
      } else {
        val activeUpload = storedInfo.startUploadIfReady(mExecutor)
        isActive = isActive || activeUpload
      }
      if (it.Visibility == UploadContract.Visibility.HIDDEN_COMPLETE) {
        mUploads.remove(id)
      }

      nextActionMillis = Math.min(it.nextActionMillis(now), nextActionMillis)
    }

    mNotifier.updateWith(mUploads.values)

    if (nextActionMillis > 0 && nextActionMillis < java.lang.Long.MAX_VALUE) {
      Log.v(TAG, "updateFromDatabaseLocked: scheduling start in $nextActionMillis ms")
      val intent = Intent(UploadContract.NotificationAction.Retry.actionString)
      intent.setClass(this, UploadReceiver::class.java)
      // schedule a retry
      mAlarmManager?.set(AlarmManager.RTC_WAKEUP, now + nextActionMillis,
          PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_ONE_SHOT))
    }
    return isActive
  }

  private inner class UploadManagerContentObserver : Query.Listener {
    override fun queryResultsChanged() {
      enqueueUpdate()
    }
  }

  companion object {

    private const val MSG_UPDATE = 1
    private const val MSG_FINAL_UPDATE = 2
    private val TAG = LogUtils.makeTag<UploadService>()

    private fun buildUploadExecutor(): ExecutorService {
      // it's the up limit set by cluster notification
      val maxConcurrent = 5

      val executor = object : ThreadPoolExecutor(
          maxConcurrent, maxConcurrent, 10, TimeUnit.SECONDS,
          LinkedBlockingDeque()) {
        override fun afterExecute(r: Runnable, t: Throwable?) {
          super.afterExecute(r, t)
          var throwable = t
          if (t == null && r is Future<*>) {
            try {
              r.get()
            } catch (ce: CancellationException) {
              throwable = ce
            } catch (ee: ExecutionException) {
              throwable = ee.cause
            } catch (ie: InterruptedException) {
              Thread.currentThread().interrupt()
            }
          }

          if (throwable != null) {
            Log.w("UploadService", "Uncaught exception$t")
          }
        }
      }
      executor.allowCoreThreadTimeOut(true)
      return executor
    }
  }
}
