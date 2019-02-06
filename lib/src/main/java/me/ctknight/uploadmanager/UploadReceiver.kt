/*
 * Copyright (c) 2018. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager

import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.AsyncTask
import android.util.Log
import android.widget.Toast
import androidx.core.content.getSystemService
import me.ctknight.uploadmanager.internal.Database
import me.ctknight.uploadmanager.internal.partialUpdate
import me.ctknight.uploadmanager.util.LogUtils
import me.ctknight.uploadmanager.util.OpenHelper
import me.ctknight.uploadmanager.util.ToastUtils

class UploadReceiver : BroadcastReceiver() {
  private lateinit var context: Context
  private lateinit var database: UploadDatabase

  override fun onReceive(context: Context, intent: Intent) {

    this.context = context
    this.database = Database.INSTANCE
    val actionString = intent.action
    val action = UploadContract.NotificationAction.values()
        .firstOrNull { it.intentExtraString == actionString }
    if (ConnectivityManager.CONNECTIVITY_ACTION == actionString) {
      val connManager: ConnectivityManager? = context.getSystemService()
      val info = connManager?.activeNetworkInfo
      if (info != null && info.isConnected) {
        startService()
      }
    } else if (UploadContract.NotificationAction.Retry == action) {
      startService()
    } else if (action != null) {

      val result = goAsync()

      if (result == null) {
        handleNotificationBroadcast(action, intent)
      } else {
        AsyncTask.THREAD_POOL_EXECUTOR.execute {
          handleNotificationBroadcast(action, intent)
          result.finish()
        }
      }
    }
  }

  private fun handleNotificationBroadcast(action: UploadContract.NotificationAction, intent: Intent) {
    when (action) {
      UploadContract.NotificationAction.List -> {
        val ids = intent.getLongArrayExtra(
            UploadManager.EXTRA_NOTIFICATION_CLICK_UPLOAD_IDS)
        // TODO: 2016/2/18 add this in API doc.
        sendNotificationClickIntent(ids)
      }
      UploadContract.NotificationAction.Hide -> {
        val id = ContentUris.parseId(intent.data)
        hideNotification(id)
      }
      UploadContract.NotificationAction.Open -> {
        val id = ContentUris.parseId(intent.data)
        openUploadFile(id)
        hideNotification(id)
      }
      UploadContract.NotificationAction.Cancel -> {
        val id = ContentUris.parseId(intent.data)
        cancelUpload(id)
      }
      UploadContract.NotificationAction.Retry -> {
        // TODO: scheduled retry
      }
      UploadContract.NotificationAction.ManualRedo -> {
        val id = ContentUris.parseId(intent.data)
        redoUpload(id)
      }
    }
  }

  private fun cancelUpload(id: Long) {
    UploadManager.getUploadManger(context).remove(id)
  }

  private fun redoUpload(id: Long) {
    UploadManager.getUploadManger(context).restartUpload(id)
  }

  private fun hideNotification(id: Long) {
    val status: UploadContract.UploadStatus
    val visibility: UploadContract.Visibility

    val record = database.uploadManagerQueries.selectById(id).executeAsOneOrNull()
    if (record == null) {
      Log.w("UploadReceiver", "Missing details for upload $id")
      return
    }
    status = record.Status
    visibility = record.Visibility
    if (status.isComplete() || status.isFailed() &&
        (visibility == UploadContract.Visibility.VISIBLE_COMPLETE || visibility == UploadContract.Visibility.HIDDEN_UNTIL_COMPLETE))
      (record as UploadRecord.Impl).copy(Visibility = visibility).partialUpdate(database)
  }

  /**
   * Start activity to display the file represented by the given
   * [UploadContract.UPLOAD_COLUMNS._ID].
   */
  private fun openUploadFile(id: Long) {
    if (!OpenHelper.startViewIntent(context, id, Intent.FLAG_ACTIVITY_NEW_TASK)) {
      ToastUtils.show(R.string.upload_no_application_title, Toast.LENGTH_SHORT, context)
    }
  }

  private fun sendNotificationClickIntent(ids: LongArray) {

    if (ids.isEmpty()) {
      Log.d(TAG, "sendNotificationClickIntent: clicked a notification with no id")
    }
    val uri = ContentUris.withAppendedId(
        UploadContract.UPLOAD_CONTENT_URI, ids[0])
    val appIntent = Intent(UploadManager.ACTION_NOTIFICATION_CLICKED)
    appIntent.putExtra(UploadManager.EXTRA_NOTIFICATION_CLICK_UPLOAD_IDS, ids)
    appIntent.setPackage(context.packageName)

    if (ids.size == 1) {
      appIntent.data = uri
    } else {
      appIntent.data = UploadContract.UPLOAD_CONTENT_URI
    }
    context.sendBroadcast(appIntent)
  }

  private fun startService() {
    context.startService(Intent(context, UploadService::class.java))
  }

  companion object {
    private val TAG = LogUtils.makeTag<UploadReceiver>()
  }
}
