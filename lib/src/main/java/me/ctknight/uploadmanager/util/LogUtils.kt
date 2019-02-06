/*
 * Copyright (c) 2018. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager.util

internal object LogUtils {
  internal inline fun <reified T> makeTag(): String {
    return T::class.java.simpleName
  }
}
