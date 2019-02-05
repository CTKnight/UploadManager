/*
 * Copyright (c) 2019. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager.internal

import me.ctknight.uploadmanager.UploadRecord
import java.util.concurrent.ExecutorService

internal data class UploadInfo(
    internal var uploadRecord: UploadRecord,
    internal var submittedTask: UploadThread?
) {
  internal fun startUploadIfReady(executorService: ExecutorService): Boolean {
    TODO()
  }
  internal fun noticationStatus(): UploadNotifier.NotificationStatus {
    TODO()
  }
}