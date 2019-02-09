/*
 * Copyright (c) 2019. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager.internal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.annotation.GuardedBy
import androidx.collection.ArrayMap
import androidx.collection.LongSparseArray
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.core.content.res.ResourcesCompat
import me.ctknight.uploadmanager.*
import me.ctknight.uploadmanager.thirdparty.SingletonHolder


//In AOSP Download Notifier,they use LongSparseLongArray,
//actually,LongSparseArray is a generic version of LongSparseLongArray,
//so LongSparseArray<Long> is totally same with LongSparseLongArray.
internal class UploadNotifier(private val mContext: Context) {
  //AOSP use final ,but I have to get current package name in a static method,so change it to static.
  private val mNotifManager: NotificationManagerCompat = NotificationManagerCompat.from(mContext)

  private val mDatabase: UploadDatabase = Database.getInstance(mContext)

  // {tag: firstShown}
  @GuardedBy("mActiveNotif")
  private val mActiveNotif: MutableMap<Pair<Long, NotificationStatus>, Long> = ArrayMap()

  // {Id: {Speed, lastModified}}
  @GuardedBy("mUploadSpeed")
  private val mUploadSpeed = LongSparseArray<Pair<Long, Long>>()

  init {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val notificationManager: NotificationManager = mContext.getSystemService()!!
      notificationManager.also {
        it.createNotificationChannel(NotificationChannel(CHANNEL_ACTIVE,
            mContext.getText(R.string.upload_running),
            NotificationManager.IMPORTANCE_MIN))
      }.also {
        it.createNotificationChannel(NotificationChannel(CHANNEL_WAITING,
            mContext.getText(R.string.upload_queued),
            NotificationManager.IMPORTANCE_DEFAULT))
      }.also {
        it.createNotificationChannel(NotificationChannel(CHANNEL_COMPLETE,
            mContext.getText(R.string.upload_complete),
            NotificationManager.IMPORTANCE_DEFAULT))
      }
    }
  }

  fun notifyUploadSpeed(id: Long, bytesPerSecond: Long) {
    synchronized(mUploadSpeed) {
      if (bytesPerSecond != 0L) {
        mUploadSpeed.put(id, Pair(bytesPerSecond, SystemClock.elapsedRealtime()))
      } else {
        mUploadSpeed.delete(id)
      }
    }
  }

  fun update() {
    synchronized(mActiveNotif) {
      val uploads = mDatabase.uploadManagerQueries.selectAll().executeAsList()
      updateWithLocked(uploads)
    }
  }

  private fun updateWithLocked(uploads: Iterable<UploadRecord>) {

    // Cluster uploads together
    // waiting and active is clustered (ongoing)
    // complete and failed is not (can be swiped)

    val clustered = uploads
        .filter { it.Status != UploadContract.UploadStatus.DELETED }
        .groupBy {
          val notificationStatus = it.notificationStatus()
          return@groupBy when (notificationStatus) {
            in shouldClusterStatus ->
              Pair(-1L, notificationStatus)
            else ->
              Pair(it._ID, notificationStatus)
          }
        }

    clustered.entries.forEach { (tag, cluster) ->
      buildClusterNotification(tag, cluster)
    }
    // remove stale notifications
    val staleKeys = mActiveNotif.keys - clustered.keys
    staleKeys.forEach {
      mNotifManager.cancel(it.toString(), 0)
      mActiveNotif.remove(it)
    }
  }

  private fun buildClusterNotification(tag: Pair<Long, NotificationStatus?>, cluster: Iterable<UploadRecord>) {
    val (id, type) = tag
    type ?: return
    val res = mContext.resources

    val channel = when (type) {
      NotificationStatus.ACTIVE -> CHANNEL_ACTIVE
      NotificationStatus.COMPLETE -> CHANNEL_COMPLETE
      NotificationStatus.WAITING -> CHANNEL_WAITING
    }
    val builder = NotificationCompat.Builder(mContext, channel)

    // Show relevant icon
    when (type) {
      NotificationStatus.ACTIVE -> builder.setSmallIcon(android.R.drawable.stat_sys_upload)
      NotificationStatus.COMPLETE -> builder.setSmallIcon(android.R.drawable.stat_sys_upload_done)
      NotificationStatus.WAITING -> builder.setSmallIcon(android.R.drawable.stat_sys_warning)
    }

    builder.color = ResourcesCompat.getColor(res, R.color.system_notification_accent_color, null)
    // Use time when cluster was first shown to avoid shuffling
    // TODO: should firstShown be stored in to database
    val firstShown = mActiveNotif.getOrPut(Pair(id, type)) { System.currentTimeMillis() }
    builder.setWhen(firstShown)
    builder.setOnlyAlertOnce(true)

    // Calculate and show progress

    if (type == NotificationStatus.ACTIVE || type == NotificationStatus.WAITING) {
      val uploadIds = getUploadIds(cluster)
      val uri = Uri.Builder().scheme("active-ul")
          .appendPath(UploadContract.UPLOAD_CONTENT_URI.toString()).build()
      val intent = Intent(UploadContract.NotificationAction.List.actionString,
          uri, mContext, UploadReceiver::class.java)
      intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
      intent.putExtra(UploadManager.EXTRA_NOTIFICATION_CLICK_UPLOAD_IDS, uploadIds)
      intent.putExtra(UploadReceiver.EXTRA_CANCELED_UPLOAD_NOTIFICATION_TAG, tag.toString())
      builder.setContentIntent(PendingIntent.getBroadcast(mContext,
          0, intent, PendingIntent.FLAG_UPDATE_CURRENT))

      if (type == NotificationStatus.ACTIVE) {
        builder.setOngoing(true)
      }

      val cancelUri = Uri.Builder().scheme("cancel-ul")
          .appendPath(UploadContract.UPLOAD_CONTENT_URI.toString()).build()
      val cancelIntent = Intent(
          UploadContract.NotificationAction.Cancel.actionString,
          cancelUri,
          mContext,
          UploadReceiver::class.java
      )
      cancelIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
      cancelIntent.putExtra(UploadReceiver.EXTRA_CANCELED_UPLOAD_IDS, uploadIds)
      cancelIntent.putExtra(UploadReceiver.EXTRA_CANCELED_UPLOAD_NOTIFICATION_TAG, tag.toString())

      builder.addAction(R.drawable.ic_clear_black_24dp,
          mContext.getString(R.string.notification_action_cancel),
          PendingIntent.getBroadcast(mContext, 0, cancelIntent, 0))
      builder.setCategory(NotificationCompat.CATEGORY_PROGRESS)
    } else if (type == NotificationStatus.COMPLETE) {

    }
  }

  companion object {

    private object InstanceHolder : SingletonHolder<UploadNotifier, Context>(::UploadNotifier)

    internal val getInstance = InstanceHolder::getInstance

    private const val CHANNEL_ACTIVE = "active"
    private const val CHANNEL_WAITING = "waiting"
    private const val CHANNEL_COMPLETE = "complete"

    private fun getUploadTitle(res: Resources, info: UploadRecord): CharSequence {
      val title = info.NotificationTitle
      return if (title.isNullOrEmpty()) {
        res.getString(R.string.upload_unknown_upload_title)
      } else {
        title
      }
    }

    private fun getUploadIds(infos: Iterable<UploadRecord>): LongArray {
      return infos.map { it._ID }.toLongArray()
    }
  }

  internal enum class NotificationStatus {
    WAITING,
    ACTIVE,
    COMPLETE
  }
}
