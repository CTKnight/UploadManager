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
import me.ctknight.uploadmanager.UploadManager
import me.ctknight.uploadmanager.internal.Database
import java.io.File

object OpenHelper {
  private val TAG = LogUtils.makeTag<OpenHelper>()

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
    return try {
      context.startActivity(intent)
      true
    } catch (e: ActivityNotFoundException) {
      // TODO: use context.getString()
      Log.w(TAG, "Failed to start $intent: $e")
      ToastUtils.show("No compatible app to view: " + intent.data!!, context)
      false
    } catch (e: SecurityException) {
      // TODO: 2016/5/6 add to doc: make sure Uris have granted permission.
      Log.e(TAG, "startViewIntent: failed to open url" + intent.data!!)
      e.printStackTrace()
      ToastUtils.show("Permission denied, Failed to open this uri: " + intent.data!!, context)
      false
    }
  }

  /**
   * Build an [Intent] to view the upload with given ID, handling
   * subtleties around installing packages.
   */
  private fun buildViewIntent(context: Context, id: Long): Intent? {
    val uploadManager = UploadManager.getUploadManager(context)

    val record = Database.getInstance(context).uploadManagerQueries
        .selectById(id).executeAsOneOrNull()
    record ?: return null
    val fileInfoList = record.Parts.mapNotNull { it.fileInfo }
    return when (fileInfoList.size) {
      0 -> null
      1 -> {
        val fileInfo = fileInfoList.first()
        val localUri = fileInfo.fileUri
        val mimeType = fileInfo.mimeType?.toString()

        val intent = Intent(Intent.ACTION_VIEW)

        when (mimeType) {
          "application/vnd.android.package-archive" -> {
            // PackageInstaller doesn't like content URIs, so open file
            intent.setDataAndType(localUri, mimeType)

            // Also splice in details about where it came from
            intent.putExtra(Intent.EXTRA_ORIGINATING_URI, localUri)
          }
          else -> {
            if ("file" == localUri.scheme) {
              intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
              intent.setDataAndType(
                  localUri, mimeType)
            } else {
              intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
              intent.setDataAndType(localUri, mimeType)
            }
          }
        }
        intent
      }
      else -> {
        // multiple files, broadcast a list
        null
      }
    }
  }
}
