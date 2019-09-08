/*
 * Copyright (c) 2018. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */
@file:JvmName("UriUtils")

package me.ctknight.uploadmanager.util

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns

import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

fun Uri.penInputStream(
  context: Context
): InputStream? {
  return try {
    context.contentResolver.openInputStream(this)
  } catch (e: FileNotFoundException) {
    e.printStackTrace()
    null
  }

}

// See https://developer.android.com/training/secure-file-sharing/retrieve-info.html#RetrieveFileInfo
fun Uri.queryOpenableInfo(
  context: Context
): OpenableInfo? {
  val uri = this;
  val cursor = context.contentResolver.query(uri, null, null, null, null)

  if (cursor == null || !cursor.moveToFirst()) {
    // Fallback: Check if it is a file Uri.
    if (ContentResolver.SCHEME_FILE == uri.scheme) {
      val file = File(uri.path)
      return OpenableInfo(file.name, file.length())
    }
    return null
  }

  val info = OpenableInfo(
      cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)),
      cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE))
  )
  cursor.close()
  return info
}

class OpenableInfo(
  val displayName: String,
  val size: Long
)

