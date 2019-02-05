/*
 * Copyright (c) 2018. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager

open class UploadException : Exception {

  internal constructor()

  internal constructor(detailMessage: String) : super(detailMessage)

  internal constructor(detailMessage: String, throwable: Throwable) : super(detailMessage, throwable)

  internal constructor(throwable: Throwable) : super(throwable)
}
