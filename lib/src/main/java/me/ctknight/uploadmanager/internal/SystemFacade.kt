/*
 * Copyright (c) 2019. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager.internal

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import androidx.core.content.getSystemService
import me.ctknight.uploadmanager.thirdparty.SingletonHolder

internal interface SystemFacade {
  /**
   * @see System.currentTimeMillis
   */
  fun currentTimeMillis(): Long

  fun getActiveNetworkInfo(): NetworkInfo?

  /**
   * @return maximum size, in bytes, of downloads that may go over a mobile connection; or null if
   * there's no limit
   */
  fun getMaxBytesOverMobile(): Long

  /**
   * @return recommended maximum size, in bytes, of downloads that may go over a mobile
   * connection; or null if there's no recommended limit.  The user will have the option to bypass
   * this limit.
   */
  fun getRecommendedMaxBytesOverMobile(): Long

  /**
   * Send a broadcast intent.
   */
  fun sendBroadcast(intent: Intent)

  class Impl(private val context: Context) : SystemFacade {
    private val mConnectivityManager: ConnectivityManager = context.getSystemService()!!
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
    fun isActiveNetworkMetered(): Boolean {
      return mConnectivityManager.isActiveNetworkMetered()
    }
    override fun getActiveNetworkInfo(): NetworkInfo? =
        mConnectivityManager.activeNetworkInfo

    // TODO: use configurations in UploadManager
    override fun getMaxBytesOverMobile(): Long = Long.MAX_VALUE

    // TODO: use configurations in UploadManager
    override fun getRecommendedMaxBytesOverMobile(): Long = Long.MAX_VALUE

    override fun sendBroadcast(intent: Intent) = context.sendBroadcast(intent)


    companion object {
      private object InstanceHolder : SingletonHolder<Impl, Context>(::Impl)

      internal val getInstance = InstanceHolder::getInstance
    }
  }
}