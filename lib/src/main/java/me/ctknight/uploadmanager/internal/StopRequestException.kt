/*
 * Copyright (c) 2019. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager.internal

import me.ctknight.uploadmanager.UploadContract

/**
 * Raised to indicate that the current request should be stopped immediately.
 *
 * Note the message passed to this exception will be logged and therefore must be guaranteed
 * not to contain any PII, meaning it generally can't include any information about the request
 * URI, headers, or destination filename.
 */
internal class StopRequestException(val finalStatus: UploadContract.UploadStatus, message: String?) : Exception(message) {

  constructor(finalStatus: UploadContract.UploadStatus, t: Throwable) : this(finalStatus, t.message) {
    initCause(t)
  }

  constructor(finalStatus: UploadContract.UploadStatus, message: String?, t: Throwable) : this(finalStatus, message) {
    initCause(t)
  }

  companion object {
    @Throws(StopRequestException::class)
    fun throwUnhandledHttpError(code: Int, message: String): StopRequestException {
      val error = "Unhandled HTTP response: $code $message"
      when (code) {
        in 400..599 -> throw StopRequestException(UploadContract.UploadStatus.HTTP_CODE_ERROR, error)
        in 300..399 -> throw StopRequestException(UploadContract.UploadStatus.HTTP_REDIRECT_ERROR, error)
        else -> throw StopRequestException(UploadContract.UploadStatus.HTTP_CODE_ERROR, error)
      }
    }
  }
}