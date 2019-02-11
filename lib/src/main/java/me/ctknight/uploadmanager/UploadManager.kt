/*
 * Copyright (c) 2018. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager


import android.content.Context
import me.ctknight.uploadmanager.internal.Database
import me.ctknight.uploadmanager.internal.Helpers
import me.ctknight.uploadmanager.thirdparty.SingletonHolder
import okhttp3.Headers
import okhttp3.HttpUrl
import java.util.*


class UploadManager private constructor(val context: Context) {
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
    Helpers.scheduleJob(context, id)
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
  fun query(query: UploadManager.Query): com.squareup.sqldelight.Query<List<UploadRecord>> {
    TODO()
  }

  fun restartUpload(vararg ids: Long) {
    mDatabase.transaction {
      ids.forEach {
        mDatabase.uploadManagerQueries.restartUpload(0, -1,
            UploadContract.UploadStatus.PENDING, 0, null, it)
      }
    }
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

  class Query {}

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
