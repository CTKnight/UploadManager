/*
 * Copyright (c) 2018. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import me.ctknight.uploadmanager.UploadNetworkException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NetworkUtils {

    private static final int TIMEOUT_MILLISECOND = 15000;

    private static final String CHARSET_NAME = "UTF-8";

    private NetworkUtils() {
    }

    // Connectivity information

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

    public static String get(String urlString) throws UploadNetworkException {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_MILLISECOND, TimeUnit.MILLISECONDS)
                .readTimeout(TIMEOUT_MILLISECOND, TimeUnit.MILLISECONDS)
                .build();
        try {
            Request request = new Request.Builder().get().url(urlString).build();
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                response.body().close();
                throw new UploadNetworkException("Bad HTTP status code" + response.code());
            }
            return response.body().string();
        } catch (IOException e) {
            throw new UploadNetworkException(e);
        }
    }

    public static String get(String urlString, boolean useCaches) throws UploadNetworkException {

        URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            throw new UploadNetworkException(e);
        }

        HttpURLConnection connection = null;
        InputStream inputStream = null;

        try {

            // NOTE: The connection type is specified in the protocol part of the url.
            connection = (HttpURLConnection) url.openConnection();

            connection.setConnectTimeout(TIMEOUT_MILLISECOND);
            connection.setDoInput(true);
            connection.setReadTimeout(TIMEOUT_MILLISECOND);
            connection.setRequestMethod("GET");
            // NOTE: gzip compression is enabled by default in the Android SDK implementation of
            // HTTPURLConnection.
            connection.setUseCaches(useCaches);

            // Check for the only correct response code 200 according to the docs.
            // NOTE: This will do the connection.
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new UploadNetworkException("Bad Http response code: " + responseCode);
            }

            inputStream = connection.getInputStream();
            return IoUtils.inputStreamToString(inputStream, CHARSET_NAME);

        } catch (IOException e) {

            throw new UploadNetworkException(e);

        } finally {

            // We need this because sometimes StrictMode reports that InputStream is not closed.
            // The root cause is hard to investigate, while closing it here seems harmless.
            if (inputStream != null) {
                IoUtils.close(inputStream);
            }

            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
