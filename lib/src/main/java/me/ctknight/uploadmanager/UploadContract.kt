/*
 * Copyright (c) 2019. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager

import android.net.Uri

object UploadContract {

  internal val ACTION_PREFIX = "me.ctknight.uploadmanager.action"

  enum class NotificationAction(val actionString: String) {
    Open("$ACTION_PREFIX.UPLOAD_OPEN"),
    List("$ACTION_PREFIX.UPLOAD_LIST"),
    Retry("$ACTION_PREFIX.UPLOAD_RETRY"),
    ManualRedo("$ACTION_PREFIX.UPLOAD_REDO"),
    Cancel("$ACTION_PREFIX.UPLOAD_CANCEL"),
    Hide("$ACTION_PREFIX.UPLOAD_HIDE");

    companion object {
      fun fromActionString(actionString: String): NotificationAction? =
          NotificationAction.values().firstOrNull { it.actionString == actionString }
    }
  }


  val UPLOAD_CONTENT_URI: Uri = Uri.parse("")

  object Constants {
    /**
     * The minimum amount of progress that has to be done before the progress bar gets updated
     */
    val MIN_PROGRESS_STEP = 1024
    /**
     * The minimum amount of time that has to elapse before the progress bar gets updated, in
     * ms
     */
    val MIN_PROGRESS_TIME: Long = 1000
    /**
     * The number of times that the upload manager will retry its network
     * operations when no progress is happening before it gives up.
     */
    val MAX_RETRIES = 5
    /**
     * The minimum amount of time that the upload manager accepts for
     * a Retry-After response header with a parameter in delta-seconds.
     */
    val MIN_RETRY_AFTER = 5 // 5s
    /**
     * The maximum amount of time that the upload manager accepts for
     * a Retry-After response header with a parameter in delta-seconds.
     */
    val MAX_RETRY_AFTER = 5 * 60 // 5 minutes
    /**
     * The time between a failure and the first retry after an IOException.
     * Each subsequent retry grows exponentially, doubling each time.
     * The time is in seconds.
     */
    val RETRY_FIRST_DELAY = 5
  }

  enum class UploadStatus(private val statusCode: Int) {
    /*
     * Lists the states that the download manager can set on a download
     * to notify applications of the download progress.
     * The codes follow the HTTP families:<br>
     * 1xx: informational<br>
     * 2xx: success<br>
     * 3xx: redirects (not used by the download manager)<br>
     * 4xx: client errors<br>
     * 5xx: server errors
     */
    PENDING(100),
    RUNNING(102),
    PAUSED(103),
    WAITING_TO_RETRY(194),
    WAITING_FOR_NETWORK(195),
    WAITING_FOR_WIFI(196),

    SUCCESS(200),

    CAN_NOT_RESUME(489),
    CANCELED(490),
    UNKNOWN_ERROR(491),
    /**
     * error when reading file
     */
    FILE_ERROR(492),
    FILE_NOT_FOUND(493),
    // HTTP status code is not 2XX,
    HTTP_CODE_ERROR(494),
    HTTP_DATA_ERROR(495),
    HTTP_REDIRECT_ERROR(496),
    // Marked as deleted
    DELETED(497),
    FAILED(498);

    internal fun isRetryable(): Boolean =
        statusCode in 194..197

    internal fun isOnGoing(): Boolean =
        this == RUNNING

    internal fun isDeletedOrCanceled(): Boolean = this == DELETED || this == CANCELED

    /**
     * Returns whether the upload has completed (either with success or
     * error).
     */
    internal fun isCompleted(): Boolean =
        statusCode in 200..300 || statusCode in 400..600

    internal fun isFailed(): Boolean =
        statusCode >= 300

    internal fun isSuccess(): Boolean =
        statusCode == 200
  }

  enum class Visibility {
    VISIBLE,
    HIDDEN,
    VISIBLE_COMPLETE,
    HIDDEN_UNTIL_COMPLETE,
    HIDDEN_COMPLETE
  }

  enum class NetworkState {
    OK,
    CANNOT_USE_ROAMING,
    NO_CONNECTION
  }
}
