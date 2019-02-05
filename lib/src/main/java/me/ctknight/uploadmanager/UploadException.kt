/*
 * Copyright (c) 2018. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager

open class UploadException : Exception {

  internal constructor() {}

  constructor(detailMessage: String) : super(detailMessage) {}

  constructor(detailMessage: String, throwable: Throwable) : super(detailMessage, throwable) {}

  constructor(throwable: Throwable) : super(throwable) {}
}
