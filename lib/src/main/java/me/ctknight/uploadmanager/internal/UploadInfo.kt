/*
 * Copyright (c) 2019. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager.internal

import me.ctknight.uploadmanager.UploadRecord
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicReference

internal data class UploadInfo(
    internal var uploadRecord: AtomicReference<UploadRecord>,
    internal var submittedTask: AtomicReference<UploadThread?>
) {
  internal fun startUploadIfReady(executorService: ExecutorService): Boolean {
    TODO()
  }
}