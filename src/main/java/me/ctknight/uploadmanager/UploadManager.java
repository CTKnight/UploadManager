/*
 * Copyright (c) 2016. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager;


import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.ctknight.uploadmanager.util.FileUtils;
import me.ctknight.uploadmanager.util.UriUtils;

import static me.ctknight.uploadmanager.UploadContract.UPLOAD_COLUMNS.COLOMN_DATA_FIELD_NAME;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_COLUMNS.COLUMN_ALLOW_ROAMING;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_COLUMNS.COLUMN_CURRENT_BYTES;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_COLUMNS.COLUMN_LAST_MODIFICATION;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_COLUMNS.COLUMN_MIME_TYPE;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_COLUMNS.COLUMN_TARGET_URL;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_COLUMNS.COLUMN_TOTAL_BYTES;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_COLUMNS.COLUMN_VISIBILITY;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.CANNOT_RESUME;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.DEVICE_NOT_FOUND_ERROR;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.FILE_ERROR;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.FILE_NOT_FOUND;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.HTTP_DATA_ERROR;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.MIN_ARTIFICIAL_ERROR_STATUS;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.PENDING;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.RUNNING;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.SUCCESS;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.TOO_MANY_REDIRECTS;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.UNHANDLED_HTTP_CODE;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.UNHANDLED_REDIRECT;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.WAITING_FOR_NETWORK;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.WAITING_FOR_WIFI;
import static me.ctknight.uploadmanager.UploadContract.UPLOAD_STATUS.WAITING_TO_RETRY;
import static me.ctknight.uploadmanager.UploadContract.isStatusError;


public class UploadManager {

    /**
     * An identifier for a particular upload, unique across the system.  Clients use this ID to
     * make subsequent calls related to the upload.
     */
    public final static String COLUMN_ID = UploadContract.UPLOAD_COLUMNS._ID;

    /**
     * The client-supplied title for this upload.  This will be displayed in system notifications.
     * Defaults to the empty string.
     */
    public final static String COLUMN_TITLE = UploadContract.UPLOAD_COLUMNS.COLUMN_TITLE;

    /**
     * The client-supplied description of this upload.  This will be displayed in system
     * notifications.  Defaults to the empty string.
     */
    public final static String COLUMN_DESCRIPTION = UploadContract.UPLOAD_COLUMNS.COLUMN_DESCRIPTION;

    /**
     * URI to be uploaded.
     */
    public final static String COLUMN_REMOTE_URI = UploadContract.UPLOAD_COLUMNS.COLUMN_TARGET_URL;

    public final static String COLUMN_FILE_URI = UploadContract.UPLOAD_COLUMNS.COLUMN_FILE_URI;

    /**
     * Internet Media Type of the uploaded file.  If no value is provided upon creation, this will
     * initially be null and will be filled in based on the server's response once the upload has
     * started.
     *
     * @see <a href="http://www.ietf.org/rfc/rfc1590.txt">RFC 1590, defining Media Types</a>
     */
    public final static String COLUMN_MEDIA_TYPE = "media_type";

    /**
     * Total size of the upload in bytes.  This will initially be -1 and will be filled in once
     * the upload starts.
     */
    public final static String COLUMN_TOTAL_SIZE_BYTES = "total_size";

    /**
     * Uri where uploaded file will be stored.  If a destination is supplied by client, that URI
     * will be used here.  Otherwise, the value will initially be null and will be filled in with a
     * generated URI once the upload has started.
     */
    public final static String COLUMN_LOCAL_URI = "localuri";

    /**
     * Current status of the upload, as one of the * constants.
     */
    public final static String COLUMN_STATUS = UploadContract.UPLOAD_COLUMNS.COLUMN_STATUS;

    public final static String COLUMN_USER_AGENT = UploadContract.RequestContent.COLUMN_USER_AGENT;

    /**
     * Provides more detail on the status of the upload.  Its meaning depends on the value of
     * {@link #COLUMN_STATUS}.
     * <p>
     * When {@link #COLUMN_STATUS} is { #STATUS_FAILED}, this indicates the type of error that
     * occurred.  If an HTTP error occurred, this will hold the HTTP status code as defined in RFC
     * 2616.  Otherwise, it will hold one of the ERROR_* constants.
     * <p>
     * When {@link #COLUMN_STATUS} is { #STATUS_PAUSED}, this indicates why the upload is
     * paused.  It will hold one of the PAUSED_* constants.
     * <p>
     * If {@link #COLUMN_STATUS} is neither { #STATUS_FAILED} nor { #STATUS_PAUSED}, this
     * column's value is undefined.
     *
     * @see <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec6.html#sec6.1.1">RFC 2616
     * status codes</a>
     */
    public final static String COLUMN_REASON = "reason";

    /**
     * Number of bytes upload so far.
     */
    public final static String COLUMN_BYTES_UPLOADED_SO_FAR = COLUMN_CURRENT_BYTES;

    /**
     * Timestamp when the upload was last modified, in {@link System#currentTimeMillis
     * System.currentTimeMillis()} (wall clock time in UTC).
     */
    public final static String COLUMN_LAST_MODIFIED_TIMESTAMP = COLUMN_LAST_MODIFICATION;

    /**
     * Value of {@link #COLUMN_STATUS} when the upload is waiting to start.
     */
    public final static int STATUS_PENDING = 1 << 0;

    /**
     * Value of {@link #COLUMN_STATUS} when the upload is currently running.
     */
    public final static int STATUS_RUNNING = 1 << 1;

    /**
     * Value of {@link #COLUMN_STATUS} when the upload is waiting to retry or resume.
     */
    public final static int STATUS_PAUSED = 1 << 2;

    /**
     * Value of {@link #COLUMN_STATUS} when the upload has successfully completed.
     */
    public final static int STATUS_SUCCESSFUL = 1 << 3;

    /**
     * Value of {@link #COLUMN_STATUS} when the upload has failed (and will not be retried).
     */
    public final static int STATUS_FAILED = 1 << 4;

    /**
     * Value of {@link #COLUMN_STATUS} when the upload has failed (and will not be retried).
     */
    public final static int STATUS_CANCELLED = 1 << 5;

    /**
     * Value of COLUMN_ERROR_CODE when the upload has completed with an error that doesn't fit
     * under any other error code.
     */
    public final static int ERROR_UNKNOWN = 1000;

    /**
     * Value of {@link #COLUMN_REASON} when a storage issue arises which doesn't fit under any
     * other error code. Use the more specific {@link #ERROR_INSUFFICIENT_SPACE} and
     * {@link #ERROR_DEVICE_NOT_FOUND} when appropriate.
     */
    public final static int ERROR_FILE_ERROR = 1001;

    /**
     * Value of {@link #COLUMN_REASON} when an HTTP code was received that upload manager
     * can't handle.
     */
    public final static int ERROR_UNHANDLED_HTTP_CODE = 1002;

    /**
     * Value of {@link #COLUMN_REASON} when an error receiving or processing data occurred at
     * the HTTP level.
     */
    public final static int ERROR_HTTP_DATA_ERROR = 1004;

    /**
     * Value of {@link #COLUMN_REASON} when there were too many redirects.
     */
    public final static int ERROR_TOO_MANY_REDIRECTS = 1005;

    /**
     * Value of {@link #COLUMN_REASON} when there was insufficient storage space. Typically,
     * this is because the SD card is full.
     */
    public final static int ERROR_INSUFFICIENT_SPACE = 1006;

    /**
     * Value of {@link #COLUMN_REASON} when no external storage device was found. Typically,
     * this is because the SD card is not mounted.
     */
    public final static int ERROR_DEVICE_NOT_FOUND = 1007;

    /**
     * Value of {@link #COLUMN_REASON} when some possibly transient error occurred but we can't
     * resume the upload.
     */
    public final static int ERROR_CANNOT_RESUME = 1008;

    /**
     * Value of {@link #COLUMN_REASON} when the requested destination file already exists (the
     * upload manager will not overwrite an existing file).
     */
    public final static int ERROR_FILE_ALREADY_EXISTS = 1009;

    /**
     * Value of {@link #COLUMN_REASON} when the upload has failed because of
     * { NetworkPolicyManager} controls on the requesting application.
     *
     * @hide
     */
    public final static int ERROR_BLOCKED = 1010;

    /**
     * Value of {@link #COLUMN_REASON} when the upload is paused because some network error
     * occurred and the upload manager is waiting before retrying the request.
     */
    public final static int PAUSED_WAITING_TO_RETRY = 1;

    /**
     * Value of {@link #COLUMN_REASON} when the upload is waiting for network connectivity to
     * proceed.
     */
    public final static int PAUSED_WAITING_FOR_NETWORK = 2;

    /**
     * Value of {@link #COLUMN_REASON} when the upload exceeds a size limit for uploads over
     * the mobile network and the upload manager is waiting for a Wi-Fi connection to proceed.
     */
    public final static int PAUSED_QUEUED_FOR_WIFI = 3;

    /**
     * Value of {@link #COLUMN_REASON} when the upload is paused for some other reason.
     */
    public final static int PAUSED_UNKNOWN = 4;

    /**
     * Broadcast intent action sent by the upload manager when a upload completes.
     */
    public final static String ACTION_UPLOAD_COMPLETE = "me.ctknight.uploadmanager.intent.action.UPLOAD_COMPLETE";

    /**
     * Broadcast intent action sent by the upload manager when the user clicks on a running
     * upload, either from a system notification or from the uploads UI.
     */
    public final static String ACTION_NOTIFICATION_CLICKED =
            "com.myqsc.mobile3.intent.action.UPLOAD_NOTIFICATION_CLICKED";

    /**
     * Intent action to launch an activity to display all uploads.
     */
    public final static String ACTION_VIEW_UPLOADS = "me.ctknight.uploadmanager.intent.action.VIEW_UPLOADS";

    /**
     * Intent extra included with {@link #ACTION_VIEW_UPLOADS} to start UploadApp in
     * sort-by-size mode.
     */
    public final static String INTENT_EXTRAS_SORT_BY_SIZE =
            "android.app.UploadManager.extra_sortBySize";

    /**
     * Intent extra included with {@link #ACTION_UPLOAD_COMPLETE} intents, indicating the ID (as a
     * long) of the upload that just completed.
     */
    public static final String EXTRA_UPLOAD_ID = "extra_upload_id";

    /**
     * When clicks on multiple notifications are received, the following
     * provides an array of upload ids corresponding to the upload notification that was
     * clicked. It can be retrieved by the receiver of this
     * Intent using {@link android.content.Intent#getLongArrayExtra(String)}.
     */
    public static final String EXTRA_NOTIFICATION_CLICK_UPLOAD_IDS = "extra_click_upload_ids";
    /**
     * columns to request from DownloadProvider.
     *
     * @hide
     */
    public static final String[] UNDERLYING_COLUMNS = new String[]{
            BaseColumns._ID,
            COLUMN_TITLE,
            COLUMN_DESCRIPTION,
            COLUMN_STATUS,
            COLUMN_TARGET_URL,
            COLUMN_FILE_URI + " AS " + COLUMN_FILE_URI,
            COLUMN_MIME_TYPE + " AS " + COLUMN_MEDIA_TYPE,
            COLUMN_TOTAL_BYTES + " AS " + COLUMN_TOTAL_SIZE_BYTES,
            COLUMN_LAST_MODIFICATION + " AS " + COLUMN_LAST_MODIFIED_TIMESTAMP,
            COLUMN_CURRENT_BYTES + " AS " + COLUMN_BYTES_UPLOADED_SO_FAR,
        /* add the following 'computed' columns to the cursor.
         * they are not 'returned' by the database, but their inclusion
         * eliminates need to have lot of methods in CursorTranslator
         */
            "'placeholder' AS " + COLUMN_LOCAL_URI,
            "'placeholder' AS " + COLUMN_REASON
    };
    private static UploadManager mUploadManager = null;
    private ContentResolver mResolver;
    private Uri mBaseUri = UploadContract.UPLOAD_URIS.CONTENT_URI;

    private UploadManager(Context context) {
        mResolver = context.getApplicationContext().getContentResolver();
    }

    public static UploadManager getUploadManger(Context context) {
        if (mUploadManager == null) {
            mUploadManager = new UploadManager(context.getApplicationContext());
        }
        return mUploadManager;
    }

    /**
     * Get a parameterized SQL WHERE clause to select a bunch of IDs.
     */
    static String getWhereClauseForIds(long[] ids) {
        StringBuilder whereClause = new StringBuilder();
        whereClause.append("(");
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) {
                whereClause.append("OR ");
            }
            whereClause.append(BaseColumns._ID);
            whereClause.append(" = ? ");
        }
        whereClause.append(")");
        return whereClause.toString();
    }

    /**
     * Get the selection args for a clause returned by {@link #getWhereClauseForIds(long[])}.
     */
    static String[] getWhereArgsForIds(long[] ids) {
        String[] whereArgs = new String[ids.length];
        for (int i = 0; i < ids.length; i++) {
            whereArgs[i] = Long.toString(ids[i]);
        }
        return whereArgs;
    }

    public long enqueue(Request request) {
        ContentValues values = request.toContentValues();
        Uri uploadUri = mResolver.insert(UploadContract.UPLOAD_URIS.CONTENT_URI, values);
        long id = Long.parseLong(uploadUri.getLastPathSegment());
        return id;
    }

    public int remove(long... ids) {
        if (ids == null || ids.length == 0) {
            // called with nothing to remove!
            throw new IllegalArgumentException("input param 'ids' can't be null");
        }
        ContentValues values = new ContentValues();
        values.put(UploadContract.UPLOAD_COLUMNS.COLUMN_DELETED, 1);
        // if only one id is passed in, then include it in the uri itself.
        // this will eliminate a full database scan in the download service.
        if (ids.length == 1) {
            return mResolver.update(ContentUris.withAppendedId(mBaseUri, ids[0]), values,
                    null, null);
        }
        // TODO: 2016/2/5 use bulkinsert() instead
        return mResolver.update(mBaseUri, values, getWhereClauseForIds(ids),
                getWhereArgsForIds(ids));
    }

    public Cursor query(Query query) {
        Cursor underlyingCursor = query.runQuery(mResolver, UNDERLYING_COLUMNS, mBaseUri);
        if (underlyingCursor == null) {
            return null;
        }
        return new CursorTranslator(underlyingCursor, mBaseUri);
    }

    public ParcelFileDescriptor openUploadedFile(long id) throws FileNotFoundException {
        return mResolver.openFileDescriptor(getUploadUri(id), "r");
    }

    public Uri getUriForUploadedFile(long id) {
        // to check if the file is in cache, get its destination from the database
        Query query = new Query().setFilterById(id);
        Cursor cursor = null;
        try {
            cursor = query(query);
            if (cursor == null) {
                return null;
            }
            if (cursor.moveToFirst()) {
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_STATUS));
                if (UploadManager.ERROR_FILE_ERROR != status) {
                    return ContentUris.withAppendedId(UploadContract.UPLOAD_URIS.CONTENT_URI, id);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        // uploaded file not found or its status is not 'successfully completed'
        return null;
    }

    public String getMimeTypeForUploadedFile(long id) {
        Query query = new Query().setFilterById(id);
        Cursor cursor = null;
        try {
            cursor = query(query);
            if (cursor == null) {
                return null;
            }
            while (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MEDIA_TYPE));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        //uploaded file not found or its status is not 'successfully completed'
        return null;
    }

    public void restartUpload(long... ids) {
        Cursor cursor = query(new Query().setFilterById(ids));
        try {
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                int status = cursor.getInt(cursor.getColumnIndex(COLUMN_STATUS));
                if (status != STATUS_FAILED && status != STATUS_CANCELLED) {
                    throw new IllegalArgumentException("Cannot restart incomplete upload: "
                            + cursor.getLong(cursor.getColumnIndex(COLUMN_ID)));
                }
            }
        } finally {
            cursor.close();
        }

        ContentValues values = new ContentValues();
        values.put(UploadContract.UPLOAD_COLUMNS.COLUMN_CURRENT_BYTES, 0);
        values.put(UploadContract.UPLOAD_COLUMNS.COLUMN_TOTAL_BYTES, -1);
        values.put(UploadContract.UPLOAD_COLUMNS.COLUMN_STATUS, UploadContract.UPLOAD_STATUS.PENDING);
        values.put(UploadContract.UPLOAD_COLUMNS.COLUMN_NUM_FAILED, 0);
        mResolver.update(mBaseUri, values, getWhereClauseForIds(ids), getWhereArgsForIds(ids));
    }

    public Uri getUploadUri(long id) {
        return ContentUris.withAppendedId(mBaseUri, id);
    }

    public static class Request {
        /**
         * This upload is visible but only shows in the notifications
         * while it's in progress.
         */
        public static final int VISIBILITY_VISIBLE = 0;
        /**
         * This upload is visible and shows in the notifications while
         * in progress and after completion.
         */
        public static final int VISIBILITY_VISIBLE_COMPLETED = 1;
        /**
         * This upload doesn't show in the UI or in the notifications.
         */
        public static final int VISIBILITY_HIDDEN = 5;
        /**
         * This upload shows in the notifications after completion ONLY.
         * It is usuable only with
         * { UploadManager.addCompletedUpload(String, String,
         * boolean, String, String, long, boolean)}.
         */
        public static final int VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION = 3;
        private Context mContext;
        private Uri mTargetUrl;
        private Uri mFileUri;
        private Map<String, String> mRequestHeaders = new HashMap<>();
        private Map<String, String> mContentDispositions = new HashMap<>();
        private String mFilename;
        private String mTitle;
        private String mDescription;
        private String mMimeType;
        private String mUserAgent;
        private String mDataFieldName;
        private boolean mMobileAllowed = true;
        /**
         * can take any of the following values: {@link #VISIBILITY_HIDDEN}
         * {@link #VISIBILITY_VISIBLE_COMPLETED}, {@link #VISIBILITY_VISIBLE},
         * {@link #VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION}
         */
        private int mNotificationVisibility = VISIBILITY_VISIBLE;

        /**
         * @param uri the HTTP or HTTPS URI to upload.
         */
        public Request(Uri uri, Context context) {
            if (uri == null) {
                throw new NullPointerException();
            }
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
                throw new IllegalArgumentException("Can only upload http/https URIs: " + uri);
            }
            mTargetUrl = uri;
            mContext = context.getApplicationContext();
        }

        Request(String uriString) {
            mTargetUrl = Uri.parse(uriString);
        }

        /**
         * Set the local destination for the uploaded file. Must be a file URI to a path on
         * external storage, and the calling application must have the WRITE_EXTERNAL_STORAGE
         * permission.
         * <p>
         * The uploaded file is not scanned by MediaScanner.
         * But it can be made scannable by calling { #allowScanningByMediaScanner()}.
         * <p>
         * By default, uploads are saved to a generated filename in the shared upload cache and
         * may be deleted by the system at any time to reclaim space.
         *
         * @return this object
         */
        public Request setFileUri(Uri uri) {
            mFileUri = uri;

            return this;
        }

        /**
         * Add an HTTP header to be included with the upload request.  The header will be added
         * to
         * the end of the list.
         *
         * @param header HTTP header name
         * @param value  header value
         * @return this object
         * @see <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2">HTTP/1.1
         * Message Headers</a>
         */
        public Request addRequestHeader(@NonNull String header, @Nullable String value) {
            if (header == null) {
                throw new NullPointerException("header cannot be null");
            }
            if (header.contains(":")) {
                throw new IllegalArgumentException("header may not contain ':'");
            }
            if (value == null) {
                value = "";
            }
            mRequestHeaders.put(header, value);
            return this;
        }

        public Request addRequestHeaders(Map<String, String> headers) {
            // add them one by one to avoid some Maps' not-allow-null implementation
            for (String key : headers.keySet()) {
                addRequestHeader(key, headers.get(key));
            }
            return this;
        }

        public Request addContentDisposition(@NonNull String name, @Nullable String value) {
            if (name == null) {
                throw new NullPointerException("content disposition cannot be null");
            }

            if (value == null) {
                value = "";
            }
            mContentDispositions.put(name, value);
            return this;
        }

        public Request addContentDispositions(Map<String, String> cds) {
            for (String key : cds.keySet()) {
                addContentDisposition(key, cds.get(key));
            }
            return this;
        }

        public Request addUserAgent(String userAgent) {
            mUserAgent = userAgent;
            return this;
        }

        /**
         * Set the title of this upload, to be displayed in notifications (if enabled).  If no
         * title is given, a default one will be assigned based on the upload filename, once the
         * upload starts.
         *
         * @return this object
         */
        public Request setTitle(String title) {
            mTitle = title;
            return this;
        }

        /**
         * Set a description of this upload, to be displayed in notifications (if enabled)
         *
         * @return this object
         */
        public Request setDescription(String description) {
            mDescription = description;
            return this;
        }

        /**
         * Set the MIME content type of this upload.  This will override the content type
         * declared
         * in the server's response.
         *
         * @return this object
         * @see <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.7">HTTP/1.1
         * Media Types</a>
         */
        public Request setMimeType(@Nullable String mimeType) {
            //call this only when you have to modify.
            mMimeType = mimeType;
            return this;
        }

        /**
         * Control whether a system notification is posted by the upload manager while this
         * upload is running or when it is completed.
         * If enabled, the upload manager posts notifications about uploads
         * through the system {@link android.app.NotificationManager}.
         * By default, a notification is shown only when the upload is in progress.
         * <p>
         * It can take the following values: {@link #VISIBILITY_HIDDEN},
         * {@link #VISIBILITY_VISIBLE},
         * {@link #VISIBILITY_VISIBLE_COMPLETED}.
         * <p>
         * If set to {@link #VISIBILITY_HIDDEN}, this requires the permission
         * android.permission.UPLOAD_WITHOUT_NOTIFICATION.
         *
         * @param visibility the visibility setting value
         * @return this object
         */
        public Request setNotificationVisibility(int visibility) {
            mNotificationVisibility = visibility;
            return this;
        }

        /**
         * Set whether this upload may proceed over a roaming connection.  By default, roaming is
         * allowed.
         *
         * @param allowed whether to allow a roaming connection to be used
         * @return this object
         */
        public Request setAllowedOverRoaming(boolean allowed) {
            mMobileAllowed = allowed;
            return this;
        }

        /**
         * set data field name.
         * for example :
         * ------WebKitFormBoundary6naClQj9yERx1WNV
         * Content-Disposition: form-data; name="file"; filename="sum.docx"
         * Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document
         * <p>
         * where name = "file" the "file" is field name.
         */
        public Request setDataFieldName(String dataFieldName) {
            mDataFieldName = dataFieldName;
            return this;
        }

        /**
         * @return ContentValues to be passed to UploadProvider.insert()
         */
        ContentValues toContentValues() {
            ContentValues values = new ContentValues();
            assert mTargetUrl != null;
            values.put(COLUMN_TARGET_URL, mTargetUrl.toString());

            if (!mRequestHeaders.isEmpty()) {
                encodeHttpHeaders(values);
            }

            if (!mContentDispositions.isEmpty()) {
                encodePayload(values);
            }
            //NOTE: if you change items here , you should also go to UploadProvider and add them in filteredValue.
            updateFilename(mFileUri);
            putIfNonNull(values, COLUMN_FILE_URI, mFileUri);
            putIfNonNull(values, COLUMN_MIME_TYPE, mMimeType == null ? mContext.getContentResolver().getType(mFileUri) : mMimeType);
            putIfNonNull(values, COLUMN_TITLE, mFilename);
            putIfNonNull(values, COLOMN_DATA_FIELD_NAME, mDataFieldName == null ? "file" : mDataFieldName);
            //use filename as default title.
            putIfNonNull(values, COLUMN_TITLE, mTitle);
            putIfNonNull(values, COLUMN_DESCRIPTION, mDescription);
            putIfNonNull(values, COLUMN_USER_AGENT, mUserAgent);

            values.put(COLUMN_VISIBILITY, mNotificationVisibility);
            values.put(COLUMN_ALLOW_ROAMING, mMobileAllowed);
            return values;
        }

        private void encodeHttpHeaders(ContentValues values) {
            int index = 0;
            for (Map.Entry<String, String> entry : mRequestHeaders.entrySet()) {
                String headerString = entry.getKey() + ": " + entry.getValue();
                values.put(UploadContract.RequestContent.INSERT_KEY_PREFIX + index, headerString);
                index++;
            }
        }

        private void encodePayload(ContentValues values) {
            int index = 0;
            for (Map.Entry<String, String> entry : mContentDispositions.entrySet()) {
                String cdString = entry.getKey() + ": " + entry.getValue();
                values.put(UploadContract.RequestContent.INSERT_CD_PREFIX + index, cdString);
                index++;
            }
        }


        public void updateFilename(Uri uri) {
            String fullPath = FileUtils.getPath(mContext, uri);
            if (fullPath != null) {
                mFilename = new File(fullPath).getName();
            } else {
                UriUtils.OpenableInfo info = UriUtils.queryOpenableInfo(uri, mContext);
                if (info != null) {
                    mFilename = info.getDisplayName();
                }
            }
        }

        private void putIfNonNull(ContentValues contentValues, String key, Object value) {
            if (value != null) {
                contentValues.put(key, value.toString());
            }
        }
    }

    /**
     * This class may be used to filter upload manager queries.
     */
    public static class Query {
        /**
         * Constant for use with {@link #orderBy}
         *
         * @hide
         */
        public static final int ORDER_ASCENDING = 1;

        /**
         * Constant for use with {@link #orderBy}
         *
         * @hide
         */
        public static final int ORDER_DESCENDING = 2;

        private long[] mIds = null;
        private Integer mStatusFlags = null;
        private String mOrderByColumn = COLUMN_LAST_MODIFICATION;
        private int mOrderDirection = ORDER_DESCENDING;

        /**
         * Include only the uploads with the given IDs.
         *
         * @return this object
         */
        public Query setFilterById(long... ids) {
            mIds = ids;
            return this;
        }

        /**
         * Include only uploads with status matching any the given status flags.
         *
         * @param flags any combination of the * bit flags
         * @return this object
         */
        public Query setFilterByStatus(int flags) {
            mStatusFlags = flags;
            return this;
        }

        /**
         * Change the sort order of the returned Cursor.
         *
         * @param column    one of the COLUMN_* constants; currently, only
         *                  { #COLUMN_LAST_MODIFIED_TIMESTAMP} and { #COLUMN_TOTAL_SIZE_BYTES} are
         *                  supported.
         * @param direction either {@link #ORDER_ASCENDING} or {@link #ORDER_DESCENDING}
         * @return this object
         */
        public Query orderBy(String column, int direction) {
            if (direction != ORDER_ASCENDING && direction != ORDER_DESCENDING) {
                throw new IllegalArgumentException("Invalid direction: " + direction);
            }

            if (column.equals(COLUMN_LAST_MODIFIED_TIMESTAMP)) {
                mOrderByColumn = UploadContract.UPLOAD_COLUMNS.COLUMN_LAST_MODIFICATION;
            } else if (column.equals(COLUMN_TOTAL_SIZE_BYTES)) {
                mOrderByColumn = UploadContract.UPLOAD_COLUMNS.COLUMN_TOTAL_BYTES;
            } else {
                throw new IllegalArgumentException("Cannot order by " + column);
            }
            mOrderDirection = direction;
            return this;
        }

        /**
         * Run this query using the given ContentResolver.
         *
         * @param projection the projection to pass to ContentResolver.query()
         * @return the Cursor returned by ContentResolver.query()
         */
        Cursor runQuery(ContentResolver resolver, String[] projection, Uri baseUri) {
            Uri uri = baseUri;
            List<String> selectionParts = new ArrayList<String>();
            String[] selectionArgs = null;

            if (mIds != null) {
                selectionParts.add(getWhereClauseForIds(mIds));
                selectionArgs = getWhereArgsForIds(mIds);
            }

            if (mStatusFlags != null) {
                List<String> parts = new ArrayList<String>();
                if ((mStatusFlags & STATUS_PENDING) != 0) {
                    parts.add(statusClause("=", UploadContract.UPLOAD_STATUS.PENDING));
                }
                if ((mStatusFlags & STATUS_RUNNING) != 0) {
                    parts.add(statusClause("=", UploadContract.UPLOAD_STATUS.RUNNING));
                }
                if ((mStatusFlags & STATUS_PAUSED) != 0) {
                    //parts.add(statusClause("=", UploadContract.UPLOAD_STATUS.PAUSED_BY_APP));
                    parts.add(statusClause("=", UploadContract.UPLOAD_STATUS.WAITING_TO_RETRY));
                    parts.add(statusClause("=", UploadContract.UPLOAD_STATUS.WAITING_FOR_NETWORK));
                    parts.add(statusClause("=", UploadContract.UPLOAD_STATUS.WAITING_FOR_WIFI));
                }
                if ((mStatusFlags & STATUS_SUCCESSFUL) != 0) {
                    parts.add(statusClause("=", UploadContract.UPLOAD_STATUS.SUCCESS));
                }
                if ((mStatusFlags & STATUS_FAILED) != 0) {
                    parts.add("(" + statusClause(">=", 400)
                            + " AND " + statusClause("<", 600) + ")");
                }
                selectionParts.add(joinStrings(" OR ", parts));
            }


            // only return rows which are not marked 'deleted = 1'
            selectionParts.add(UploadContract.UPLOAD_COLUMNS.COLUMN_DELETED + " != '1'");

            String selection = joinStrings(" AND ", selectionParts);
            String orderDirection = (mOrderDirection == ORDER_ASCENDING ? "ASC" : "DESC");
            String orderBy = mOrderByColumn + " " + orderDirection;

            return resolver.query(uri, projection, selection, selectionArgs, orderBy);
        }

        private String joinStrings(String joiner, Iterable<String> parts) {
            StringBuilder builder = new StringBuilder();
            boolean first = true;
            for (String part : parts) {
                if (!first) {
                    builder.append(joiner);
                }
                builder.append(part);
                first = false;
            }
            return builder.toString();
        }

        private String statusClause(String operator, int value) {
            return UploadContract.UPLOAD_COLUMNS.COLUMN_STATUS + operator + "'" + value + "'";
        }
    }

    /**
     * This class wraps a cursor returned by UploadProvider -- the "underlying cursor" -- and
     * presents a different set of columns, those defined in the DownloadManager.COLUMN_*
     * constants.
     * Some columns correspond directly to underlying values while others are computed from
     * underlying data.
     */
    private static class CursorTranslator extends CursorWrapper {
        private Uri mBaseUri;

        public CursorTranslator(Cursor cursor, Uri baseUri) {
            super(cursor);
            mBaseUri = baseUri;
        }

        @Override
        public int getInt(int columnIndex) {
            return (int) getLong(columnIndex);
        }

        @Override
        public long getLong(int columnIndex) {
            if (getColumnName(columnIndex).equals(COLUMN_REASON)) {
                return getReason(super.getInt(getColumnIndex(COLUMN_STATUS)));
            } else if (getColumnName(columnIndex).equals(COLUMN_STATUS)) {
                return translateStatus(super.getInt(getColumnIndex(COLUMN_STATUS)));
            } else {
                return super.getLong(columnIndex);
            }
        }

        @Override
        public String getString(int columnIndex) {
            return (getColumnName(columnIndex).equals(COLUMN_LOCAL_URI)) ? getLocalUri() :
                    super.getString(columnIndex);
        }

        private String getLocalUri() {
            // return content URI for cache upload
            long uploadId = getLong(getColumnIndex(UploadContract.UPLOAD_COLUMNS._ID));
            return ContentUris.withAppendedId(mBaseUri, uploadId).toString();
        }

        private long getReason(int status) {
            switch (translateStatus(status)) {
                case STATUS_FAILED:
                    return getErrorCode(status);

                case STATUS_PAUSED:
                    return getPausedReason(status);

                default:
                    return 0; // arbitrary value when status is not an error
            }
        }

        private long getPausedReason(int status) {
            switch (status) {
                case WAITING_TO_RETRY:
                    return PAUSED_WAITING_TO_RETRY;

                case WAITING_FOR_NETWORK:
                    return PAUSED_WAITING_FOR_NETWORK;

                case WAITING_FOR_WIFI:
                    return PAUSED_QUEUED_FOR_WIFI;

                default:
                    return PAUSED_UNKNOWN;
            }
        }

        private long getErrorCode(int status) {
            if ((400 <= status && status < MIN_ARTIFICIAL_ERROR_STATUS)
                    || (500 <= status && status < 600)) {
                // HTTP status code
                return status;
            }

            switch (status) {
                case FILE_ERROR:
                case FILE_NOT_FOUND:
                    return ERROR_FILE_ERROR;

                case UNHANDLED_HTTP_CODE:
                case UNHANDLED_REDIRECT:
                    return ERROR_UNHANDLED_HTTP_CODE;

                case HTTP_DATA_ERROR:
                    return ERROR_HTTP_DATA_ERROR;

                case TOO_MANY_REDIRECTS:
                    return ERROR_TOO_MANY_REDIRECTS;

                case DEVICE_NOT_FOUND_ERROR:
                    return ERROR_DEVICE_NOT_FOUND;

                case CANNOT_RESUME:
                    return ERROR_CANNOT_RESUME;

                default:
                    return ERROR_UNKNOWN;
            }
        }

        private int translateStatus(int status) {
            switch (status) {
                case PENDING:
                    return STATUS_PENDING;

                case RUNNING:
                    return STATUS_RUNNING;

                case STATUS_PAUSED:
                    return STATUS_PAUSED;

                case SUCCESS:
                    return STATUS_SUCCESSFUL;
                case WAITING_TO_RETRY:
                case WAITING_FOR_NETWORK:
                case WAITING_FOR_WIFI:
                default:
                    assert isStatusError(status);
                    return STATUS_FAILED;
            }
        }
    }


}
