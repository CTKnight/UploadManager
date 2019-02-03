/*
 * Copyright (c) 2019. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager.internal

import android.content.Context
import me.ctknight.uploadmanager.UploadContract
import me.ctknight.uploadmanager.UploadDatabase
import me.ctknight.uploadmanager.UploadInfo

// only update the parts that refresh through
internal fun UploadInfo.partialUpdate(database: UploadDatabase) {
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

internal fun UploadInfo.sendIntentIfRequested(context: Context) {
  TODO()
}

internal fun UploadInfo.notifyQueryForNetwork(b: Boolean) {
  TODO()
}

internal fun UploadInfo.checkNetworkState(context: Context): UploadContract.NetworkState {
  TODO()
}