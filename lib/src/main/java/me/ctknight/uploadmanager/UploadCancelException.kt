/*
 * Copyright (c) 2018. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager

/**
 * Created by laikitman on 7/6/17.
 */

class UploadCancelException : UploadException {
  internal constructor() : super()

  internal constructor(detailMessage: String) : super(detailMessage)

  internal constructor(detailMessage: String, throwable: Throwable) : super(detailMessage, throwable)

  internal constructor(throwable: Throwable) : super(throwable)
}
