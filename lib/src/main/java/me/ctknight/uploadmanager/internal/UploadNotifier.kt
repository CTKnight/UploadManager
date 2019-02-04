/*
 * Copyright (c) 2019. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager.internal

import android.app.Notification
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
import android.support.v4.app.NotificationCompat
import android.support.v4.util.LongSparseArray
import android.text.TextUtils
import android.util.Log

import java.text.NumberFormat
import java.util.ArrayList
import java.util.Arrays
import java.util.HashMap

import me.ctknight.uploadmanager.R
import me.ctknight.uploadmanager.UploadInfo
import me.ctknight.uploadmanager.UploadManager
import me.ctknight.uploadmanager.UploadReceiver
import me.ctknight.uploadmanager.UploadContract
import me.ctknight.uploadmanager.util.TimeUtils

//In AOSP Download Notifier,they use LongSparseLongArray,
//actually,LongSparseArray is a generic version of LongSparseLongArray,
//so LongSparseArray<Long> is totally same with LongSparseLongArray.
internal class UploadNotifier (context: Context) {
  //AOSP use final ,but I have to get current package name in a static method,so change it to static.
  private val mNotifManager: NotificationManager?

  private val mActiveNotif = HashMap<String, Long>()

  private val mUploadSpeed = LongSparseArray<Long>()

  private val mUploadTouch = LongSparseArray<Long>()

  init {
    mContext = context
    mNotifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mNotifManager != null) {
      val channel = NotificationChannel(NOTIFICATION_CHANNEL,
          mContext!!.getString(R.string.notification_channel),
          NotificationManager.IMPORTANCE_DEFAULT)
      mNotifManager.createNotificationChannel(channel)
    }
  }

  fun cancelAll() {
    mNotifManager!!.cancelAll()
  }

  fun notifyUploadSpeed(id: Long, bytesPerSecond: Long) {
    synchronized(mUploadSpeed) {
      if (bytesPerSecond != 0L) {
        mUploadSpeed.put(id, bytesPerSecond)
        mUploadTouch.put(id, SystemClock.elapsedRealtime())
      } else {
        mUploadSpeed.delete(id)
        mUploadTouch.delete(id)
      }
    }
  }

  fun updateWith(uploads: Collection<UploadInfo>) {
    synchronized(mActiveNotif) {
      updateWithLocked(uploads)
    }
  }

  private fun updateWithLocked(uploads: Collection<UploadInfo>) {
    val res = mContext!!.resources

    // Cluster uploads together
    val clustered = HashMap<String, ArrayList<UploadInfo>>()
    //        final Multimap<String, UploadInfo> clustered = ArrayListMultimap.create();
    for (info in uploads) {
      val tag = buildNotificationTag(info)
      if (tag != null) {
        if (clustered.containsKey(tag)) {
          clustered[tag]!!.add(info)
        } else {
          //NOTE I know this is dirty, but I just don't want to get guava involved.
          clustered[tag] = ArrayList(Arrays.asList(info))
        }
      }
    }

    //Build notification for each cluster
    for (tag in clustered.keys) {
      val type = getNotificationTagType(tag)
      val cluster = clustered[tag]

      val builder = NotificationCompat.Builder(mContext!!, NOTIFICATION_CHANNEL)
      builder.color = res.getColor(R.color.system_notification_accent_color)

      // Use time when cluster was first shown to avoid shuffling
      val firstShown: Long
      if (mActiveNotif.containsKey(tag)) {
        firstShown = mActiveNotif[tag]!!
      } else {
        firstShown = System.currentTimeMillis()
        mActiveNotif[tag] = firstShown
      }
      builder.setWhen(firstShown)

      // Show relevant icon
      if (type == TYPE_ACTIVE) {
        builder.setSmallIcon(android.R.drawable.stat_sys_upload)
      } else if (type == TYPE_WAITING) {
        builder.setSmallIcon(android.R.drawable.stat_sys_warning)
      } else if (type == TYPE_COMPLETE) {
        builder.setSmallIcon(android.R.drawable.stat_sys_upload_done)
      } else if (type == TYPE_FAILED) {
        builder.setSmallIcon(android.R.drawable.stat_sys_warning)
      }

      // Build action intents
      // add action by type
      // active -> cancel
      // fail waiting -> retry
      if (type == TYPE_ACTIVE || type == TYPE_WAITING) {
        val uri = Uri.Builder().scheme("active-dl").appendPath(tag).build()
        val intent = Intent(UploadContract.ACTION_LIST,
            uri, mContext, UploadReceiver::class.java)
        intent.putExtra(UploadManager.EXTRA_NOTIFICATION_CLICK_UPLOAD_IDS,
            getUploadIds(cluster!!))
        builder.setContentIntent(PendingIntent.getBroadcast(mContext,
            9, intent, PendingIntent.FLAG_UPDATE_CURRENT))
        builder.setOngoing(true)

        val info = cluster.iterator().next()
        if (type == TYPE_ACTIVE) {
          val idUri = ContentUris.withAppendedId(UploadContract.UPLOAD_URIS.CONTENT_URI, info.mId)
          val actionIntent = Intent(UploadContract.ACTION_CANCEL,
              idUri, mContext, UploadReceiver::class.java)
          builder.addAction(R.drawable.ic_clear_black_24dp,
              mContext!!.getString(R.string.notification_action_cancel),
              PendingIntent.getBroadcast(mContext, 0, actionIntent, 0))
        } else {
          // WAITING
          val idUri = ContentUris.withAppendedId(UploadContract.UPLOAD_URIS.CONTENT_URI, info.mId)
          val actionIntent = Intent(UploadContract.ACTION_MANUAL_REDO,
              idUri, mContext, UploadReceiver::class.java)
          builder.addAction(R.drawable.ic_redo_black_24dp,
              mContext!!.getString(R.string.notification_action_redo),
              PendingIntent.getBroadcast(mContext, 0, actionIntent, 0))
        }

      } else if (type == TYPE_COMPLETE) {
        val info = cluster!!.iterator().next()
        val uri = ContentUris.withAppendedId(UploadContract.UPLOAD_URIS.CONTENT_URI, info.mId)
        builder.setAutoCancel(true)
        //In AOSP, action is set according to its status
        //but we just need to open the upload list.

        val action: String
        if (UploadContract.isStatusError(info.mStatus)) {
          action = UploadContract.ACTION_LIST
        } else {
          action = UploadContract.ACTION_OPEN
        }
        val intent = Intent(action, uri, mContext, UploadReceiver::class.java)
        intent.putExtra(UploadManager.EXTRA_NOTIFICATION_CLICK_UPLOAD_IDS,
            getUploadIds(cluster))
        builder.setContentIntent(PendingIntent.getBroadcast(mContext,
            0, intent, PendingIntent.FLAG_UPDATE_CURRENT))

        val hideIntent = Intent(UploadContract.ACTION_HIDE,
            uri, mContext, UploadReceiver::class.java)
        builder.setDeleteIntent(PendingIntent.getBroadcast(mContext, 0, hideIntent, 0))

      } else if (type == TYPE_FAILED) {
        val info = cluster!!.iterator().next()
        val uri = ContentUris.withAppendedId(UploadContract.UPLOAD_URIS.CONTENT_URI, info.mId)
        builder.setAutoCancel(true)

        val action = UploadContract.ACTION_LIST

        val intent = Intent(action, uri, mContext, UploadReceiver::class.java)
        intent.putExtra(UploadManager.EXTRA_NOTIFICATION_CLICK_UPLOAD_IDS,
            getUploadIds(cluster))
        builder.setContentIntent(PendingIntent.getBroadcast(mContext,
            0, intent, PendingIntent.FLAG_UPDATE_CURRENT))

        val hideIntent = Intent(UploadContract.ACTION_HIDE,
            uri, mContext, UploadReceiver::class.java)
        builder.setDeleteIntent(PendingIntent.getBroadcast(mContext, 0, hideIntent, 0))
      }

      //Calculate and show progress
      var remainingText: String? = null
      var percentText: String? = null
      if (type == TYPE_ACTIVE) {
        var current: Long = 0
        var total: Long = 0
        var speed: Long = 0
        synchronized(mUploadSpeed) {
          for (info in cluster!!) {
            if (info.mTotalBytes !== -1) {
              current += info.mCurrentBytes
              total += info.mTotalBytes
              speed += if (mUploadSpeed.get(info.mId) == null) 0 else mUploadSpeed.get(info.mId)
            }
          }
        }

        if (total > 0) {
          percentText = NumberFormat.getPercentInstance().format(current.toDouble() / total)

          if (speed > 0) {
            val remainingMillis = (total - current) * 1000 / speed
            remainingText = res.getString(R.string.upload_remaining,
                TimeUtils.formatDuration(remainingMillis, res))
          }

          val percent = (current * 100 / total).toInt()
          builder.setProgress(100, percent, false)
        } else {
          builder.setProgress(100, 0, true)
        }
      }

      val notification: Notification
      if (cluster!!.size == 1) {
        val info = cluster.iterator().next()

        builder.setContentTitle(getUploadTitle(res, info))

        if (type == TYPE_ACTIVE) {
          if (!TextUtils.isEmpty(info.mDescription)) {
            builder.setContentText(info.mDescription)
          } else {
            builder.setContentText(remainingText)
          }
          builder.setContentInfo(percentText)

        } else if (type == TYPE_WAITING) {
          //For both continuable transfer and network check before transfer,
          //but Box doesn't support previous one.
          builder.setContentText(res.getText(
              R.string.notification_waiting_for_suitable_network))

        } else if (type == TYPE_COMPLETE) {
          builder.setContentText(res.getString(R.string.notification_upload_successfully))

        } else if (type == TYPE_FAILED) {
          // TODO: 2016/2/28 TEMP FIX
          if (info.mStatus === UploadContract.UPLOAD_STATUS.FILE_NOT_FOUND) {
            builder.setContentText(res.getString(R.string.notification_upload_file_not_found))
          } else {
            builder.setContentText(res.getString(R.string.notification_upload_unsuccessfully))
          }
        }

        notification = builder.build()

      } else {
        // handle more than one items

        val inboxStyle = NotificationCompat.InboxStyle(builder)

        for (info in cluster) {
          inboxStyle.addLine(getUploadTitle(res, info))
        }

        if (type == TYPE_ACTIVE) {
          builder.setContentTitle(res.getQuantityString(
              R.plurals.notif_summary_active, cluster.size, cluster.size))
          builder.setContentText(remainingText)
          builder.setContentInfo(percentText)
          inboxStyle.setSummaryText(remainingText)

        } else if (type == TYPE_WAITING) {
          builder.setContentTitle(
              res.getQuantityString(R.plurals.notif_summary_waiting, cluster.size, cluster.size))
          builder.setContentText(
              res.getString(R.string.notification_waiting_for_suitable_network))
          inboxStyle.setSummaryText(
              res.getString(R.string.notification_waiting_for_suitable_network))

        } else if (type == TYPE_FAILED) {
          builder.setContentText(res.getQuantityString(R.plurals.notif_summary_failed, cluster.size, cluster.size))
        }

        notification = inboxStyle.build()

      }
      mNotifManager!!.notify(tag, 0, notification)

    }

    val it = mActiveNotif.keys.iterator()
    while (it.hasNext()) {
      val tag = it.next()
      if (!clustered.containsKey(tag)) {
        mNotifManager!!.cancel(tag, 0)
        it.remove()
      }
    }
  }

  private fun getUploadIds(infos: Collection<UploadInfo>): LongArray {
    val ids = LongArray(infos.size)
    var i = 0
    for (info in infos) {
      ids[i++] = info.mId
    }
    return ids
  }

  fun dumpSpeeds() {
    synchronized(mUploadSpeed) {
      for (i in 0 until mUploadSpeed.size()) {
        val id = mUploadSpeed.keyAt(i)
        val delta = SystemClock.elapsedRealtime() - mUploadTouch.get(id)!!
        Log.d("UploadManager", "Upload " + id + " speed " + mUploadSpeed.valueAt(i) + "bps, "
            + delta + "ms ago")

      }
    }
  }

  companion object {

    val NOTIFICATION_CHANNEL = "Upload notification"

    private val TYPE_ACTIVE = 1
    private val TYPE_WAITING = 2
    private val TYPE_COMPLETE = 3
    private val TYPE_FAILED = 4


    private var mContext: Context? = null

    private fun getUploadTitle(res: Resources, info: UploadInfo): CharSequence {
      return if (!TextUtils.isEmpty(info.mTitle)) {
        info.mTitle
      } else {
        res.getString(R.string.upload_unknown_upload_title)
      }
    }

    private fun buildNotificationTag(info: UploadInfo): String? {
      // waiting and active is clustered
      return if (UploadContract.isWaiting(info)) {
        TYPE_WAITING.toString() + ":" + mContext!!.packageName
      } else if (UploadContract.isOnGoing(info)) {
        TYPE_ACTIVE.toString() + ":" + mContext!!.packageName
      } else if (UploadContract.isFailed(info)) {
        TYPE_FAILED.toString() + ":" + info.mId
      } else if (UploadContract.isComplete(info)) {
        TYPE_COMPLETE.toString() + ":" + info.mId
      } else {
        null
      }
    }

    private fun getNotificationTagType(tag: String): Int {
      return Integer.parseInt(tag.substring(0, tag.indexOf(':')))
    }
  }
}
