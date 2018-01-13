/*
 * Copyright (c) 2018. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.io.File;

import me.ctknight.uploadmanager.UploadManager;

import static me.ctknight.uploadmanager.UploadManager.COLUMN_FILE_URI;
import static me.ctknight.uploadmanager.UploadManager.COLUMN_MEDIA_TYPE;
import static me.ctknight.uploadmanager.UploadManager.COLUMN_REMOTE_URI;

public class OpenHelper {
    public static final String TAG = "OpenHelper";

    /**
     * Build and start an {@link Intent} to view the download with given ID,
     * handling subtleties around installing packages.
     */
    public static boolean startViewIntent(Context context, long id, int intentFlags) {
        final Intent intent = OpenHelper.buildViewIntent(context, id);
        if (intent == null) {
            Log.w(TAG, "No intent built for " + id);
            return false;
        }

        intent.addFlags(intentFlags);
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Failed to start " + intent + ": " + e);
            return false;
        } catch (SecurityException e) {
            // TODO: 2016/5/6 add to doc: make sure Uris have granted permission.
            Log.e(TAG, "startViewIntent: failed to open url" + intent.getData());
            e.printStackTrace();
            ToastUtils.show("Failed to open this url: Permission denied", context);
            return false;
        }
    }

    /**
     * Build an {@link Intent} to view the upload with given ID, handling
     * subtleties around installing packages.
     */
    private static Intent buildViewIntent(Context context, long id) {
        final UploadManager uploadManager = UploadManager.getUploadManger(context);

        final Cursor cursor = uploadManager.query(new UploadManager.Query().setFilterById(id));
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }

            final Uri localUri = getCursorUri(cursor, COLUMN_FILE_URI);
            String mimeType = getCursorString(cursor, COLUMN_MEDIA_TYPE);

            final Intent intent = new Intent(Intent.ACTION_VIEW);

            if ("application/vnd.android.package-archive".equals(mimeType)) {
                // PackageInstaller doesn't like content URIs, so open file
                intent.setDataAndType(localUri, mimeType);

                // Also splice in details about where it came from
                final Uri remoteUri = getCursorUri(cursor, COLUMN_REMOTE_URI);
                intent.putExtra(Intent.EXTRA_ORIGINATING_URI, remoteUri);

            } else if ("file".equals(localUri.getScheme())) {
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                intent.setDataAndType(
                        localUri, mimeType);
            } else {
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setDataAndType(localUri, mimeType);
            }

            return intent;
        } finally {
            cursor.close();
        }
    }

    private static String getCursorString(Cursor cursor, String column) {
        return cursor.getString(cursor.getColumnIndexOrThrow(column));
    }

    private static Uri getCursorUri(Cursor cursor, String column) {
        return Uri.parse(getCursorString(cursor, column));
    }

    private static File getCursorFile(Cursor cursor, String column) {
        return new File(cursor.getString(cursor.getColumnIndexOrThrow(column)));
    }
}
