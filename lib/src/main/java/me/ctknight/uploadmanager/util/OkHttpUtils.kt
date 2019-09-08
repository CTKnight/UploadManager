/*
 * Copyright (c) 2019. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */
@file:JvmName("OkHttpUtils")

package me.ctknight.uploadmanager.util

import android.os.ParcelFileDescriptor
import android.system.Os

import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.internal.closeQuietly
import okio.BufferedSink
import okio.Source
import okio.buffer
import okio.source

fun ParcelFileDescriptor.createRequest(mediaType: MediaType?): RequestBody {
  return object : RequestBody() {
    override fun contentType(): MediaType? {
      return mediaType
    }

    override fun contentLength(): Long {
      return statSize
    }

    @Throws(IOException::class)
    override fun writeTo(sink: BufferedSink) {
      var inputStream: InputStream? = null
      try {
        val fd = fileDescriptor
        inputStream = FileInputStream(fd)
        val source = inputStream.source()
            .buffer()
        sink.writeAll(source)
      } finally {
        inputStream?.closeQuietly()
      }
      // not closing the pfd here, because okhttp may try to use this several time (http 308)
    }
  }
}

