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
     * The buffer size used to stream the data
     */

    val BUFFER_SIZE = 8192
    /**
     * The minimum amount of progress that has to be done before the progress bar gets updated
     */
    val MIN_PROGRESS_STEP = 1024
    /**
     * The minimum amount of time that has to elapse before the progress bar gets updated, in
     * ms
     */
    val MIN_PROGRESS_TIME: Long = 2000
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
     * The maximum number of redirects.
     */
    val MAX_REDIRECTS = 5 // can't be more than 7.
    /**
     * The time between a failure and the first retry after an IOException.
     * Each subsequent retry grows exponentially, doubling each time.
     * The time is in seconds.
     */
    val RETRY_FIRST_DELAY = 5
  }

  enum class UploadStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    PAUSED,
    CANCELED,
    // Marked as deleted
    DELETED,

    WAITING_TO_RETRY,
    WAITING_FOR_NETWORK,
    WAITING_FOR_WIFI,

    CAN_NOT_RESUME,
    // HTTP status code is not 2XX,
    HTTP_CODE_ERROR,
    HTTP_DATA_ERROR,
    HTTP_REDIRECT_ERROR,
    /**
     * error when reading file
     */
    FILE_ERROR,
    FILE_NOT_FOUND,
    UNKNOWN_ERROR,

    FAILED;

    internal fun isRetryable(): Boolean {
      TODO()
    }

    internal fun isOnGoing(): Boolean {
      TODO()
    }

    internal fun isDeletedOrCanceled(): Boolean {
      TODO()
    }

    internal fun isComplete(): Boolean {
      TODO()
    }

    internal fun isFailed(): Boolean {
      TODO()
    }
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
