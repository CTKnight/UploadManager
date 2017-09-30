/*
 * Copyright (c) 2016. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.support.v4.util.ArrayMap;
import android.text.TextUtils;
import android.util.Log;

import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import me.ctknight.uploadmanager.util.LogUtils;

import static me.ctknight.uploadmanager.util.NetworkUtils.isConnected;
import static me.ctknight.uploadmanager.util.NetworkUtils.isMobileNetwork;

public class UploadInfo {

    public static final String TAG = LogUtils.makeTag(UploadInfo.class);

    public static final String EXTRA_IS_WIFI_REQUIRED = "isWifiRequired";
    private final Context mContext;
    private final UploadNotifier mNotifier;
    //for building a intent.
    public long mId;
    public String mTargetUrl;
    public String mFileUri;
    public int mUid;
    public String mFileName;
    public int mStatus;
    public int mNumFailed;
    public int mRetryAfter;
    public String mMimeType;
    public long mTotalBytes;
    public long mCurrentBytes;
    public String mTitle;
    public String mDescription;
    public boolean mDeleted;
    public long mLastMod;
    public String mPackage;
    public String mClass;
    public String mExtras;
    public int mVisibility;
    public int mControl;
    public boolean mBypass;
    public String mServerResponse;
    public String mUserAgent;
    public String mReferer;
    public boolean mAllowRoaming;
    public String mDataFieldName;

    private Map<String, String> mRequestHeaders = new ArrayMap<>();
    private Map<String, String> mContentDisposition = new ArrayMap<>();

    private Future<?> mSubmittedTask;

    private UploadThread mTask;

    public UploadInfo(Context context, UploadNotifier notifier) {
        mContext = context.getApplicationContext();
        mNotifier = notifier;
    }

    /**
     * Query and return status of requested download.
     */
    public static int queryUploadStatus(ContentResolver resolver, long id) {
        final Cursor cursor = resolver.query(
                ContentUris.withAppendedId(UploadContract.UPLOAD_URIS.CONTENT_URI, id),
                new String[]{UploadContract.UPLOAD_COLUMNS.COLUMN_STATUS}, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            } else {
                // downloads; this is safe default for now.
                return UploadContract.UPLOAD_STATUS.PENDING;
            }
        } finally {
            cursor.close();
        }
    }

    public Map<String, String> getHeaders() {
        return mRequestHeaders;
    }

    public Map<String, String> getContentDisposition() {
        return mContentDisposition;
    }

    public String getUserAgent() {
        if (mUserAgent != null) {
            return mUserAgent;
        } else {
            return UploadContract.RequestContent.DEFAULT_USER_AGENT;
        }
    }

    public void sendIntentIfRequested() {
        if (mPackage == null) {
            return;
        }

        Intent intent;

        intent = new Intent(UploadManager.ACTION_UPLOAD_COMPLETE);
        intent.setPackage(mPackage);
        intent.putExtra(UploadManager.EXTRA_UPLOAD_ID, mId);
        mContext.sendBroadcast(intent);
    }

    void notifyQueryForNetwork(boolean isWifiRequired) {
        // TODO: 2016/1/26 send an Intent to broadcast receiver
    }

    public Uri getUploadsUri() {
        return ContentUris.withAppendedId(UploadContract.UPLOAD_URIS.CONTENT_URI, mId);
    }

    private boolean isReadyToUpload() {
        if (mControl == UploadContract.CONTROL.PAUSED) {
            return false;
        }
        switch (mStatus) {

            case UploadContract.UPLOAD_STATUS.PENDING://explicitly marked as ready to upload
            case UploadContract.UPLOAD_STATUS.RUNNING://upload was interrupted (proceess kill),
                //unable to update database
                return true;

            case UploadContract.UPLOAD_STATUS.WAITING_FOR_NETWORK:
                return isConnected(mContext);
            case UploadContract.UPLOAD_STATUS.WAITING_FOR_WIFI:
                return checkCanUseNetwork() == NetworkState.OK;

            case UploadContract.UPLOAD_STATUS.WAITING_TO_RETRY:
                final long now = System.currentTimeMillis();
                return restartTime(now) <= now;
            //in AOSP ,currentTimeMillis was adapted in SystemFacade
            case UploadContract.UPLOAD_STATUS.FILE_NOT_FOUND:
                return false;

        }

        return false;
    }

    public NetworkState checkCanUseNetwork() {
        ConnectivityManager connectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            Log.w(TAG, "checkCanUseNetwork: couldn't get connectivity manager");
            return null;
        }

        final NetworkInfo info = connectivityManager.getActiveNetworkInfo();

        boolean isMobile = (info != null && isMobileNetwork(info));

        if (info == null || !info.isConnected()) {
            return NetworkState.NO_CONNECTION;
        }
        if (NetworkInfo.DetailedState.BLOCKED.equals(info.getDetailedState())) {
            return NetworkState.BLOCKED;
        }
        if (isMobile && !mAllowRoaming) {
            return NetworkState.CANNOT_USE_ROAMING;
        }
        return NetworkState.OK;
    }

    public long restartTime(long now) {
        if (mNumFailed == 0) {
            return now;
        }
        if (mRetryAfter > 0) {
            return mLastMod + mRetryAfter;
        }
        Random random = new Random();
        //random for not blocking network
        int randomInt = random.nextInt(1001);
        return mLastMod + UploadContract.Constants.RETRY_FIRST_DELAY *
                (1000 + randomInt) * (1 << (mNumFailed - 1));
    }

    public long nextActionMillis(long now) {
        if (UploadContract.isComplete(mStatus)) {
            return Long.MAX_VALUE;
        }
        if (mStatus != UploadContract.UPLOAD_STATUS.WAITING_TO_RETRY) {
            return 0;
        }
        long when = restartTime(now);
        if (when <= now) {
            return 0;
        }
        return when - now;
    }

    public boolean startUploadIfReady(ExecutorService executorService) {
        synchronized (this) {
            final boolean isReady = isReadyToUpload();
            final boolean isActive = mSubmittedTask != null && !mSubmittedTask.isDone();
            if (isReady && !isActive) {
                if (mStatus != UploadContract.UPLOAD_STATUS.RUNNING) {
                    mStatus = UploadContract.UPLOAD_STATUS.RUNNING;
                    ContentValues values = new ContentValues();
                    values.put(UploadContract.UPLOAD_COLUMNS.COLUMN_STATUS, mStatus);
                    mContext.getContentResolver().update(getUploadsUri(), values, null, null);
                }

                mTask = new UploadThread(mContext, mNotifier, this);
                mSubmittedTask = executorService.submit(mTask);
            }
            return isReady;
        }
    }

    /**
     * Constants used to indicate network state for a specific download, after
     * applying any requested constraints.
     */
    public enum NetworkState {
        /**
         * The network is usable for the given download.
         */
        OK,
        /**
         * There is no network connectivity.
         */
        NO_CONNECTION,

        /**
         * Current network is blocked for requesting application.
         */
        BLOCKED,

        /**
         * The current connection is roaming, and the download can't proceed
         * over a roaming connection.
         */
        CANNOT_USE_ROAMING
    }

    public static class Reader {
        private ContentResolver mResolver;
        private Cursor mCursor;

        public Reader(ContentResolver resolver, Cursor cursor) {
            mResolver = resolver;
            mCursor = cursor;
        }

        /**
         * Normalize a MIME data type.
         *
         * <p>A normalized MIME type has white-space trimmed,
         * content-type parameters removed, and is lower-case.
         * This aligns the type with Android best practices for
         * intent filtering.
         *
         * <p>For example, "text/plain; charset=utf-8" becomes "text/plain".
         * "text/x-vCard" becomes "text/x-vcard".
         *
         * <p>All MIME types received from outside Android (such as user input,
         * or external sources like Bluetooth, NFC, or the Internet) should
         * be normalized before they are used to create an Intent.
         *
         * @param type MIME data type to normalize
         * @return normalized MIME data type, or null if the input was null
         */
        public static String normalizeMimeType(String type) {
            if (type == null) {
                return null;
            }

            type = type.trim().toLowerCase(Locale.ROOT);

            final int semicolonIndex = type.indexOf(';');
            if (semicolonIndex != -1) {
                type = type.substring(0, semicolonIndex);
            }
            return type;
        }

        public UploadInfo newUploadInfo(Context context, UploadNotifier notifier) {
            final UploadInfo info = new UploadInfo(context, notifier);
            updateFromDatabase(info);
            readRequestHeaders(info);
            readContentDisposition(info);
            return info;
        }

        public void updateFromDatabase(UploadInfo info) {
            info.mId = getLong(UploadContract.UPLOAD_COLUMNS._ID);
            info.mTargetUrl = getString(UploadContract.UPLOAD_COLUMNS.COLUMN_TARGET_URL);
            info.mFileUri = getString(UploadContract.UPLOAD_COLUMNS.COLUMN_FILE_URI);
            info.mUid = getInt(UploadContract.UPLOAD_COLUMNS.COLUMN_UID);
            info.mMimeType = normalizeMimeType(getString(UploadContract.UPLOAD_COLUMNS.COLUMN_MIME_TYPE));
            info.mVisibility = getInt(UploadContract.UPLOAD_COLUMNS.COLUMN_VISIBILITY);
            info.mStatus = getInt(UploadContract.UPLOAD_COLUMNS.COLUMN_STATUS);
            info.mNumFailed = getInt(UploadContract.UPLOAD_COLUMNS.COLUMN_NUM_FAILED);
            int retryRedirect = getInt(UploadContract.UPLOAD_COLUMNS.RETRY_AFTER_X_REDIRECT_COUNT);
            info.mRetryAfter = retryRedirect & 0xfffffff;
            info.mLastMod = getLong(UploadContract.UPLOAD_COLUMNS.COLUMN_LAST_MODIFICATION);
            info.mPackage = getString(UploadContract.UPLOAD_COLUMNS.COLUMN_NOTIFICATION_PACKAGE);
            info.mClass = getString(UploadContract.UPLOAD_COLUMNS.COLUMN_NOTIFICATION_CLASS);
            info.mExtras = getString(UploadContract.UPLOAD_COLUMNS.COLUMN_NOTIFICATION_EXTRAS);
            info.mTotalBytes = getLong(UploadContract.UPLOAD_COLUMNS.COLUMN_TOTAL_BYTES);
            info.mCurrentBytes = getLong(UploadContract.UPLOAD_COLUMNS.COLUMN_CURRENT_BYTES);
            info.mDeleted = getInt(UploadContract.UPLOAD_COLUMNS.COLUMN_DELETED) == 1;
            info.mTitle = getString(UploadContract.UPLOAD_COLUMNS.COLUMN_TITLE);
            info.mDescription = getString(UploadContract.UPLOAD_COLUMNS.COLUMN_DESCRIPTION);
            info.mControl = getInt(UploadContract.UPLOAD_COLUMNS.COLUMN_CONTROL);
            info.mBypass = getInt(UploadContract.UPLOAD_COLUMNS.COLUMN_BYPASS_NETWORK_CHANGE) == 1;
            info.mUserAgent = getString(UploadContract.RequestContent.COLUMN_USER_AGENT);
            info.mReferer = getString(UploadContract.RequestContent.COLUMN_REFERER);
            info.mAllowRoaming = getInt(UploadContract.UPLOAD_COLUMNS.COLUMN_ALLOW_ROAMING) != 0;
            info.mServerResponse = getString(UploadContract.UPLOAD_COLUMNS.COLUMN_SERVER_RESPONSE);
            synchronized (this) {
                info.mControl = getInt(UploadContract.UPLOAD_COLUMNS.COLUMN_CONTROL);
            }
        }

        private void readRequestHeaders(UploadInfo info) {
            info.mRequestHeaders.clear();
            Uri headerUri = Uri.withAppendedPath(
                    info.getUploadsUri(), UploadContract.RequestContent.URI_SEGMENT);
            Cursor cursor = mResolver.query(headerUri, null, null, null, null);
            try {
                int headerIndex =
                        cursor.getColumnIndexOrThrow(UploadContract.RequestContent.COLUMN_HEADER_NAME);
                int valueIndex =
                        cursor.getColumnIndexOrThrow(UploadContract.RequestContent.COLUMN_HEADER_VALUE);
                for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                    if ((!cursor.isNull(headerIndex)) && (!cursor.isNull(valueIndex))) {
                        addHeader(info, cursor.getString(headerIndex), cursor.getString(valueIndex));
                    }
                }
            } finally {
                cursor.close();
            }

            if (info.mReferer != null) {
                addHeader(info, "Referer", info.mReferer);
            }
        }

        private void readContentDisposition(UploadInfo info) {
            info.mContentDisposition.clear();
            Uri contentDispositionUri = Uri.withAppendedPath(
                    info.getUploadsUri(), UploadContract.RequestContent.CD_URI_SEGMENT);
            Cursor cursor = mResolver.query(contentDispositionUri, null, null, null, null);
            if (cursor == null) {
                return;
            }
            String contentValues = "";
            try {
                int headerIndex =
                        cursor.getColumnIndexOrThrow(UploadContract.RequestContent.COLUMN_CD_NAME);
                int valueIndex =
                        cursor.getColumnIndexOrThrow(UploadContract.RequestContent.COLUMN_CD_VALUE);
                if (cursor.moveToFirst()) {
                    contentValues = cursor.getString(valueIndex);
                }
                for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                    if ((!cursor.isNull(headerIndex) && (!cursor.isNull(valueIndex)))) {
                        addContentDisposition(info, cursor.getString(headerIndex), cursor.getString(valueIndex));
                    }
                }
            } finally {
                cursor.close();
            }
        }

        private void addHeader(UploadInfo info, String header, String value) {
            info.mRequestHeaders.put(header, value);
        }

        private void addContentDisposition(UploadInfo info, String header, String value) {
            info.mContentDisposition.put(header, value);
        }

        private String getString(String column) {
            int index = mCursor.getColumnIndexOrThrow(column);
            String s = mCursor.getString(index);
            return (TextUtils.isEmpty(s)) ? null : s;
        }

        private Integer getInt(String column) {
            return mCursor.getInt(mCursor.getColumnIndexOrThrow(column));
        }

        private Long getLong(String column) {
            return mCursor.getLong(mCursor.getColumnIndexOrThrow(column));
        }


    }


}
