/*
 * Copyright (c) 2019. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager.internal

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import androidx.core.content.getSystemService
import me.ctknight.uploadmanager.UploadJobService
import me.ctknight.uploadmanager.UploadRecord
import me.ctknight.uploadmanager.util.LogUtils
import kotlin.random.Random


internal object Helpers {
  internal val sAsyncHandler: Handler by lazy {
    val thread = HandlerThread("AsyncUtils", Process.THREAD_PRIORITY_BACKGROUND)
    thread.start()
    return@lazy Handler(thread.looper)
  }
  internal val sRandom: Random = Random.Default
  fun scheduleJob(context: Context, id: Long) {
    val record = Database.getInstance(context)
        .uploadManagerQueries.selectById(id).executeAsOneOrNull()
    val scheduled = scheduleJob(context, record)
    if (!scheduled) {
      UploadNotifier.getInstance(context).update()
    }
  }

  internal fun scheduleJob(context: Context, record: UploadRecord?): Boolean {
    if (record == null) {
      Log.w(LogUtils.makeTag<Helpers>(), "scheduleJob: record is null, skipped")
      return false
    }
    val scheduler: JobScheduler = context.getSystemService()!!
    val jobId = record._ID.toInt()
    scheduler.cancel(jobId)

    if (!record.isReadyToSchedule()) return false

    val builder = JobInfo.Builder(jobId,
        ComponentName(context, UploadJobService::class.java))
    val latency = record.minLatency()
    if (latency > 0) {
      builder.setMinimumLatency(latency)
    }
    if (record.MeteredAllowed && record.RoamingAllowed) {
      builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
    } else if (!record.MeteredAllowed) {
      builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
    } else if (!record.RoamingAllowed) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_NOT_ROAMING)
      }
    } else {
      builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      if (record.TotalBytes >= 0) {
        builder.setEstimatedNetworkBytes(0, record.TotalBytes)
      }
      if (record.isVisible()) {

      }
    }
    scheduler.schedule(builder.build())
    return true
  }
}