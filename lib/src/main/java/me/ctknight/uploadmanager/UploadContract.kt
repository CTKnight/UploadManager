/*
 * Copyright (c) 2019. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager

import android.content.ContentResolver
import android.net.Uri

import me.ctknight.uploadmanager.UploadInfo

object UploadContract {

  val ACTION_OPEN = "me.ctknight.uploadmanager.action.UPLOAD_OPEN"
  val ACTION_LIST = "me.ctknight.uploadmanager.action.UPLOAD_LIST"
  val ACTION_RETRY = "me.ctknight.uploadmanager.action.UPLOAD_RETRY"
  // redo is manually triggered by
  val ACTION_MANUAL_REDO = "me.ctknight.uploadmanager.action.UPLOAD_REDO"
  val ACTION_CANCEL = "me.ctknight.uploadmanager.action.UPLOAD_CANCEL"
  val ACTION_HIDE = "me.ctknight.uploadmanager.action.UPLOAD_HIDE"

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

  enum class UploadStatus private constructor(val status: Int) {
    PENDING(0),
    PAUSED(1),
    RUNNING(2),
    DELETED(3),

    WAITING_TO_RETRY(10),
    WAITING_FOR_NETWORK(11),
    WAITING_FOR_WIFI(12),

    SUCCESS(20),

    CAN_NOT_RESUME(40),
    CANCELED(41),
    // HTTP status code is not 2XX,
    HTTP_CODE_ERROR(42),
    HTTP_DATA_ERROR(43),
    /**
     * error when reading file
     */
    FILE_ERROR(44),
    FILE_NOT_FOUND(45),
    UNKNOWN_ERROR(50),

    FAILED(60);

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
  }

  enum class Visibility {
    VISIBLE,
    HIDDEN,
    VISIBLE_COMPLETE,
    HIDDEN_COMPLETE
  }

  enum class NetworkState {
    OK,
    CANNOT_USE_ROAMING,
    NO_CONNECTION
  }
}
