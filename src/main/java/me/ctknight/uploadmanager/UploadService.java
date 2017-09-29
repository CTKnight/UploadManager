/*
 * Copyright (c) 2016. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import android.util.Log;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import me.ctknight.uploadmanager.util.LogUtils;

import static android.text.format.DateUtils.MINUTE_IN_MILLIS;

public class UploadService extends Service {

    private static final int MSG_UPDATE = 1;
    private static final int MSG_FINAL_UPDATE = 2;
    private static final String TAG = LogUtils.makeTag(UploadService.class);
    // don't use LongSparseArray, it can't get keys' collection
    private final Map<Long, UploadInfo> mUploads = new HashMap<>();
    private AlarmManager mAlarmManager;
    private UploadManagerContentObserver mObserver;
    private UploadNotifier mNotifier;
    private ExecutorService mExecutor = buildUploadExecutor();
    private HandlerThread mUpdateThread;
    private Handler mUpdateHandler;
    private volatile int mLastStartId;

    private Handler.Callback mUpdateCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

            final int startId = msg.arg1;
//            Log.v(TAG, "Updating for startId " + startId);

            // Since database is current source of truth, our "active" status
            // depends on database state. We always get one final update pass
            // once the real actions have finished and persisted their state.

            final boolean isActive;
            synchronized (mUploads) {
                isActive = updateLocked();
            }

            if (msg.what == MSG_FINAL_UPDATE) {
                // Dump thread stacks belonging to pool
                for (Map.Entry<Thread, StackTraceElement[]> entry :
                        Thread.getAllStackTraces().entrySet()) {
                    if (entry.getKey().getName().startsWith("pool")) {
                        Log.d(TAG, entry.getKey() + ": " + Arrays.toString(entry.getValue()));
                    }
                }

                // Dump speed and update details
                mNotifier.dumpSpeeds();

                Log.wtf(TAG, "Final update pass triggered, isActive=" + isActive
                        + "; someone didn't update correctly.");
            }

            if (isActive) {
                // Still doing useful work, keep service alive. These active
                // tasks will trigger another update pass when they're finished.

                // Enqueue delayed update pass to catch finished operations that
                // didn't trigger an update pass; these are bugs.
                enqueueFinalUpdate();

            } else {
                // No active tasks, and any pending update messages can be
                // ignored, since any updates important enough to initiate tasks
                // will always be delivered with a new startId.

                if (stopSelfResult(startId)) {
//                    Log.v(TAG, "Nothing left; stopped");
                    getContentResolver().unregisterContentObserver(mObserver);
                    mUpdateThread.quit();
                }
            }

            return true;
        }
    };

    private static ExecutorService buildUploadExecutor() {
        // it's the up limit set by cluster notification
        final int maxConcurrent = 5;

        final ThreadPoolExecutor executor = new ThreadPoolExecutor(
                maxConcurrent, maxConcurrent, 10, TimeUnit.SECONDS,
                new LinkedBlockingDeque<Runnable>()) {
            @Override
            protected void afterExecute(Runnable r, Throwable t) {
                super.afterExecute(r, t);

                if ((t == null) && r instanceof Future<?>) {
                    try {
                        ((Future<?>) r).get();
                    } catch (CancellationException ce) {
                        t = ce;
                    } catch (ExecutionException ee) {
                        t = ee.getCause();
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }

                if (t != null) {
                    Log.w("UploadService", "Uncaught exception" + t);
                }
            }
        };
        executor.allowCoreThreadTimeOut(true);
        return executor;

    }

    /**
     * Binding to this service is not allowed
     */
    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v("UploadService", "created");

        mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        mUpdateThread = new HandlerThread("UploadService-UpdateThread");
        mUpdateThread.start();
        mUpdateHandler = new Handler(mUpdateThread.getLooper(), mUpdateCallback);

        mNotifier = new UploadNotifier(this);
        mNotifier.cancelAll();

        mObserver = new UploadManagerContentObserver();
        getContentResolver().registerContentObserver(UploadContract.UPLOAD_URIS.CONTENT_URI,
                true, mObserver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int returnValue = super.onStartCommand(intent, flags, startId);
        Log.v(TAG, "Service onStart");

        mLastStartId = startId;
        enqueueUpdate();
        return returnValue;
    }

    @Override
    public void onDestroy() {
        getContentResolver().unregisterContentObserver(mObserver);
        mUpdateThread.quit();
//        Log.v("UploadService", "Service onDestroy");
        super.onDestroy();
    }

    public void enqueueUpdate() {
        if (mUpdateHandler != null) {
            mUpdateHandler.removeMessages(MSG_UPDATE);
            mUpdateHandler.obtainMessage(MSG_UPDATE, mLastStartId, -1).sendToTarget();
        }
    }

    /**
     * Enqueue an {@link #updateLocked()} pass to occur after delay, usually to
     * catch any finished operations that didn't trigger an update pass.
     */
    private void enqueueFinalUpdate() {
        mUpdateHandler.removeMessages(MSG_FINAL_UPDATE);
        mUpdateHandler.sendMessageDelayed(
                mUpdateHandler.obtainMessage(MSG_FINAL_UPDATE, mLastStartId, -1),
                5 * MINUTE_IN_MILLIS);
    }


    private boolean updateLocked() {
        final long now = System.currentTimeMillis();

        boolean isActive = false;
        long nextActionMillis = Long.MAX_VALUE;

        final Set<Long> staleIds = new HashSet<>(mUploads.keySet());

        final ContentResolver resolver = getContentResolver();
        final Cursor cursor = resolver.query(UploadContract.UPLOAD_URIS.CONTENT_URI,
                null, UploadContract.UPLOAD_COLUMNS.COLUMN_VISIBILITY + " != " + UploadContract.VISIBILITY_STATUS.HIDDEN_COMPLETE,
                null, null);
        if (cursor == null) {
            return false;
        }
        try {
            UploadInfo.Reader reader = new UploadInfo.Reader(resolver, cursor);
            final int idColumn = cursor.getColumnIndexOrThrow(UploadContract.UPLOAD_COLUMNS._ID);
            while (cursor.moveToNext()) {
                final long id = cursor.getLong(idColumn);
                staleIds.remove(id);

                UploadInfo info = mUploads.get(id);

                if (info != null) {
                    updateUpload(reader, info);
                } else {
                    info = insertUploadLocked(reader);
                }

                if (info.mDeleted) {
                    // Delete download if requested, but only after cleaning up
                    resolver.delete(info.getUploadsUri(), null, null);
                } else {
                    final boolean activeUpload = info.startUploadIfReady(mExecutor);
                    isActive |= activeUpload;
                }

                if (info.mVisibility == UploadContract.VISIBILITY_STATUS.HIDDEN_COMPLETE) {
                    mUploads.remove(id);
                }

                nextActionMillis = Math.min(info.nextActionMillis(now), nextActionMillis);
            }
        } finally {
            cursor.close();
        }

        for (Long id : staleIds) {
            deleteUploadLocked(id);
        }

        mNotifier.updateWith(mUploads.values());

        if (nextActionMillis > 0 && nextActionMillis < Long.MAX_VALUE) {
            Log.v(TAG, "updateLocked: " + "scheduling start in " + nextActionMillis + "ms");


            final Intent intent = new Intent(UploadContract.ACTION_RETRY);
            intent.setClass(this, UploadReceiver.class);
            mAlarmManager.set(AlarmManager.RTC_WAKEUP, now + nextActionMillis,
                    PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_ONE_SHOT));

        }
        return isActive;
    }

    private void updateUpload(UploadInfo.Reader reader, UploadInfo info) {
        reader.updateFromDatabase(info);
//        Log.v("UploadService", info.mId + " status: " + info.mStatus);
    }

    private UploadInfo insertUploadLocked(UploadInfo.Reader reader) {
        final UploadInfo info = reader.newUploadInfo(this, mNotifier);
        if (info.mVisibility != UploadContract.VISIBILITY_STATUS.HIDDEN_COMPLETE) {
            mUploads.put(info.mId, info);
        }

        Log.v(TAG, "insertUploadLocked: " + "processing inserted upload " + info.mId);

        return info;
    }

    private void deleteUploadLocked(long id) {
        UploadInfo info = mUploads.get(id);
        if (info.mStatus == UploadContract.UPLOAD_STATUS.RUNNING) {
            info.mStatus = UploadContract.UPLOAD_STATUS.CANCELED;
        }
        mUploads.remove(info.mId);
    }

    private class UploadManagerContentObserver extends ContentObserver {
        public UploadManagerContentObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            enqueueUpdate();
        }
    }


}
