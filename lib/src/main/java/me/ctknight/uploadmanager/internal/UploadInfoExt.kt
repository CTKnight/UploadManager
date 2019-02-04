/*
 * Copyright (c) 2019. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager.internal

import android.content.Context
import me.ctknight.uploadmanager.UploadContract
import me.ctknight.uploadmanager.UploadDatabase
import me.ctknight.uploadmanager.UploadRecord
import java.util.concurrent.ExecutorService

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

internal fun UploadRecord.updateFromDatabase(database: UploadDatabase): UploadRecord? {
  return database.uploadManagerQueries.selectById(_ID).executeAsOneOrNull()
}

internal fun UploadRecord.startIfReady(executorService: ExecutorService): Boolean {
  synchronized(this) {
    val ready = isReadyToUpload()
    val isActive =
  }
}