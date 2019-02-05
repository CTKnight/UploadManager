/*
 * Copyright (c) 2018. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import com.facebook.stetho.okhttp3.StethoInterceptor;

import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import me.ctknight.uploadmanager.BuildConfig;
import okhttp3.OkHttpClient;

public class NetworkUtils {

  private static final int TIMEOUT_MILLISECOND = 15000;
  private static final int DEFAULT_TIMEOUT = (int) (20 * 1000L);
  public static final OkHttpClient sNetworkClient = buildClient();
  private static final String CHARSET_NAME = "UTF-8";

  private NetworkUtils() {
  }

  // Connectivity information

  @NonNull
  private static OkHttpClient buildClient() {
    OkHttpClient.Builder builder = new OkHttpClient.Builder()
        .connectTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
        .readTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS);
    if (BuildConfig.DEBUG) {
      builder.addNetworkInterceptor(new StethoInterceptor());
    }
    return builder.build();
  }

  // NOTICE: the return value may be null.
  public static NetworkInfo getActiveNetworkInfo(Context context) {
    ConnectivityManager connectivityManager =
        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    return connectivityManager.getActiveNetworkInfo();
  }

  public static boolean isConnected(Context context) {
    return isConnected(getActiveNetworkInfo(context));
  }

  public static boolean isConnected(NetworkInfo networkInfo) {
    return networkInfo != null && networkInfo.isConnected();
  }

  // NOTICE: Not checking for null.
  // NOTE: From ConnectivityManager.isNetworkTypeMobile(int) (Hidden)
  public static boolean isMobileNetwork(NetworkInfo networkInfo) {
    switch (networkInfo.getType()) {
      case ConnectivityManager.TYPE_MOBILE:
      case ConnectivityManager.TYPE_MOBILE_MMS:
      case ConnectivityManager.TYPE_MOBILE_SUPL:
      case ConnectivityManager.TYPE_MOBILE_DUN:
      case ConnectivityManager.TYPE_MOBILE_HIPRI:
        return true;
      default:
        return false;
    }
  }

  public static boolean isNonMobileConnected(Context context) {
    return isNonMobileConnected(getActiveNetworkInfo(context));
  }

  public static boolean isNonMobileConnected(NetworkInfo networkInfo) {
    return isConnected(networkInfo) && !isMobileNetwork(networkInfo);
  }

  public static WifiInfo getWifiInfo(Context context) {
    WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    return wifiManager.getConnectionInfo();
  }
}
