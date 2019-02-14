/*
 * Copyright (c) 2018. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.util.Log

import java.io.File

import me.ctknight.uploadmanager.UploadManager

import me.ctknight.uploadmanager.UploadManager.COLUMN_FILE_URI
import me.ctknight.uploadmanager.UploadManager.COLUMN_MEDIA_TYPE
import me.ctknight.uploadmanager.UploadManager.COLUMN_REMOTE_URI

object OpenHelper {
  val TAG = "OpenHelper"

  /**
   * Build and start an [Intent] to view the download with given ID,
   * handling subtleties around installing packages.
   */
  fun startViewIntent(context: Context, id: Long, intentFlags: Int): Boolean {
    val intent = OpenHelper.buildViewIntent(context, id)
    if (intent == null) {
      Log.w(TAG, "No intent built for $id")
      return false
    }

    intent.addFlags(intentFlags)
    try {
      context.startActivity(intent)
      return true
    } catch (e: ActivityNotFoundException) {
      Log.w(TAG, "Failed to start $intent: $e")
      return false
    } catch (e: SecurityException) {
      // TODO: 2016/5/6 add to doc: make sure Uris have granted permission.
      Log.e(TAG, "startViewIntent: failed to open url" + intent.data!!)
      e.printStackTrace()
      ToastUtils.show("Permission denied, Failed to open this uri: " + intent.data!!, context)
      return false
    }

  }

  /**
   * Build an [Intent] to view the upload with given ID, handling
   * subtleties around installing packages.
   */
  private fun buildViewIntent(context: Context, id: Long): Intent? {
    val uploadManager = UploadManager.getUploadManager(context)

    val cursor = uploadManager.query(UploadManager.Query().setFilterById(id))
    try {
      if (!cursor.moveToFirst()) {
        return null
      }

      val localUri = getCursorUri(cursor, Companion.getCOLUMN_FILE_URI())
      val mimeType = getCursorString(cursor, Companion.getCOLUMN_MEDIA_TYPE())

      val intent = Intent(Intent.ACTION_VIEW)

      if ("application/vnd.android.package-archive" == mimeType) {
        // PackageInstaller doesn't like content URIs, so open file
        intent.setDataAndType(localUri, mimeType)

        // Also splice in details about where it came from
        val remoteUri = getCursorUri(cursor, Companion.getCOLUMN_REMOTE_URI())
        intent.putExtra(Intent.EXTRA_ORIGINATING_URI, remoteUri)

      } else if ("file" == localUri.scheme) {
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        intent.setDataAndType(
            localUri, mimeType)
      } else {
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        intent.setDataAndType(localUri, mimeType)
      }

      return intent
    } finally {
      cursor.close()
    }
  }

  private fun getCursorString(cursor: Cursor, column: String): String {
    return cursor.getString(cursor.getColumnIndexOrThrow(column))
  }

  private fun getCursorUri(cursor: Cursor, column: String): Uri {
    return Uri.parse(getCursorString(cursor, column))
  }

  private fun getCursorFile(cursor: Cursor, column: String): File {
    return File(cursor.getString(cursor.getColumnIndexOrThrow(column)))
  }
}
