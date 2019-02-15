/*
 * Copyright (c) 2018. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager.util

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkUtils {

  private val DEFAULT_TIMEOUT = (20 * 1000L).toInt()
  private val DEFAULT_CLIENT = buildClient()
  @Volatile
  var customNetworkClient: OkHttpClient? = null
  var sNetworkClient: OkHttpClient
    get() {
      return customNetworkClient ?: DEFAULT_CLIENT
    }
    set(value) {
      customNetworkClient = value
    }

  // Connectivity information

  private fun buildClient(): OkHttpClient {
    val builder = OkHttpClient.Builder()
        .connectTimeout(DEFAULT_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(DEFAULT_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
    return builder.build()
  }
}
