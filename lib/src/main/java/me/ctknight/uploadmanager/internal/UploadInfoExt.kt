/*
 * Copyright (c) 2019. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager.internal

import android.content.Context
import me.ctknight.uploadmanager.UploadContract
import me.ctknight.uploadmanager.UploadDatabase
import me.ctknight.uploadmanager.UploadRecord
import java.util.concurrent.TimeUnit

// only update the parts that refresh through
internal fun UploadRecord.partialUpdate(database: UploadDatabase) {
  database.uploadManagerQueries.partialUpdate(
      Status = Status,
      CurrentBytes = CurrentBytes,
      TotalBytes = TotalBytes,
      NumFailed = NumFailed,
      RetryAfter = RetryAfter,
      ServerResponse = ServerResponse,
      Visibility = Visibility,
      _ID = _ID
  )
}

internal fun UploadRecord.isReadyToSchdule(): Boolean =
    Status.isReadyToSchedule()

internal fun UploadRecord.notificationStatus(): UploadNotifier.NotificationStatus? {
  return when (Visibility) {
    UploadContract.Visibility.VISIBLE -> {
      when {
        Status.isWaiting() -> UploadNotifier.NotificationStatus.WAITING
        Status == UploadContract.UploadStatus.RUNNING -> UploadNotifier.NotificationStatus.ACTIVE
        Status.isCompleted() -> UploadNotifier.NotificationStatus.COMPLETE
        else -> null
      }
    }
    UploadContract.Visibility.HIDDEN_UNTIL_COMPLETE -> {
      when {
        Status.isCompleted() -> UploadNotifier.NotificationStatus.COMPLETE
        else -> null
      }
    }
    else -> null
  }
}

internal fun UploadRecord.updateFromDatabase(database: UploadDatabase): UploadRecord? {
  return database.uploadManagerQueries.selectById(_ID).executeAsOneOrNull()
}

internal fun UploadRecord.nextActionMillis(now: Long): Long {
  TODO()
}

internal fun UploadRecord.isMeteredAllowed(context: Context) = this.MeteredAllowed

internal fun UploadRecord.isRoamingAllowed(context: Context) = this.RoamingAllowed

// TODO: dummy
internal fun UploadRecord.isVisible(): Boolean = true

internal fun UploadRecord.minLatency(): Long {
  val now = System.currentTimeMillis()
  val retryAfter = RetryAfter
  val lastMod = LastModification ?: now
  val startAfter: Long =
      if (NumFailed == 0L) {
        now
      } else if (retryAfter != null && retryAfter > 0) {
        lastMod + fuzzDelay(retryAfter)
      } else {
        // expon delay
        val delay = TimeUnit.SECONDS.toMillis(UploadContract.Constants.MIN_RETRY_AFTER) *
            (1L shl (NumFailed - 1).toInt())
        lastMod + fuzzDelay(delay)
      }
  return Math.max(0, startAfter - now)
}

internal fun fuzzDelay(delay: Long): Long =
    delay + Helpers.sRandom.nextInt(delay.toInt() / 2)
