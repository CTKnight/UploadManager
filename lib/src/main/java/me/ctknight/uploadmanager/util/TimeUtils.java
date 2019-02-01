/*
 * Copyright (c) 2018. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager.util;

import android.content.res.Resources;

import me.ctknight.uploadmanager.R;

public class TimeUtils {

  //return a human readable time
  public static CharSequence formatDuration(long millis, Resources res) {
    final long SECOND_IN_MILLIS = 1000;
    final long MINUTE_IN_MILLIS = SECOND_IN_MILLIS * 60;
    final long HOUR_IN_MILLIS = MINUTE_IN_MILLIS * 60;
    final long DAY_IN_MILLIS = HOUR_IN_MILLIS * 24;

    if (millis >= HOUR_IN_MILLIS) {
      final int hours = (int) ((millis + 1800000) / HOUR_IN_MILLIS);
      return res.getQuantityString(
          R.plurals.duration_hours, hours, hours);
    } else if (millis >= MINUTE_IN_MILLIS) {
      final int minutes = (int) ((millis + 30000) / MINUTE_IN_MILLIS);
      return res.getQuantityString(
          R.plurals.duration_minutes, minutes, minutes);
    } else {
      final int seconds = (int) ((millis + 500) / SECOND_IN_MILLIS);
      return res.getQuantityString(
          R.plurals.duration_seconds, seconds, seconds);
    }
  }

}
