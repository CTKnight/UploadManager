/*
 * Copyright (c) 2018. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */
@file:JvmName("TimeUtils")

package me.ctknight.uploadmanager.util

import android.content.res.Resources

import me.ctknight.uploadmanager.R

//return a human readable time
fun Long.formatDuration(res: Resources): CharSequence {
  val millis = this
  val SECOND_IN_MILLIS: Long = 1000
  val MINUTE_IN_MILLIS = SECOND_IN_MILLIS * 60
  val HOUR_IN_MILLIS = MINUTE_IN_MILLIS * 60
  val DAY_IN_MILLIS = HOUR_IN_MILLIS * 24

  return when {
    millis >= HOUR_IN_MILLIS -> {
      val hours = ((millis + 1800000) / HOUR_IN_MILLIS).toInt()
      res.getQuantityString(
          R.plurals.duration_hours, hours, hours
      )
    }
    millis >= MINUTE_IN_MILLIS -> {
      val minutes = ((millis + 30000) / MINUTE_IN_MILLIS).toInt()
      res.getQuantityString(
          R.plurals.duration_minutes, minutes, minutes
      )
    }
    else -> {
      val seconds = ((millis + 500) / SECOND_IN_MILLIS).toInt()
      res.getQuantityString(
          R.plurals.duration_seconds, seconds, seconds
      )
    }
  }
}

