/*
 * Copyright (c) 2016. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.support.v4.util.LongSparseArray;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import me.ctknight.uploadmanager.util.TimeUtils;

public class UploadNotifier {

    public static final String NOTIFICATION_CHANNEL = "Upload notification";

    private static final int TYPE_ACTIVE = 1;
    private static final int TYPE_WAITING = 2;
    private static final int TYPE_COMPLETE = 3;
    private static final int TYPE_FAILED = 4;


    private static Context mContext = null;
    //AOSP use final ,but I have to get current package name in a static method,so change it to static.
    private final NotificationManager mNotifManager;

    private final HashMap<String, Long> mActiveNotif = new HashMap<>();

    private final LongSparseArray<Long> mUploadSpeed = new LongSparseArray<>();

    private final LongSparseArray<Long> mUploadTouch = new LongSparseArray<>();
    //In AOSP Download Notifier,they use LongSparseLongArray,
    //actually,LongSparseArray is a generic version of LongSparseLongArray,
    //so LongSparseArray<Long> is totally same with LongSparseLongArray.


    public UploadNotifier(Context context) {
        mContext = context;
        mNotifManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mNotifManager != null) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL,
                    mContext.getString(R.string.notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT);
            mNotifManager.createNotificationChannel(channel);
        }
    }

    private static CharSequence getUploadTitle(Resources res, UploadInfo info) {
        if (!TextUtils.isEmpty(info.mTitle)) {
            return info.mTitle;
        } else {
            return res.getString(R.string.upload_unknown_upload_title);
        }
    }

    private static String buildNotificationTag(UploadInfo info) {
        // waiting and active is clustered
        if (UploadContract.isWaiting(info)) {
            return TYPE_WAITING + ":" + mContext.getPackageName();
        } else if (UploadContract.isOnGoing(info)) {
            return TYPE_ACTIVE + ":" + mContext.getPackageName();
        } else if (UploadContract.isFailed(info)) {
            return TYPE_FAILED + ":" + info.mId;
        } else if (UploadContract.isComplete(info)) {
            return TYPE_COMPLETE + ":" + info.mId;
        } else {
            return null;
        }
    }

    private static int getNotificationTagType(String tag) {
        return Integer.parseInt(tag.substring(0, tag.indexOf(':')));
    }

    public void cancelAll() {
        mNotifManager.cancelAll();
    }

    public void notifyUploadSpeed(long id, long bytesPerSecond) {
        synchronized (mUploadSpeed) {
            if (bytesPerSecond != 0) {
                mUploadSpeed.put(id, bytesPerSecond);
                mUploadTouch.put(id, SystemClock.elapsedRealtime());
            } else {
                mUploadSpeed.delete(id);
                mUploadTouch.delete(id);
            }
        }
    }

    public void updateWith(Collection<UploadInfo> uploads) {
        synchronized (mActiveNotif) {
            updateWithLocked(uploads);
        }
    }

    public void updateWithLocked(Collection<UploadInfo> uploads) {
        final Resources res = mContext.getResources();

        // Cluster uploads together
        final Map<String, ArrayList<UploadInfo>> clustered = new HashMap<>();
//        final Multimap<String, UploadInfo> clustered = ArrayListMultimap.create();
        for (UploadInfo info : uploads) {
            final String tag = buildNotificationTag(info);
            if (tag != null) {
                if (clustered.containsKey(tag)) {
                    clustered.get(tag).add(info);
                } else {
                    //NOTE I know this is dirty, but I just don't want to get guava involved.
                    clustered.put(tag, new ArrayList<>(Arrays.asList(info)));
                }
            }
        }

        //Build notification for each cluster
        for (String tag : clustered.keySet()) {
            final int type = getNotificationTagType(tag);
            final Collection<UploadInfo> cluster = clustered.get(tag);

            final NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext, NOTIFICATION_CHANNEL);
            builder.setColor(res.getColor(R.color.system_notification_accent_color));

            // Use time when cluster was first shown to avoid shuffling
            final long firstShown;
            if (mActiveNotif.containsKey(tag)) {
                firstShown = mActiveNotif.get(tag);
            } else {
                firstShown = System.currentTimeMillis();
                mActiveNotif.put(tag, firstShown);
            }
            builder.setWhen(firstShown);

            // Show relevant icon
            if (type == TYPE_ACTIVE) {
                builder.setSmallIcon(android.R.drawable.stat_sys_upload);
            } else if (type == TYPE_WAITING) {
                builder.setSmallIcon(android.R.drawable.stat_sys_warning);
            } else if (type == TYPE_COMPLETE) {
                builder.setSmallIcon(android.R.drawable.stat_sys_upload_done);
            } else if (type == TYPE_FAILED) {
                builder.setSmallIcon(android.R.drawable.stat_sys_warning);
            }

            // Build action intents
            // add action by type
            // active -> cancel
            // fail waiting -> retry
            if (type == TYPE_ACTIVE || type == TYPE_WAITING) {
                final Uri uri = new Uri.Builder().scheme("active-dl").appendPath(tag).build();
                final Intent intent = new Intent(UploadContract.ACTION_LIST,
                        uri, mContext, UploadReceiver.class);
                intent.putExtra(UploadManager.EXTRA_NOTIFICATION_CLICK_UPLOAD_IDS,
                        getUploadIds(cluster));
                builder.setContentIntent(PendingIntent.getBroadcast(mContext,
                        9, intent, PendingIntent.FLAG_UPDATE_CURRENT));
                builder.setOngoing(true);

                final UploadInfo info = cluster.iterator().next();
                if (type == TYPE_ACTIVE) {
                    final Uri idUri = ContentUris.withAppendedId(UploadContract.UPLOAD_URIS.CONTENT_URI, info.mId);
                    final Intent actionIntent = new Intent(UploadContract.ACTION_CANCEL,
                            idUri, mContext, UploadReceiver.class);
                    builder.addAction(R.drawable.ic_clear_black_24dp,
                            mContext.getString(R.string.notification_action_cancel),
                            PendingIntent.getBroadcast(mContext, 0, actionIntent, 0));
                } else {
                    // WAITING
                    final Uri idUri = ContentUris.withAppendedId(UploadContract.UPLOAD_URIS.CONTENT_URI, info.mId);
                    final Intent actionIntent = new Intent(UploadContract.ACTION_MANUAL_REDO,
                            idUri, mContext, UploadReceiver.class);
                    builder.addAction(R.drawable.ic_redo_black_24dp,
                            mContext.getString(R.string.notification_action_redo),
                            PendingIntent.getBroadcast(mContext, 0, actionIntent, 0));
                }

            } else if (type == TYPE_COMPLETE) {
                final UploadInfo info = cluster.iterator().next();
                final Uri uri = ContentUris.withAppendedId(UploadContract.UPLOAD_URIS.CONTENT_URI, info.mId);
                builder.setAutoCancel(true);
                //In AOSP, action is set according to its status
                //but we just need to open the upload list.

                final String action;
                if (UploadContract.isStatusError(info.mStatus)) {
                    action = UploadContract.ACTION_LIST;
                } else {
                    action = UploadContract.ACTION_OPEN;
                }
                final Intent intent = new Intent(action, uri, mContext, UploadReceiver.class);
                intent.putExtra(UploadManager.EXTRA_NOTIFICATION_CLICK_UPLOAD_IDS,
                        getUploadIds(cluster));
                builder.setContentIntent(PendingIntent.getBroadcast(mContext,
                        0, intent, PendingIntent.FLAG_UPDATE_CURRENT));

                final Intent hideIntent = new Intent(UploadContract.ACTION_HIDE,
                        uri, mContext, UploadReceiver.class);
                builder.setDeleteIntent(PendingIntent.getBroadcast(mContext, 0, hideIntent, 0));

            } else if (type == TYPE_FAILED) {
                final UploadInfo info = cluster.iterator().next();
                final Uri uri = ContentUris.withAppendedId(UploadContract.UPLOAD_URIS.CONTENT_URI, info.mId);
                builder.setAutoCancel(true);

                final String action = UploadContract.ACTION_LIST;

                final Intent intent = new Intent(action, uri, mContext, UploadReceiver.class);
                intent.putExtra(UploadManager.EXTRA_NOTIFICATION_CLICK_UPLOAD_IDS,
                        getUploadIds(cluster));
                builder.setContentIntent(PendingIntent.getBroadcast(mContext,
                        0, intent, PendingIntent.FLAG_UPDATE_CURRENT));

                final Intent hideIntent = new Intent(UploadContract.ACTION_HIDE,
                        uri, mContext, UploadReceiver.class);
                builder.setDeleteIntent(PendingIntent.getBroadcast(mContext, 0, hideIntent, 0));
            }

            //Calculate and show progress
            String remainingText = null;
            String percentText = null;
            if (type == TYPE_ACTIVE) {
                long current = 0;
                long total = 0;
                long speed = 0;
                synchronized (mUploadSpeed) {
                    for (UploadInfo info : cluster) {
                        if (info.mTotalBytes != -1) {
                            current += info.mCurrentBytes;
                            total += info.mTotalBytes;
                            speed += mUploadSpeed.get(info.mId) == null ? 0 : mUploadSpeed.get(info.mId);
                        }
                    }
                }

                if (total > 0) {
                    percentText =
                            NumberFormat.getPercentInstance().format((double) current / total);

                    if (speed > 0) {
                        final long remainingMillis = ((total - current) * 1000) / speed;
                        remainingText = res.getString(R.string.upload_remaining,
                                TimeUtils.formatDuration(remainingMillis, res));
                    }

                    final int percent = (int) ((current * 100) / total);
                    builder.setProgress(100, percent, false);
                } else {
                    builder.setProgress(100, 0, true);
                }
            }

            final Notification notification;
            if (cluster.size() == 1) {
                final UploadInfo info = cluster.iterator().next();

                builder.setContentTitle(getUploadTitle(res, info));

                if (type == TYPE_ACTIVE) {
                    if (!TextUtils.isEmpty(info.mDescription)) {
                        builder.setContentText(info.mDescription);
                    } else {
                        builder.setContentText(remainingText);
                    }
                    builder.setContentInfo(percentText);

                } else if (type == TYPE_WAITING) {
                    //For both continuable transfer and network check before transfer,
                    //but Box doesn't support previous one.
                    builder.setContentText(res.getText(
                            R.string.notification_waiting_for_suitable_network));

                } else if (type == TYPE_COMPLETE) {
                    builder.setContentText(res.getString(R.string.notification_upload_successfully));

                } else if (type == TYPE_FAILED) {
                    // TODO: 2016/2/28 TEMP FIX
                    if (info.mStatus == UploadContract.UPLOAD_STATUS.FILE_NOT_FOUND) {
                        builder.setContentText(res.getString(R.string.notification_upload_file_not_found));
                    } else {
                        builder.setContentText(res.getString(R.string.notification_upload_unsuccessfully));
                    }
                }

                notification = builder.build();

            } else {
                // handle more than one items

                final NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle(builder);

                for (UploadInfo info : cluster) {
                    inboxStyle.addLine(getUploadTitle(res, info));
                }

                if (type == TYPE_ACTIVE) {
                    builder.setContentTitle(res.getQuantityString(
                            R.plurals.notif_summary_active, cluster.size(), cluster.size()));
                    builder.setContentText(remainingText);
                    builder.setContentInfo(percentText);
                    inboxStyle.setSummaryText(remainingText);

                } else if (type == TYPE_WAITING) {
                    builder.setContentTitle(
                            res.getQuantityString(R.plurals.notif_summary_waiting, cluster.size(), cluster.size()));
                    builder.setContentText(
                            res.getString(R.string.notification_waiting_for_suitable_network));
                    inboxStyle.setSummaryText(
                            res.getString(R.string.notification_waiting_for_suitable_network));

                } else if (type == TYPE_FAILED) {
                    builder.setContentText(res.getQuantityString(R.plurals.notif_summary_failed, cluster.size(), cluster.size()));
                }

                notification = inboxStyle.build();

            }
            mNotifManager.notify(tag, 0, notification);

        }

        final Iterator<String> it = mActiveNotif.keySet().iterator();
        while (it.hasNext()) {
            final String tag = it.next();
            if (!clustered.containsKey(tag)) {
                mNotifManager.cancel(tag, 0);
                it.remove();
            }
        }
    }

    private long[] getUploadIds(Collection<UploadInfo> infos) {
        final long[] ids = new long[infos.size()];
        int i = 0;
        for (UploadInfo info : infos) {
            ids[i++] = info.mId;
        }
        return ids;
    }

    public void dumpSpeeds() {
        synchronized (mUploadSpeed) {
            for (int i = 0; i < mUploadSpeed.size(); i++) {
                final long id = mUploadSpeed.keyAt(i);
                final long delta = SystemClock.elapsedRealtime() - mUploadTouch.get(id);
                Log.d("UploadManager", "Upload " + id + " speed " + mUploadSpeed.valueAt(i) + "bps, "
                        + delta + "ms ago");

            }
        }
    }
}
