/*
 * Copyright (c) 2019. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager.internal

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import me.ctknight.uploadmanager.UploadRecord

internal object Helpers {
  internal val sAsyncHandler: Handler by lazy {
    val thread = HandlerThread("AsyncUtils", Process.THREAD_PRIORITY_BACKGROUND)
    thread.start()
    return@lazy Handler(thread.looper)
  }
  fun scheduleJob(context: Context, info: UploadRecord?) {

  }
}