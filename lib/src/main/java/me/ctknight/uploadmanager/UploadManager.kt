/*
 * Copyright (c) 2018. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager


import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import me.ctknight.uploadmanager.internal.Database
import me.ctknight.uploadmanager.thirdparty.FileUtils
import me.ctknight.uploadmanager.thirdparty.SingletonHolder
import me.ctknight.uploadmanager.util.UriUtils
import okhttp3.Headers
import okhttp3.HttpUrl
import java.io.File
import java.util.*


class UploadManager private constructor(context: Context) {
  private val mBaseUri = UploadContract.UPLOAD_CONTENT_URI
  private val mDatabase = Database.getInstance(context)

  fun enqueue(request: Request): Long {
    var id: Long = -1
    mDatabase.transaction {
      mDatabase.uploadManagerQueries.insertUpload(
          request.targetUrl,
          request.userAgent,
          request.headers,
          request.parts,
          request.title,
          request.description,
          request.notificationVisibility,
          request.meteredAllowed,
          request.roamingAllowed
      )
      id = mDatabase.uploadManagerQueries.lastInsertId().executeAsOne()
    }
    return id
  }

  fun cancel(vararg ids: Long) {
    if (ids.isEmpty()) {
      // called with nothing to remove!
      throw IllegalArgumentException("input param 'ids' can't be null")
    }
    mDatabase.transaction {
      ids.forEach {
        mDatabase.uploadManagerQueries.updateStatus(UploadContract.UploadStatus.CANCELED, it)
      }
    }
  }

  // TODO
  fun query(query: Query): List<UploadRecord> {
    return emptyList()
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

  class Request internal constructor(builder: Builder) {
    /**
     * @param url the HTTP or HTTPS URI to upload.
     */
    internal val targetUrl: HttpUrl = builder.targetUrl
    internal val headers: Headers? = builder.headers
    internal val parts: MutableList<Part> = builder.parts
    internal val title: String? = builder.title
    internal val description: String? = builder.description
    internal val userAgent: String? = builder.userAgent
    internal val meteredAllowed: Boolean = builder.meteredAllowed
    internal val roamingAllowed: Boolean = builder.roamingAllowed
    internal val notificationVisibility: UploadContract.Visibility = builder.notificationVisibility

    data class Builder(
        val targetUrl: HttpUrl,
        var headers: Headers,
        val parts: MutableList<Part> = ArrayList(),
        var title: String? = null,
        var description: String? = null,
        var userAgent: String? = null,
        var meteredAllowed: Boolean = true,
        var roamingAllowed: Boolean = true,
        var notificationVisibility: UploadContract.Visibility = UploadContract.Visibility.VISIBLE
    ) {
      // TODO: check args here
      fun build(): Request = Request(this)
    }
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
    private object InstanceHolder : SingletonHolder<UploadManager, Context>(::UploadManager)

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
