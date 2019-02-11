/*
 * Copyright (c) 2019. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager.internal

import android.content.Context
import me.ctknight.uploadmanager.UploadContract
import me.ctknight.uploadmanager.UploadDatabase
import me.ctknight.uploadmanager.UploadRecord

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

internal fun UploadRecord.isReadyToUpload(): Boolean {
  TODO()
}

internal fun UploadRecord.sendIntentIfRequested(context: Context) {
  TODO()
}

internal fun UploadRecord.notifyQueryForNetwork(b: Boolean) {
  TODO()
}

internal fun UploadRecord.checkNetworkState(context: Context): UploadContract.NetworkState {
  TODO()
}

internal fun UploadRecord.notificationStatus(): UploadNotifier.NotificationStatus? {
  if (Visibility !in
      arrayOf(UploadContract.Visibility.VISIBLE, UploadContract.Visibility.VISIBLE_COMPLETE)) {
    return null
  }
  return when (Status) {
    UploadContract.UploadStatus.WAITING_FOR_WIFI -> UploadNotifier.NotificationStatus.WAITING
    UploadContract.UploadStatus.RUNNING -> UploadNotifier.NotificationStatus.ACTIVE
    else -> {
      if (Status.isCompleted()) {
        UploadNotifier.NotificationStatus.COMPLETE
      } else {
        null
      }
    }
  }

}

internal fun UploadRecord.updateFromDatabase(database: UploadDatabase): UploadRecord? {
  return database.uploadManagerQueries.selectById(_ID).executeAsOneOrNull()
}

internal fun UploadRecord.nextActionMillis(now: Long): Long {
  TODO()
}

// TODO: dummy impl
internal fun UploadRecord.isMeteredAllowed(context: Context) = this.MeteredAllowed


internal fun UploadRecord.isRoamingAllowed(context: Context) = this.RoamingAllowed