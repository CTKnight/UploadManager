/*
 * Copyright (c) 2018. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager


import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.CursorWrapper
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.BaseColumns

import java.io.File
import java.io.FileNotFoundException
import java.util.ArrayList
import java.util.HashMap

import me.ctknight.uploadmanager.thirdparty.FileUtils
import me.ctknight.uploadmanager.util.UriUtils

import me.ctknight.uploadmanager.UploadContract.UPLOAD_COLUMNS.COLOMN_DATA_FIELD_NAME
import me.ctknight.uploadmanager.UploadContract.UPLOAD_COLUMNS.COLUMN_ALLOW_ROAMING
import me.ctknight.uploadmanager.UploadContract.UPLOAD_COLUMNS.COLUMN_CURRENT_BYTES
import me.ctknight.uploadmanager.UploadContract.UPLOAD_COLUMNS.COLUMN_LAST_MODIFICATION
import me.ctknight.uploadmanager.UploadContract.UPLOAD_COLUMNS.COLUMN_MIME_TYPE
import me.ctknight.uploadmanager.UploadContract.UPLOAD_COLUMNS.COLUMN_TARGET_URL
import me.ctknight.uploadmanager.UploadContract.UPLOAD_COLUMNS.COLUMN_TOTAL_BYTES
import me.ctknight.uploadmanager.UploadContract.UPLOAD_COLUMNS.COLUMN_VISIBILITY
import me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.CANNOT_RESUME
import me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.DEVICE_NOT_FOUND_ERROR
import me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.FILE_ERROR
import me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.FILE_NOT_FOUND
import me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.HTTP_DATA_ERROR
import me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.MIN_ARTIFICIAL_ERROR_STATUS
import me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.PENDING
import me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.RUNNING
import me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.SUCCESS
import me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.TOO_MANY_REDIRECTS
import me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.UNHANDLED_HTTP_CODE
import me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.UNHANDLED_REDIRECT
import me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.WAITING_FOR_NETWORK
import me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.WAITING_FOR_WIFI
import me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.WAITING_TO_RETRY
import me.ctknight.uploadmanager.UploadContract.isStatusError


class UploadManager private constructor(context: Context) {
  private val mBaseUri = UploadContract.UPLOAD_URIS.CONTENT_URI

  fun enqueue(request: Request): Long {
    val values = request.toContentValues()
    val uploadUri = mResolver.insert(UploadContract.UPLOAD_URIS.CONTENT_URI, values)
    val id = java.lang.Long.parseLong(uploadUri!!.lastPathSegment!!)
    return id
  }

  fun remove(vararg ids: Long): Int {
    if (ids == null || ids.size == 0) {
      // called with nothing to remove!
      throw IllegalArgumentException("input param 'ids' can't be null")
    }
    val values = ContentValues()
    values.put(UploadContract.UPLOAD_COLUMNS.COLUMN_DELETED, 1)
    // if only one id is passed in, then include it in the uri itself.
    // this will eliminate a full database scan in the download service.
    return if (ids.size == 1) {
      mResolver.update(ContentUris.withAppendedId(mBaseUri, ids[0]), values, null, null)
    } else mResolver.update(mBaseUri, values, getWhereClauseForIds(ids),
        getWhereArgsForIds(ids))
    // TODO: 2016/2/5 use bulkinsert() instead
  }

  fun query(query: Query): Cursor? {
    val underlyingCursor = query.runQuery(mResolver, UNDERLYING_COLUMNS, mBaseUri) ?: return null
    return CursorTranslator(underlyingCursor, mBaseUri)
  }

  @Throws(FileNotFoundException::class)
  fun openUploadedFile(id: Long): ParcelFileDescriptor? {
    return mResolver.openFileDescriptor(getUploadUri(id), "r")
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

  class Request {
    private val mContext: Context
    private var mTargetUrl: Uri? = null
    private var mFileUri: Uri? = null
    private val mRequestHeaders = HashMap<String, String>()
    private val mContentDispositions = HashMap<String, String>()
    private var mFilename: String? = null
    private var mTitle: String? = null
    private var mDescription: String? = null
    private var mMimeType: String? = null
    private var mUserAgent: String? = null
    private var mDataFieldName: String? = null
    private var mMobileAllowed = true
    /**
     * can take any of the following values: [.VISIBILITY_HIDDEN]
     * [.VISIBILITY_VISIBLE_COMPLETED], [.VISIBILITY_VISIBLE],
     * [.VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION]
     */
    private var mNotificationVisibility = VISIBILITY_VISIBLE

    /**
     * @param uri the HTTP or HTTPS URI to upload.
     */
    constructor(uri: Uri?, context: Context) {
      if (uri == null) {
        throw NullPointerException()
      }
      val scheme = uri.scheme
      if (scheme == null || scheme != "http" && scheme != "https") {
        throw IllegalArgumentException("Can only upload http/https URIs: $uri")
      }
      mTargetUrl = uri
      mContext = context.applicationContext
    }

    internal constructor(uriString: String) {
      mTargetUrl = Uri.parse(uriString)
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
    fun addRequestHeader(@NonNull header: String?, @Nullable value: String?): Request {
      var value = value
      if (header == null) {
        throw NullPointerException("header cannot be null")
      }
      if (header.contains(":")) {
        throw IllegalArgumentException("header may not contain ':'")
      }
      if (value == null) {
        value = ""
      }
      mRequestHeaders[header] = value
      return this
    }

    fun addRequestHeaders(headers: Map<String, String>): Request {
      // add them one by one to avoid some Maps' not-allow-null implementation
      for (key in headers.keys) {
        addRequestHeader(key, headers[key])
      }
      return this
    }

    fun addContentDisposition(@NonNull name: String?, @Nullable value: String?): Request {
      var value = value
      if (name == null) {
        throw NullPointerException("content disposition cannot be null")
      }

      if (value == null) {
        value = ""
      }
      mContentDispositions[name] = value
      return this
    }

    fun addContentDispositions(cds: Map<String, String>): Request {
      for (key in cds.keys) {
        addContentDisposition(key, cds[key])
      }
      return this
    }

    fun addUserAgent(userAgent: String): Request {
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
     * Set the MIME content type of this upload.  This will override the content type
     * declared
     * in the server's response.
     *
     * @return this object
     * @see [HTTP/1.1
     * Media Types](http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html.sec3.7)
     */
    fun setMimeType(@Nullable mimeType: String): Request {
      //call this only when you have to modify.
      mMimeType = mimeType
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
    fun setNotificationVisibility(visibility: Int): Request {
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

    /**
     * set data field name.
     * for example :
     * ------WebKitFormBoundary6naClQj9yERx1WNV
     * Content-Disposition: form-data; name="file"; filename="sum.docx"
     * Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document
     *
     *
     * where name = "file" the "file" is field name.
     */
    fun setDataFieldName(dataFieldName: String): Request {
      mDataFieldName = dataFieldName
      return this
    }

    /**
     * @return ContentValues to be passed to UploadProvider.insert()
     */
    internal fun toContentValues(): ContentValues {
      val values = ContentValues()
      assert(mTargetUrl != null)
      values.put(COLUMN_TARGET_URL, mTargetUrl!!.toString())

      if (!mRequestHeaders.isEmpty()) {
        encodeHttpHeaders(values)
      }

      if (!mContentDispositions.isEmpty()) {
        encodePayload(values)
      }
      //NOTE: if you change items here , you should also go to UploadProvider and add them in filteredValue.
      updateFilename(mFileUri)
      putIfNonNull(values, COLUMN_FILE_URI, mFileUri)
      putIfNonNull(values, COLUMN_MIME_TYPE, if (mMimeType == null) mContext.contentResolver.getType(mFileUri!!) else mMimeType)
      putIfNonNull(values, COLUMN_TITLE, mFilename)
      putIfNonNull(values, COLOMN_DATA_FIELD_NAME, if (mDataFieldName == null) "file" else mDataFieldName)
      //use filename as default title.
      putIfNonNull(values, COLUMN_TITLE, mTitle)
      putIfNonNull(values, COLUMN_DESCRIPTION, mDescription)
      putIfNonNull(values, COLUMN_USER_AGENT, mUserAgent)

      values.put(COLUMN_VISIBILITY, mNotificationVisibility)
      values.put(COLUMN_ALLOW_ROAMING, mMobileAllowed)
      return values
    }

    private fun encodeHttpHeaders(values: ContentValues) {
      var index = 0
      for ((key, value) in mRequestHeaders) {
        val headerString = entry.key + ": " + entry.value
        values.put(UploadContract.RequestContent.INSTANCE.getINSERT_KEY_PREFIX() + index, headerString)
        index++
      }
    }

    private fun encodePayload(values: ContentValues) {
      var index = 0
      for ((key, value) in mContentDispositions) {
        val cdString = entry.key + ": " + entry.value
        values.put(UploadContract.RequestContent.INSTANCE.getINSERT_CD_PREFIX() + index, cdString)
        index++
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

    private fun putIfNonNull(contentValues: ContentValues, key: String, value: Any?) {
      if (value != null) {
        contentValues.put(key, value.toString())
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
     * Change the sort order of the returned Cursor.
     *
     * @param column    one of the COLUMN_* constants; currently, only
     * { #COLUMN_LAST_MODIFIED_TIMESTAMP} and { #COLUMN_TOTAL_SIZE_BYTES} are
     * supported.
     * @param direction either [.ORDER_ASCENDING] or [.ORDER_DESCENDING]
     * @return this object
     */
    fun orderBy(column: String, direction: Int): Query {
      if (direction != ORDER_ASCENDING && direction != ORDER_DESCENDING) {
        throw IllegalArgumentException("Invalid direction: $direction")
      }

      if (column == COLUMN_LAST_MODIFIED_TIMESTAMP) {
        mOrderByColumn = UploadContract.UPLOAD_COLUMNS.COLUMN_LAST_MODIFICATION
      } else if (column == COLUMN_TOTAL_SIZE_BYTES) {
        mOrderByColumn = UploadContract.UPLOAD_COLUMNS.COLUMN_TOTAL_BYTES
      } else {
        throw IllegalArgumentException("Cannot order by $column")
      }
      mOrderDirection = direction
      return this
    }

    /**
     * Run this query using the given ContentResolver.
     *
     * @param projection the projection to pass to ContentResolver.query()
     * @return the Cursor returned by ContentResolver.query()
     */
    internal fun runQuery(resolver: ContentResolver, projection: Array<String>, baseUri: Uri): Cursor? {
      val uri = baseUri
      val selectionParts = ArrayList<String>()
      var selectionArgs: Array<String>? = null

      if (mIds != null) {
        selectionParts.add(getWhereClauseForIds(mIds!!))
        selectionArgs = getWhereArgsForIds(mIds!!)
      }

      if (mStatusFlags != null) {
        val parts = ArrayList<String>()
        if (mStatusFlags and STATUS_PENDING != 0) {
          parts.add(statusClause("=", UploadContract.UPLOAD_STATUS.PENDING))
        }
        if (mStatusFlags and STATUS_RUNNING != 0) {
          parts.add(statusClause("=", UploadContract.UPLOAD_STATUS.RUNNING))
        }
        if (mStatusFlags and STATUS_PAUSED != 0) {
          //parts.add(statusClause("=", UploadContract.UPLOAD_STATUS.PAUSED_BY_APP));
          parts.add(statusClause("=", UploadContract.UPLOAD_STATUS.WAITING_TO_RETRY))
          parts.add(statusClause("=", UploadContract.UPLOAD_STATUS.WAITING_FOR_NETWORK))
          parts.add(statusClause("=", UploadContract.UPLOAD_STATUS.WAITING_FOR_WIFI))
        }
        if (mStatusFlags and STATUS_SUCCESSFUL != 0) {
          parts.add(statusClause("=", UploadContract.UPLOAD_STATUS.SUCCESS))
        }
        if (mStatusFlags and STATUS_FAILED != 0) {
          parts.add("(" + statusClause(">=", 400)
              + " AND " + statusClause("<", 600) + ")")
        }
        selectionParts.add(joinStrings(" OR ", parts))
      }


      // only return rows which are not marked 'deleted = 1'
      selectionParts.add(UploadContract.UPLOAD_COLUMNS.COLUMN_DELETED + " != '1'")

      val selection = joinStrings(" AND ", selectionParts)
      val orderDirection = if (mOrderDirection == ORDER_ASCENDING) "ASC" else "DESC"
      val orderBy = mOrderByColumn + " " + orderDirection

      return resolver.query(uri, projection, selection, selectionArgs, orderBy)
    }

    private fun joinStrings(joiner: String, parts: Iterable<String>): String {
      val builder = StringBuilder()
      var first = true
      for (part in parts) {
        if (!first) {
          builder.append(joiner)
        }
        builder.append(part)
        first = false
      }
      return builder.toString()
    }

    private fun statusClause(operator: String, value: Int): String {
      return UploadContract.UPLOAD_COLUMNS.COLUMN_STATUS + operator + "'" + value + "'"
    }

    companion object {
      /**
       * Constant for use with [.orderBy]
       *
       * @hide
       */
      val ORDER_ASCENDING = 1

      /**
       * Constant for use with [.orderBy]
       *
       * @hide
       */
      val ORDER_DESCENDING = 2
    }
  }

  /**
   * This class wraps a cursor returned by UploadProvider -- the "underlying cursor" -- and
   * presents a different set of columns, those defined in the DownloadManager.COLUMN_*
   * constants.
   * Some columns correspond directly to underlying values while others are computed from
   * underlying data.
   */
  private class CursorTranslator(cursor: Cursor, private val mBaseUri: Uri) : CursorWrapper(cursor) {

    private// return content URI for cache upload
    val localUri: String
      get() {
        val uploadId = getLong(getColumnIndex(UploadContract.UPLOAD_COLUMNS._ID))
        return ContentUris.withAppendedId(mBaseUri, uploadId).toString()
      }

    override fun getInt(columnIndex: Int): Int {
      return getLong(columnIndex).toInt()
    }

    override fun getLong(columnIndex: Int): Long {
      return if (getColumnName(columnIndex) == COLUMN_REASON) {
        getReason(super.getInt(getColumnIndex(COLUMN_STATUS)))
      } else if (getColumnName(columnIndex) == COLUMN_STATUS) {
        translateStatus(super.getInt(getColumnIndex(COLUMN_STATUS))).toLong()
      } else {
        super.getLong(columnIndex)
      }
    }

    override fun getString(columnIndex: Int): String {
      return if (getColumnName(columnIndex) == COLUMN_LOCAL_URI)
        localUri
      else
        super.getString(columnIndex)
    }

    private fun getReason(status: Int): Long {
      when (translateStatus(status)) {
        STATUS_FAILED -> return getErrorCode(status)

        STATUS_PAUSED -> return getPausedReason(status)

        else -> return 0 // arbitrary value when status is not an error
      }
    }

    private fun getPausedReason(status: Int): Long {
      when (status) {
        WAITING_TO_RETRY -> return PAUSED_WAITING_TO_RETRY.toLong()

        WAITING_FOR_NETWORK -> return PAUSED_WAITING_FOR_NETWORK.toLong()

        WAITING_FOR_WIFI -> return PAUSED_QUEUED_FOR_WIFI.toLong()

        else -> return PAUSED_UNKNOWN.toLong()
      }
    }

    private fun getErrorCode(status: Int): Long {
      if (400 <= status && status < MIN_ARTIFICIAL_ERROR_STATUS || 500 <= status && status < 600) {
        // HTTP status code
        return status.toLong()
      }

      when (status) {
        FILE_ERROR, FILE_NOT_FOUND -> return ERROR_FILE_ERROR.toLong()

        UNHANDLED_HTTP_CODE, UNHANDLED_REDIRECT -> return ERROR_UNHANDLED_HTTP_CODE.toLong()

        HTTP_DATA_ERROR -> return ERROR_HTTP_DATA_ERROR.toLong()

        TOO_MANY_REDIRECTS -> return ERROR_TOO_MANY_REDIRECTS.toLong()

        DEVICE_NOT_FOUND_ERROR -> return ERROR_DEVICE_NOT_FOUND.toLong()

        CANNOT_RESUME -> return ERROR_CANNOT_RESUME.toLong()

        else -> return ERROR_UNKNOWN.toLong()
      }
    }

    private fun translateStatus(status: Int): Int {
      when (status) {
        PENDING -> return STATUS_PENDING

        RUNNING -> return STATUS_RUNNING

        STATUS_PAUSED -> return STATUS_PAUSED

        SUCCESS -> return STATUS_SUCCESSFUL
        WAITING_TO_RETRY, WAITING_FOR_NETWORK, WAITING_FOR_WIFI -> {
          assert(isStatusError(status))
          return STATUS_FAILED
        }
        else -> {
          assert(isStatusError(status))
          return STATUS_FAILED
        }
      }
    }
  }

  companion object {

    /**
     * An identifier for a particular upload, unique across the system.  Clients use this ID to
     * make subsequent calls related to the upload.
     */
    val COLUMN_ID = UploadContract.UPLOAD_COLUMNS._ID

    /**
     * The client-supplied title for this upload.  This will be displayed in system notifications.
     * Defaults to the empty string.
     */
    val COLUMN_TITLE = UploadContract.UPLOAD_COLUMNS.COLUMN_TITLE

    /**
     * The client-supplied description of this upload.  This will be displayed in system
     * notifications.  Defaults to the empty string.
     */
    val COLUMN_DESCRIPTION = UploadContract.UPLOAD_COLUMNS.COLUMN_DESCRIPTION

    /**
     * URI to be uploaded.
     */
    val COLUMN_REMOTE_URI = UploadContract.UPLOAD_COLUMNS.COLUMN_TARGET_URL

    val COLUMN_FILE_URI = UploadContract.UPLOAD_COLUMNS.COLUMN_FILE_URI

    /**
     * Internet Media Type of the uploaded file.  If no value is provided upon creation, this will
     * initially be null and will be filled in based on the server's response once the upload has
     * started.
     *
     * @see [RFC 1590, defining Media Types](http://www.ietf.org/rfc/rfc1590.txt)
     */
    val COLUMN_MEDIA_TYPE = "media_type"

    /**
     * Total size of the upload in bytes.  This will initially be -1 and will be filled in once
     * the upload starts.
     */
    val COLUMN_TOTAL_SIZE_BYTES = "total_size"

    /**
     * Uri where uploaded file will be stored.  If a destination is supplied by client, that URI
     * will be used here.  Otherwise, the value will initially be null and will be filled in with a
     * generated URI once the upload has started.
     */
    val COLUMN_LOCAL_URI = "localuri"

    /**
     * Current status of the upload, as one of the * constants.
     */
    val COLUMN_STATUS = UploadContract.UPLOAD_COLUMNS.COLUMN_STATUS

    val COLUMN_USER_AGENT = UploadContract.RequestContent.INSTANCE.getCOLUMN_USER_AGENT()

    /**
     * Provides more detail on the status of the upload.  Its meaning depends on the value of
     * [.COLUMN_STATUS].
     *
     *
     * When [.COLUMN_STATUS] is { #STATUS_FAILED}, this indicates the type of error that
     * occurred.  If an HTTP error occurred, this will hold the HTTP status code as defined in RFC
     * 2616.  Otherwise, it will hold one of the ERROR_* constants.
     *
     *
     * When [.COLUMN_STATUS] is { #STATUS_PAUSED}, this indicates why the upload is
     * paused.  It will hold one of the PAUSED_* constants.
     *
     *
     * If [.COLUMN_STATUS] is neither { #STATUS_FAILED} nor { #STATUS_PAUSED}, this
     * column's value is undefined.
     *
     * @see [RFC 2616
     * status codes](http://www.w3.org/Protocols/rfc2616/rfc2616-sec6.html.sec6.1.1)
     */
    val COLUMN_REASON = "reason"

    /**
     * Number of bytes upload so far.
     */
    val COLUMN_BYTES_UPLOADED_SO_FAR = COLUMN_CURRENT_BYTES

    /**
     * Timestamp when the upload was last modified, in [ System.currentTimeMillis()][System.currentTimeMillis] (wall clock time in UTC).
     */
    val COLUMN_LAST_MODIFIED_TIMESTAMP = COLUMN_LAST_MODIFICATION

    /**
     * Value of [.COLUMN_STATUS] when the upload is waiting to start.
     */
    val STATUS_PENDING = 1 shl 0

    /**
     * Value of [.COLUMN_STATUS] when the upload is currently running.
     */
    val STATUS_RUNNING = 1 shl 1

    /**
     * Value of [.COLUMN_STATUS] when the upload is waiting to retry or resume.
     */
    val STATUS_PAUSED = 1 shl 2

    /**
     * Value of [.COLUMN_STATUS] when the upload has successfully completed.
     */
    val STATUS_SUCCESSFUL = 1 shl 3

    /**
     * Value of [.COLUMN_STATUS] when the upload has failed (and will not be retried).
     */
    val STATUS_FAILED = 1 shl 4

    /**
     * Value of [.COLUMN_STATUS] when the upload has failed (and will not be retried).
     */
    val STATUS_CANCELLED = 1 shl 5

    /**
     * Value of COLUMN_ERROR_CODE when the upload has completed with an error that doesn't fit
     * under any other error code.
     */
    val ERROR_UNKNOWN = 1000

    /**
     * Value of [.COLUMN_REASON] when a storage issue arises which doesn't fit under any
     * other error code. Use the more specific [.ERROR_INSUFFICIENT_SPACE] and
     * [.ERROR_DEVICE_NOT_FOUND] when appropriate.
     */
    val ERROR_FILE_ERROR = 1001

    /**
     * Value of [.COLUMN_REASON] when an HTTP code was received that upload manager
     * can't handle.
     */
    val ERROR_UNHANDLED_HTTP_CODE = 1002

    /**
     * Value of [.COLUMN_REASON] when an error receiving or processing data occurred at
     * the HTTP level.
     */
    val ERROR_HTTP_DATA_ERROR = 1004

    /**
     * Value of [.COLUMN_REASON] when there were too many redirects.
     */
    val ERROR_TOO_MANY_REDIRECTS = 1005

    /**
     * Value of [.COLUMN_REASON] when there was insufficient storage space. Typically,
     * this is because the SD card is full.
     */
    val ERROR_INSUFFICIENT_SPACE = 1006

    /**
     * Value of [.COLUMN_REASON] when no external storage device was found. Typically,
     * this is because the SD card is not mounted.
     */
    val ERROR_DEVICE_NOT_FOUND = 1007

    /**
     * Value of [.COLUMN_REASON] when some possibly transient error occurred but we can't
     * resume the upload.
     */
    val ERROR_CANNOT_RESUME = 1008

    /**
     * Value of [.COLUMN_REASON] when the requested destination file already exists (the
     * upload manager will not overwrite an existing file).
     */
    val ERROR_FILE_ALREADY_EXISTS = 1009

    /**
     * Value of [.COLUMN_REASON] when the upload has failed because of
     * { NetworkPolicyManager} controls on the requesting application.
     *
     * @hide
     */
    val ERROR_BLOCKED = 1010

    /**
     * Value of [.COLUMN_REASON] when the upload is paused because some network error
     * occurred and the upload manager is waiting before retrying the request.
     */
    val PAUSED_WAITING_TO_RETRY = 1

    /**
     * Value of [.COLUMN_REASON] when the upload is waiting for network connectivity to
     * proceed.
     */
    val PAUSED_WAITING_FOR_NETWORK = 2

    /**
     * Value of [.COLUMN_REASON] when the upload exceeds a size limit for uploads over
     * the mobile network and the upload manager is waiting for a Wi-Fi connection to proceed.
     */
    val PAUSED_QUEUED_FOR_WIFI = 3

    /**
     * Value of [.COLUMN_REASON] when the upload is paused for some other reason.
     */
    val PAUSED_UNKNOWN = 4

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
     * Intent extra included with [.ACTION_VIEW_UPLOADS] to start UploadApp in
     * sort-by-size mode.
     */
    val INTENT_EXTRAS_SORT_BY_SIZE = "android.app.UploadManager.extra_sortBySize"

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
    /**
     * columns to request from DownloadProvider.
     *
     * @hide
     */
    val UNDERLYING_COLUMNS = arrayOf(BaseColumns._ID, COLUMN_TITLE, COLUMN_DESCRIPTION, COLUMN_STATUS, COLUMN_TARGET_URL, COLUMN_FILE_URI + " AS " + COLUMN_FILE_URI, COLUMN_MIME_TYPE + " AS " + COLUMN_MEDIA_TYPE, COLUMN_TOTAL_BYTES + " AS " + COLUMN_TOTAL_SIZE_BYTES, COLUMN_LAST_MODIFICATION + " AS " + COLUMN_LAST_MODIFIED_TIMESTAMP, COLUMN_CURRENT_BYTES + " AS " + COLUMN_BYTES_UPLOADED_SO_FAR,
        /* add the following 'computed' columns to the cursor.
       * they are not 'returned' by the database, but their inclusion
       * eliminates need to have lot of methods in CursorTranslator
       */
        "'placeholder' AS $COLUMN_LOCAL_URI", "'placeholder' AS $COLUMN_REASON")
    @Volatile
    private var mUploadManager: UploadManager? = null

    /**
     * The only to get a instance of UploadManager
     * this method is thread safe
     *
     * @param context no need to be Application
     * @return a singleton instance of UploadManager
     */
    fun getUploadManger(context: Context): UploadManager? {
      if (mUploadManager == null) {
        synchronized(UploadManager::class.java) {
          if (mUploadManager == null) {
            mUploadManager = UploadManager(context.applicationContext)
          }
        }
      }
      return mUploadManager
    }

    /**
     * Get a parameterized SQL WHERE clause to select a bunch of IDs.
     */
    internal fun getWhereClauseForIds(ids: LongArray): String {
      val whereClause = StringBuilder()
      whereClause.append("(")
      for (i in ids.indices) {
        if (i > 0) {
          whereClause.append("OR ")
        }
        whereClause.append(BaseColumns._ID)
        whereClause.append(" = ? ")
      }
      whereClause.append(")")
      return whereClause.toString()
    }

    /**
     * Get the selection args for a clause returned by [.getWhereClauseForIds].
     */
    internal fun getWhereArgsForIds(ids: LongArray): Array<String> {
      val whereArgs = arrayOfNulls<String>(ids.size)
      for (i in ids.indices) {
        whereArgs[i] = java.lang.Long.toString(ids[i])
      }
      return whereArgs
    }
  }


}
