/*
 * Copyright (c) 2018. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */
@file:JvmName("ToastUtils")

package me.ctknight.uploadmanager.util

import android.content.Context
import android.widget.Toast

object ToastUtils {
  fun show(
    text: CharSequence,
    duration: Int,
    context: Context
  ) {
    Toast.makeText(context, text, duration)
        .show()
  }

  fun show(
    resId: Int,
    duration: Int,
    context: Context
  ) {
    show(context.getText(resId), duration, context)
  }

  fun show(
    text: CharSequence,
    context: Context
  ) {
    show(text, Toast.LENGTH_SHORT, context)
  }

  fun show(
    resId: Int,
    context: Context
  ) {
    show(context.getText(resId), context)
  }
}

