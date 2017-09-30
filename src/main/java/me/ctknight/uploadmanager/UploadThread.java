/*
 * Copyright (c) 2016. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Debug;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.util.DebugUtils;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import me.ctknight.uploadmanager.util.LogUtils;
import me.ctknight.uploadmanager.util.okhttputil.CountingInputStreamMultipartBody;
import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.CANCELED;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.CANNOT_RESUME;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.HTTP_DATA_ERROR;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.RUNNING;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.SUCCESS;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.UNKNOWN_ERROR;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.WAITING_FOR_NETWORK;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.WAITING_TO_RETRY;


public class UploadThread implements Runnable, CountingInputStreamMultipartBody.ProgressListener {

    private static final int DEFAULT_TIMEOUT = (int) (20 * 1000L);

    private static final String TAG = LogUtils.makeTag(UploadThread.class);
    private static final OkHttpClient mClient = buildClient();
    private static final Object mMonitor = new Object();
    private final Context mContext;
    private final UploadNotifier mNotifier;
    private final long mId;
    private final UploadInfo mInfo;
    // global setting
    private final UploadInfoDelta mInfoDelta;
    private Call mCall;
    // upload has started or not
    private boolean mMadeProgress = false;
    private long mLastUpdateBytes = 0;
    private long mLastUpdateTime = 0;
    //TYPE_NONE
    private int mNetworkType = -1;
    private long mSpeed;
    private long mSpeedSampleStart;
    private long mSpeedSampleBytes;

    public UploadThread(Context context, UploadNotifier notifier, UploadInfo info) {
        mContext = context.getApplicationContext();
        mNotifier = notifier;

        mId = info.mId;
        mInfo = info;
        mInfoDelta = new UploadInfoDelta(info);
    }

    public static boolean isStatusRetryable(int status) {
        switch (status) {
            case HTTP_DATA_ERROR:
            case HTTP_UNAVAILABLE:
            case HTTP_INTERNAL_ERROR:
            case RUNNING:
                return true;
        }
        return false;
    }

    @NonNull
    private static OkHttpClient buildClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS);
        return builder.build();
    }

    @Override
    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        final ConnectivityManager connectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (UploadInfo.queryUploadStatus(mContext.getContentResolver(), mId) == SUCCESS) {
            Log.d("UploadThread", "run: " + "skipping finished item id: " + mId);
            return;
        }

        PowerManager.WakeLock wakelock = null;
        final PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);

        try {
            wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UploadThread" + mId);
            wakelock.acquire();

//            Log.d("UploadThread", "run: starting");

            final NetworkInfo info = connectivityManager.getActiveNetworkInfo();
            if (info != null) {
                mNetworkType = info.getType();
            }
            if (!checkDeletedOrCanceled() || isStatusRetryable(mInfoDelta.mStatus)) {
                executeUpload();
            }
            mInfoDelta.mStatus = SUCCESS;

            if (mInfoDelta.mTotalBytes == -1) {
                mInfoDelta.mTotalBytes = mInfoDelta.mCurrentBytes;
            }

        } catch (FileNotFoundException e) {
            mInfoDelta.mErrorMsg = e.getMessage();
            Log.e(TAG, "executeUpload: ", e);
            mInfoDelta.mStatus = UploadContract.UPLOAD_STATUS.FILE_NOT_FOUND;

        } catch (UploadException e) {
            mInfoDelta.mErrorMsg = e.getMessage();

            Log.w(TAG, "run: " + "Stop uploading with "
                    + mInfoDelta.mStatus
                    + mInfoDelta.mErrorMsg);

            //only we can request retry
            if (mInfoDelta.mStatus == WAITING_TO_RETRY) {
                throw new IllegalStateException("Execution should always throw final error codes");
            }

            if (isStatusRetryable(mInfoDelta.mStatus)) {
                if (mMadeProgress) {
                    mInfoDelta.mNumFailed = 1;
                } else {
                    mInfoDelta.mNumFailed += 1;
                }

                if (mInfoDelta.mNumFailed < UploadContract.Constants.MAX_RETRIES) {
                    final NetworkInfo info = connectivityManager.getActiveNetworkInfo();
                    if (info != null && info.getType() == mNetworkType && info.isConnected()) {
                        // Underlying network is still intact, use normal backoff
                        mInfoDelta.mStatus = WAITING_TO_RETRY;
                    } else {
                        // Network changed, retry on any next available
                        mInfoDelta.mStatus = WAITING_FOR_NETWORK;
                    }

                    //we can't resume uploading, so checking ETag is unnecessary.
                } else {
                    mInfoDelta.mStatus = CANNOT_RESUME;
                    mInfoDelta.writeToDatabase();
                }
            } else {
                mInfoDelta.mStatus = CANNOT_RESUME;
                mInfoDelta.writeToDatabase();
            }

        } catch (Throwable t) {
            mInfoDelta.mStatus = UNKNOWN_ERROR;
            mInfoDelta.mErrorMsg = t.toString();

            Log.e(TAG, "Failed: " + mInfoDelta.mErrorMsg, t);
        } finally {
//            Log.d(TAG, "run: Finish with status" + mInfoDelta.mStatus);

            mNotifier.notifyUploadSpeed(mId, 0);


            if (UploadContract.isComplete(mInfoDelta.mStatus)) {
                if (mInfoDelta.mVisibility == UploadContract.VISIBILITY_STATUS.VISIBLE) {
                    mInfoDelta.mVisibility = UploadContract.VISIBILITY_STATUS.VISIBLE_COMPLETE;
                }
                mInfo.sendIntentIfRequested();
            }

            mInfoDelta.writeToDatabase();

            if (wakelock != null) {
                wakelock.release();
                wakelock = null;
            }
        }
    }

    @Override
    public void transferred(long num) {
        mMadeProgress = true;
        mInfoDelta.mCurrentBytes = num;
        try {
            updateProgress();
            if (checkDeletedOrCanceled()) {
                throw new UploadCancelException("Upload canceled");
            }

        } catch (IOException | UploadException e) {
            mCall.cancel();

            Log.e(TAG, "transferred: ", e);
        }

    }

    private void executeUpload() throws UploadException, IOException {

        URL url;

        try {
            url = new URL(mInfoDelta.mTargetUrl);
        } catch (MalformedURLException e) {
            throw new UploadException("Invalid URL");
        }

        try {
            checkConnectivity();
            uploadData(url);
        } catch (Exception e) {
            if (e instanceof FileNotFoundException) {
                throw e;
            } else {
                throw new UploadNetworkException(e);
            }
        }
    }

    private void checkConnectivity() throws UploadException {

        final UploadInfo.NetworkState networkState = mInfo.checkCanUseNetwork();
        if (networkState != UploadInfo.NetworkState.OK) {
            int status = UploadContract.UPLOAD_STATUS.WAITING_FOR_NETWORK;
            if (networkState == UploadInfo.NetworkState.CANNOT_USE_ROAMING) {
                status = UploadContract.UPLOAD_STATUS.WAITING_FOR_WIFI;
                mInfo.notifyQueryForNetwork(true);
            } else if (networkState == UploadInfo.NetworkState.NO_CONNECTION) {
                status = UploadContract.UPLOAD_STATUS.WAITING_FOR_WIFI;
                mInfo.notifyQueryForNetwork(false);
            }
            throw new UploadException(status + networkState.name());
        }
    }

    private InputStream getFileInputStream() throws IOException {
        Uri fileUri = Uri.parse(mInfo.mFileUri);
        final String scheme = fileUri.getScheme();
        try {
            switch (scheme) {
                case ContentResolver.SCHEME_CONTENT:
                    return mContext.getContentResolver().openInputStream(fileUri);
                case ContentResolver.SCHEME_FILE:
                    String path = fileUri.getPath();
                    return new FileInputStream(new File(path));
                default:
                    throw new IOException("Unsupported Uri" + mInfo.mFileUri);
            }
        } catch (IOException e) {
            Log.e("UploadThread", "getFileInputStream: ", e);
            throw e;
        }
    }

    private RequestBody buildRequestBody() throws IOException {
        CountingInputStreamMultipartBody.Builder builder = new CountingInputStreamMultipartBody.Builder()
                .setType(CountingInputStreamMultipartBody.FORM)
                .addFormDataPart("file", mInfo.mFileName,
                        CountingInputStreamMultipartBody.create(MediaType.parse(mInfo.mMimeType), getFileInputStream()));
        for (Map.Entry<String, String> cd : mInfo.getContentDisposition().entrySet()) {
            builder.addFormDataPart(cd.getKey(), cd.getValue());
        }
        RequestBody body = builder.setProgressListener(this).build();
        try {
            setTotalBytes(body.contentLength());
        } catch (IOException e) {
            Log.e(TAG, "buildRequestBody: ", e);
            setTotalBytes(-1);
        }
        return body;
    }

    private Request buildRequest(URL url) throws IOException {
        return new Request.Builder()
                .headers(Headers.of(mInfo.getHeaders()))
                .url(url)
                .post(buildRequestBody())
                .build();
    }

    private void uploadData(URL url) throws IOException {
        synchronized (mMonitor) {
            mCall = mClient.newCall(buildRequest(url));
        }
        Response response = mCall.execute();
        String responseMsg = response.body().string();
        recordResponse(responseMsg);
    }

    public void setTotalBytes(long totalBytes) {
        mInfoDelta.mTotalBytes = totalBytes;
        mInfoDelta.writeToDatabase();
    }

    private void recordResponse(String responseMsg) {
        Log.d(TAG, "executeUpload: " + responseMsg);
        mInfoDelta.mServerResponse = responseMsg;
        mInfoDelta.writeToDatabase();
    }

    private void updateProgress() throws IOException, UploadException {
        final long now = SystemClock.elapsedRealtime();
        final long currentBytes = mInfoDelta.mCurrentBytes;

        final long sampleDelta = now - mSpeedSampleStart;
        if (sampleDelta > 500) {
            final long sampleSpeed = ((currentBytes - mSpeedSampleBytes) * 1000) / sampleDelta;

            if (mSpeed == 0) {
                mSpeed = sampleSpeed;
            } else {
                mSpeed = ((mSpeed * 3) + sampleSpeed) / 4;
                //From AOSP, but why??
            }

            //only notify after a full sample window time (sampleDelta)
            if (mSpeedSampleStart != 0) {
                mNotifier.notifyUploadSpeed(mId, mSpeed);
            }
            mSpeedSampleStart = now;
            mSpeedSampleBytes = currentBytes;
        }

        final long bytesDelta = currentBytes - mLastUpdateBytes;
        final long timeDelta = now - mLastUpdateTime;

        if (bytesDelta > UploadContract.Constants.MIN_PROGRESS_STEP && timeDelta > UploadContract.Constants.MIN_PROGRESS_TIME) {
            mInfoDelta.writeToDatabaseOrThrow();
            mLastUpdateBytes = currentBytes;
            mLastUpdateTime = now;
        }

    }

    private boolean checkDeletedOrCanceled() throws UploadException {
        synchronized (mInfo) {
            return mInfo.mStatus == CANCELED || mInfo.mDeleted;
        }
    }

    private class UploadInfoDelta {
        public String mTargetUrl;
        public String mMimeType;
        public int mStatus;
        public int mNumFailed;
        public int mRetryAfter;
        public long mTotalBytes;
        public long mCurrentBytes;
        public int mVisibility;

        public String mErrorMsg;
        public String mServerResponse;

        public UploadInfoDelta(UploadInfo info) {
            mTargetUrl = info.mTargetUrl;
            mMimeType = info.mMimeType;
            mStatus = info.mStatus;
            mNumFailed = info.mNumFailed;
            mRetryAfter = info.mRetryAfter;
            mTotalBytes = info.mTotalBytes;
            mCurrentBytes = info.mCurrentBytes;
            mServerResponse = info.mServerResponse;
            mVisibility = info.mVisibility;
        }

        private ContentValues buildContentValues() {
            final ContentValues values = new ContentValues();

            values.put(UploadContract.UPLOAD_COLUMNS.COLUMN_TARGET_URL, mTargetUrl);
            values.put(UploadContract.UPLOAD_COLUMNS.COLUMN_MIME_TYPE, mMimeType);
            values.put(UploadContract.UPLOAD_COLUMNS.COLUMN_STATUS, mStatus);
            values.put(UploadContract.UPLOAD_COLUMNS.COLUMN_NUM_FAILED, mNumFailed);
            values.put(UploadContract.UPLOAD_COLUMNS.COLUMN_RETRY_AFTER, mRetryAfter);
            values.put(UploadContract.UPLOAD_COLUMNS.COLUMN_TOTAL_BYTES, mTotalBytes);
            values.put(UploadContract.UPLOAD_COLUMNS.COLUMN_CURRENT_BYTES, mCurrentBytes);
            values.put(UploadContract.UPLOAD_COLUMNS.COLUMN_LAST_MODIFICATION, System.currentTimeMillis());
            values.put(UploadContract.UPLOAD_COLUMNS.COLUMN_ERROR_MSG, mErrorMsg);
            values.put(UploadContract.UPLOAD_COLUMNS.COLUMN_SERVER_RESPONSE, mServerResponse);
            values.put(UploadContract.UPLOAD_COLUMNS.COLUMN_VISIBILITY, mVisibility);
            return values;
        }

        /**
         * Blindly push update of current delta values to provider.
         */
        public void writeToDatabase() {
            mContext.getContentResolver().update(mInfo.getUploadsUri(), buildContentValues(),
                    null, null);
        }

        /**
         * Push update of current delta values to provider, asserting strongly
         * that we haven't been paused or deleted.
         */
        public void writeToDatabaseOrThrow() throws UploadException {
            if (mContext.getContentResolver().update(mInfo.getUploadsUri(),
                    buildContentValues(), UploadContract.UPLOAD_COLUMNS.COLUMN_DELETED + " == '0'", null) == 0) {
                throw new UploadException(Integer.toString(CANCELED) + "Upload deleted or missing!");
            }
        }
    }
}
