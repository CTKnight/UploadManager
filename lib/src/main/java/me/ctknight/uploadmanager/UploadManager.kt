/*
 * Copyright (c) 2018. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager


import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri

import java.io.File
import java.util.ArrayList

import me.ctknight.uploadmanager.thirdparty.FileUtils
import me.ctknight.uploadmanager.util.UriUtils
import me.ctknight.uploadmanager.internal.Database
import me.ctknight.uploadmanager.thirdparty.SingletonHolder
import okhttp3.Headers
import okhttp3.HttpUrl


class UploadManager private constructor(context: Context) {
  private val mBaseUri = UploadContract.UPLOAD_CONTENT_URI
  private val mDatabase = Database.getInstance(context)

  fun enqueue(request: Request): Long {
    TODO()
  }

  fun cancel(vararg ids: Long): Int {
    if (ids.isEmpty()) {
      // called with nothing to remove!
      throw IllegalArgumentException("input param 'ids' can't be null")
    }
    TODO()
  }

  fun query(query: Query): List<UploadRecord> {
    TODO()
  }

  fun getUriForUploadedFile(id: Long): Uri? {
    // to check if the file is in cache, get its destination from the database
    val query = Query().setFilterById(id)
    var cursor: Cursor? = null
    try {
      cursor = query(query)
      if (cursor == null) {
        return null
      }
      if (cursor.moveToFirst()) {
        val status = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_STATUS))
        if (UploadManager.ERROR_FILE_ERROR != status) {
          return ContentUris.withAppendedId(UploadContract.UPLOAD_URIS.CONTENT_URI, id)
        }
      }
    } finally {
      cursor?.close()
    }
    // uploaded file not found or its status is not 'successfully completed'
    return null
  }

  fun getMimeTypeForUploadedFile(id: Long): String? {
    val query = Query().setFilterById(id)
    var cursor: Cursor? = null
    try {
      cursor = query(query)
      if (cursor == null) {
        return null
      }
      while (cursor.moveToFirst()) {
        return cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MEDIA_TYPE))
      }
    } finally {
      cursor?.close()
    }
    //uploaded file not found or its status is not 'successfully completed'
    return null
  }

  fun restartUpload(vararg ids: Long) {
    val cursor = query(Query().setFilterById(*ids))
    try {
      cursor!!.moveToFirst()
      while (!cursor.isAfterLast) {
        val status = cursor.getInt(cursor.getColumnIndex(COLUMN_STATUS))
        if (status != STATUS_FAILED && status != STATUS_CANCELLED) {
          throw IllegalArgumentException("Cannot restart incomplete upload: " + cursor.getLong(cursor.getColumnIndex(COLUMN_ID)))
        }
        cursor.moveToNext()
      }
    } finally {
      cursor!!.close()
    }

    val values = ContentValues()
    values.put(UploadContract.UPLOAD_COLUMNS.COLUMN_CURRENT_BYTES, 0)
    values.put(UploadContract.UPLOAD_COLUMNS.COLUMN_TOTAL_BYTES, -1)
    values.put(UploadContract.UPLOAD_COLUMNS.COLUMN_STATUS, UploadContract.UPLOAD_STATUS.PENDING)
    values.put(UploadContract.UPLOAD_COLUMNS.COLUMN_NUM_FAILED, 0)
    mResolver.update(mBaseUri, values, getWhereClauseForIds(ids), getWhereArgsForIds(ids))
  }

  fun getUploadUri(id: Long): Uri {
    return ContentUris.withAppendedId(mBaseUri, id)
  }

  // TODO: use Builder pattern
  class Request {
    private lateinit var mContext: Context
    private var mTargetUrl: HttpUrl
    private var mRequestHeaders: Headers? = null
    private val mParts: MutableList<Part> = ArrayList()
    private var mTitle: String? = null
    private var mDescription: String? = null
    private var mUserAgent: String? = null
    private var mMobileAllowed = true
    /**
     * can take any of the following values: [.VISIBILITY_HIDDEN]
     * [.VISIBILITY_VISIBLE_COMPLETED], [.VISIBILITY_VISIBLE],
     * [.VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION]
     */
    private var mNotificationVisibility:UploadContract.Visibility = UploadContract.Visibility.VISIBLE

    /**
     * @param url the HTTP or HTTPS URI to upload.
     */
    constructor(url: HttpUrl, context: Context) {
      mTargetUrl = url
      mContext = context.applicationContext
    }

    /**
     * Set the local destination for the uploaded file. Must be a file URI to a path on
     * external storage, and the calling application must have the WRITE_EXTERNAL_STORAGE
     * permission.
     *
     *
     * The uploaded file is not scanned by MediaScanner.
     * But it can be made scannable by calling { #allowScanningByMediaScanner()}.
     *
     *
     * By default, uploads are saved to a generated filename in the shared upload cache and
     * may be deleted by the system at any time to reclaim space.
     *
     * @return this object
     */
    fun setFileUri(uri: Uri): Request {
      mFileUri = uri

      return this
    }

    /**
     * Add an HTTP header to be included with the upload request.  The header will be added
     * to
     * the end of the list.
     *
     * @param header HTTP header name
     * @param value  header value
     * @return this object
     * @see [HTTP/1.1
     * Message Headers](http://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html.sec4.2)
     */
    private fun setRequestHeaders(headers: Headers): Request {
      mRequestHeaders = headers
      return this
    }

    fun addPart(part: Part): Request{
      mParts.add(part)
      return this
    }

    fun setUserAgent(userAgent: String): Request {
      mUserAgent = userAgent
      return this
    }

    /**
     * Set the title of this upload, to be displayed in notifications (if enabled).  If no
     * title is given, a default one will be assigned based on the upload filename, once the
     * upload starts.
     *
     * @return this object
     */
    fun setTitle(title: String): Request {
      mTitle = title
      return this
    }

    /**
     * Set a description of this upload, to be displayed in notifications (if enabled)
     *
     * @return this object
     */
    fun setDescription(description: String): Request {
      mDescription = description
      return this
    }

    /**
     * Control whether a system notification is posted by the upload manager while this
     * upload is running or when it is completed.
     * If enabled, the upload manager posts notifications about uploads
     * through the system [android.app.NotificationManager].
     * By default, a notification is shown only when the upload is in progress.
     *
     *
     * It can take the following values: [.VISIBILITY_HIDDEN],
     * [.VISIBILITY_VISIBLE],
     * [.VISIBILITY_VISIBLE_COMPLETED].
     *
     *
     * If set to [.VISIBILITY_HIDDEN], this requires the permission
     * android.permission.UPLOAD_WITHOUT_NOTIFICATION.
     *
     * @param visibility the visibility setting value
     * @return this object
     */
    fun setNotificationVisibility(visibility: UploadContract.Visibility): Request {
      if ()
      mNotificationVisibility = visibility
      return this
    }

    /**
     * Set whether this upload may proceed over a roaming connection.  By default, roaming is
     * allowed.
     *
     * @param allowed whether to allow a roaming connection to be used
     * @return this object
     */
    fun setAllowedOverRoaming(allowed: Boolean): Request {
      mMobileAllowed = allowed
      return this
    }

    fun updateFilename(uri: Uri?) {
      val fullPath = FileUtils.getPath(mContext, uri)
      if (fullPath != null) {
        mFilename = File(fullPath).name
      } else {
        val info = UriUtils.queryOpenableInfo(uri, mContext)
        if (info != null) {
          mFilename = info.displayName
        }
      }
    }

    companion object {
      /**
       * This upload is visible but only shows in the notifications
       * while it's in progress.
       */
      val VISIBILITY_VISIBLE = 0
      /**
       * This upload is visible and shows in the notifications while
       * in progress and after completion.
       */
      val VISIBILITY_VISIBLE_COMPLETED = 1
      /**
       * This upload doesn't show in the UI or in the notifications.
       */
      val VISIBILITY_HIDDEN = 5
      /**
       * This upload shows in the notifications after completion ONLY.
       * It is usuable only with
       * { UploadManager.addCompletedUpload(String, String,
       * boolean, String, String, long, boolean)}.
       */
      val VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION = 3

    }
  }

  /**
   * This class may be used to filter upload manager queries.
   */
  class Query {

    private var mIds: LongArray? = null
    private var mStatusFlags: Int? = null
    private var mOrderByColumn = COLUMN_LAST_MODIFICATION
    private var mOrderDirection = ORDER_DESCENDING

    /**
     * Include only the uploads with the given IDs.
     *
     * @return this object
     */
    fun setFilterById(vararg ids: Long): Query {
      mIds = ids
      return this
    }

    /**
     * Include only uploads with status matching any the given status flags.
     *
     * @param flags any combination of the * bit flags
     * @return this object
     */
    fun setFilterByStatus(flags: Int): Query {
      mStatusFlags = flags
      return this
    }

    /**
     * Run this query using the given ContentResolver.
     *
     * @param projection the projection to pass to ContentResolver.query()
     * @return the Cursor returned by ContentResolver.query()
     */
    internal fun runQuery(database: UploadDatabase): List<UploadRecord> {
      TODO()
    }
  }
  companion object {
    private object InstanceHolder: SingletonHolder<UploadManager, Context>(::UploadManager)
    /**
     * The only to get a instance of UploadManager
     * this method is thread safe
     *
     * @param context no need to be Application
     * @return a singleton instance of UploadManager
     */
    @JvmStatic
    val getUploadManager = InstanceHolder::getInstance
    /**
     * Broadcast intent action sent by the upload manager when a upload completes.
     */
    val ACTION_UPLOAD_COMPLETE = "me.ctknight.uploadmanager.intent.action.UPLOAD_COMPLETE"

    /**
     * Broadcast intent action sent by the upload manager when the user clicks on a running
     * upload, either from a system notification or from the uploads UI.
     */
    val ACTION_NOTIFICATION_CLICKED = "me.ctknight.uploadmanager.intent.action.UPLOAD_NOTIFICATION_CLICKED"

    /**
     * Intent action to launch an activity to display all uploads.
     */
    val ACTION_VIEW_UPLOADS = "me.ctknight.uploadmanager.intent.action.VIEW_UPLOADS"

    /**
     * Intent extra included with [.ACTION_UPLOAD_COMPLETE] intents, indicating the ID (as a
     * long) of the upload that just completed.
     */
    val EXTRA_UPLOAD_ID = "extra_upload_id"

    /**
     * When clicks on multiple notifications are received, the following
     * provides an array of upload ids corresponding to the upload notification that was
     * clicked. It can be retrieved by the receiver of this
     * Intent using [android.content.Intent.getLongArrayExtra].
     */
    val EXTRA_NOTIFICATION_CLICK_UPLOAD_IDS = "extra_click_upload_ids"
  }
}
