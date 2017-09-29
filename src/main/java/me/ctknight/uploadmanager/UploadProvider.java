/*
 * Copyright (c) 2016. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.UriMatcher;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import me.ctknight.uploadmanager.util.LogUtils;

public final class UploadProvider extends ContentProvider {

    private static final String TAG = LogUtils.makeTag(UploadProvider.class);
    /** Database filename */
    private static final String DB_NAME = "uploads.db";
    /** Current database version */
    private static final int DB_VERSION = 1;
    /** Name of table in the database */
    private static final String DB_TABLE = "uploads";
    private static final String PATH_UPLOAD = "uploads";
    /** MIME type for the entire download list */
    private static final String UPLOAD_DIR_TYPE =
            ContentResolver.CURSOR_DIR_BASE_TYPE + "/" +
                    UploadContract.UPLOAD_URIS.UPLOAD_AUTHORITY + "/" + PATH_UPLOAD;
    /** MIME type for an individual download */
    private static final String UPLOAD_TYPE =
            ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" +
                    UploadContract.UPLOAD_URIS.UPLOAD_AUTHORITY + "/" + PATH_UPLOAD;

    private static final int SINGLE_UPLOAD = 1;
    private static final int ALL_UPLOADS = 2;
    private static final int REQUEST_HEADERS_URI = 3;
    private static final int REQUEST_CD_URI = 4;

    /** URI matcher used to recognize URIs sent by applications */
    private static final UriMatcher sURIMatcher = buildUriMatcher();
    private Handler mHandler;
    private DatabaseHelper mDatabaseHelper;

    public UploadProvider() {
    }

    @NonNull
    private static UriMatcher buildUriMatcher() {
        final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
        final String authority = UploadContract.UPLOAD_URIS.UPLOAD_AUTHORITY;
        matcher.addURI(authority, PATH_UPLOAD, ALL_UPLOADS);
        matcher.addURI(authority, PATH_UPLOAD + "/#", SINGLE_UPLOAD);
        matcher.addURI(authority,
                PATH_UPLOAD + "/#/" + UploadContract.RequestContent.URI_SEGMENT,
                REQUEST_HEADERS_URI);
        matcher.addURI(authority,
                PATH_UPLOAD + "/#/" + UploadContract.RequestContent.CD_URI_SEGMENT,
                REQUEST_CD_URI);

        return matcher;
    }

    private static final void copyInteger(String key, ContentValues from, ContentValues to) {
        Integer i = from.getAsInteger(key);
        if (i != null) {
            to.put(key, i);
        }
    }

    private static final void copyBoolean(String key, ContentValues from, ContentValues to) {
        Boolean b = from.getAsBoolean(key);
        if (b != null) {
            to.put(key, b);
        }
    }

    private static final void copyString(String key, ContentValues from, ContentValues to) {
        String s = from.getAsString(key);
        if (s != null) {
            to.put(key, s);
        }
    }

    private static final void copyStringWithDefault(String key, ContentValues from,
                                                    ContentValues to, String defaultValue) {
        copyString(key, from, to);
        if (!to.containsKey(key)) {
            to.put(key, defaultValue);
        }
    }

    //NOTE: Don't call getContentResolver().delete(Uri) to invoke this method, use UploadManager.remove(int id).
    @Override
    public int delete(@NonNull final Uri uri, final String selection, final String[] selectionArgs) {

        SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
        int count = -1;//default:fail
        int match = sURIMatcher.match(uri);
        switch (match) {
            case SINGLE_UPLOAD:
            case ALL_UPLOADS:
                SqlSelection sqlSelection = getWhereClause(uri, selection, selectionArgs, match);
                deleteRequestContent(db, sqlSelection.getSelection(), sqlSelection.getParameters());

                count = db.delete(DB_TABLE, sqlSelection.getSelection(), sqlSelection.getParameters());
                break;
            default:
                Log.d(TAG, " calling delete() on an unknown/invalid URI:" + uri);
        }
        notifyContentChanged(uri, match);
        return count;
    }

    @Override
    public String getType(@NonNull final Uri uri) {

        int match = sURIMatcher.match(uri);
        switch (match) {
            case ALL_UPLOADS:
                return UPLOAD_DIR_TYPE;
            case SINGLE_UPLOAD: {
                final String id = getUploadIdFromUri(uri);
                final SQLiteDatabase db = mDatabaseHelper.getReadableDatabase();
                final String mimeType = DatabaseUtils.stringForQuery(db,
                        "SELECT " + UploadContract.UPLOAD_COLUMNS.COLUMN_MIME_TYPE + " FROM " + DB_TABLE +
                                " WHERE " + UploadContract.UPLOAD_COLUMNS._ID + " = ?",
                        new String[]{id});
                if (TextUtils.isEmpty(mimeType)) {
                    return UPLOAD_TYPE;
                } else {
                    return mimeType;
                }
            }
            default: {
                Log.v(TAG, "call getType() on an unknown URI " + uri);
                throw new IllegalArgumentException("Unknown URI: " + uri);
            }

        }

    }

    @Override
    public Uri insert(@NonNull final Uri uri, final ContentValues values) {
        SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
        final Context context = getContext();
        final ApplicationInfo appInfo = context.getApplicationInfo();

        int match = sURIMatcher.match(uri);
        if (match != SINGLE_UPLOAD && match != ALL_UPLOADS) {
            Log.d(TAG, " calling insert() on an unknown URI " + uri);
            throw new IllegalArgumentException();
        }
        values.put(UploadContract.UPLOAD_COLUMNS.COLUMN_UID, appInfo.uid);

        ContentValues filteredValues = new ContentValues();

        copyString(UploadContract.UPLOAD_COLUMNS.COLUMN_TARGET_URL, values, filteredValues);
        copyString(UploadContract.UPLOAD_COLUMNS._DATA, values, filteredValues);
        copyString(UploadContract.UPLOAD_COLUMNS.COLUMN_FILE_URI, values, filteredValues);
        copyString(UploadContract.UPLOAD_COLUMNS.COLUMN_MIME_TYPE, values, filteredValues);
        copyString(UploadContract.UPLOAD_COLUMNS.COLOMN_DATA_FIELD_NAME, values, filteredValues);
        copyInteger(UploadContract.UPLOAD_COLUMNS.COLUMN_UID, values, filteredValues);

        copyString(UploadContract.UPLOAD_COLUMNS.COLUMN_TITLE, values, filteredValues);
        copyString(UploadContract.UPLOAD_COLUMNS.COLUMN_DESCRIPTION, values, filteredValues);

        copyString(UploadContract.RequestContent.COLUMN_USER_AGENT, values, filteredValues);

        copyInteger(UploadContract.UPLOAD_COLUMNS.COLUMN_VISIBILITY, values, filteredValues);
        copyBoolean(UploadContract.UPLOAD_COLUMNS.COLUMN_ALLOW_ROAMING, values, filteredValues);
        //filter columns to process safe insert.

        long rowID = db.insert(DB_TABLE, null, filteredValues);
        if (rowID == -1) {
            Log.d(TAG, " couldn't insert into uploads database");
            return null;
        }

        insertRequestHeaders(db, rowID, values);
        insertRequestBody(db, rowID, values);
        notifyContentChanged(uri, match, rowID);


        context.startService(new Intent(context, UploadService.class));

        return ContentUris.withAppendedId(UploadContract.UPLOAD_URIS.CONTENT_URI, rowID);
    }

    @Override
    public boolean onCreate() {

        HandlerThread handlerThread =
                new HandlerThread("UploadProvider handler", Process.THREAD_PRIORITY_BACKGROUND);
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());

        mDatabaseHelper = new DatabaseHelper(getContext());

        Context context = getContext();

        context.startService(new Intent(context, UploadService.class));

        return true;
    }

    private Cursor queryCleared(Uri uri, String[] projection, String selection,
                                String[] selectionArgs, String sort) {
        final long token = Binder.clearCallingIdentity();
        try {
            return query(uri, projection, selection, selectionArgs, sort);
        } finally {
            Binder.clearCallingIdentity();
        }
    }

    @Override
    public Cursor query(@NonNull final Uri uri, final String[] projection, final String selection,
                        final String[] selectionArgs, final String sortOrder) {
        SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();

        int match = sURIMatcher.match(uri);
        if (match == -1) {
            Log.v(TAG, "querying unknown URI: " + uri);
            throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        if (match == REQUEST_HEADERS_URI) {
            if (projection != null || selection != null || sortOrder != null) {
                throw new UnsupportedOperationException("Request header queries do not support "
                        + "projections, selections or sorting");
            }
            return queryRequestHeaders(db, uri);
        }

        if (match == REQUEST_CD_URI) {
            if (projection != null || selection != null || sortOrder != null) {
                throw new UnsupportedOperationException("Request header queries do not support "
                        + "projections, selections or sorting");
            }
            return queryRequestBody(db, uri);
        }

        SqlSelection fullSelection = getWhereClause(uri, selection, selectionArgs, match);
//        logVerboseQueryInfo(projection, selection, selectionArgs, sortOrder, db);
        Cursor ret = db.query(DB_TABLE, projection, fullSelection.getSelection(),
                fullSelection.getParameters(), null, null, sortOrder);

        if (ret != null) {
            ContentResolver resolver = getContext().getContentResolver();
            ret.setNotificationUri(resolver, uri);
        } else {
            Log.v(TAG, "query failed in uploads database");
        }

        return ret;
    }

    @Override
    public int update(@NonNull final Uri uri, final ContentValues values, final String selection,
                      final String[] selectionArgs) {
        SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
        int count;
        boolean startService = false;

        if (values.containsKey(UploadContract.UPLOAD_COLUMNS.COLUMN_DELETED)) {
            if (values.getAsInteger(UploadContract.UPLOAD_COLUMNS.COLUMN_DELETED) == 1) {
                //if some rows should be 'deleted', start the UploadService to handle them.
                startService = true;
            }
        }

        String filename = values.getAsString(UploadContract.UPLOAD_COLUMNS._DATA);
        if (filename != null) {
            Cursor c = null;
            try {
                c = query(uri, new String[]
                        {UploadContract.UPLOAD_COLUMNS.COLUMN_TITLE}, null, null, null);
                if (!c.moveToFirst() || c.getString(0).isEmpty()) {
                    values.put(UploadContract.UPLOAD_COLUMNS.COLUMN_TITLE, new File(filename).getName());
                    //check empty title
                }
            } catch (NullPointerException e) {
                Log.w(TAG, "update: cursor is null");
            } finally {
                c.close();
            }
        }

        Integer status = values.getAsInteger(UploadContract.UPLOAD_COLUMNS.COLUMN_STATUS);
        boolean isRestart = status != null && status == UploadContract.UPLOAD_STATUS.PENDING;
        if (isRestart) {
            startService = true;
        }

        int match = sURIMatcher.match(uri);
        switch (match) {
            case SINGLE_UPLOAD:
            case ALL_UPLOADS:
                SqlSelection sqlSelection = getWhereClause(uri, selection, selectionArgs, match);
                if (values.size() > 0) {
                    count = db.update(DB_TABLE, values, sqlSelection.getSelection(), sqlSelection.getParameters());

                } else {
                    count = 0;
                }
                break;

            default:
                Log.d(TAG, "calling unknown/invalid URI: " + uri);
                throw new UnsupportedOperationException("Can't update invalid URI: " + uri);
        }

        notifyContentChanged(uri, match);
        if (startService) {
            Context context = getContext();
            context.startService(new Intent(context, UploadService.class));
        }
        return count;
    }

    private void logVerboseQueryInfo(String[] projection, final String selection,
                                     final String[] selectionArgs, final String sort, SQLiteDatabase db) {
        StringBuilder sb = new StringBuilder();
        sb.append("starting query, database is ");
        if (db != null) {
            sb.append("not ");
        }
        sb.append("null; ");
        if (projection == null) {
            sb.append("projection is null; ");
        } else if (projection.length == 0) {
            sb.append("projection is empty; ");
        } else {
            for (int i = 0; i < projection.length; ++i) {
                sb.append("projection[");
                sb.append(i);
                sb.append("] is ");
                sb.append(projection[i]);
                sb.append("; ");
            }
        }
        sb.append("selection is ");
        sb.append(selection);
        sb.append("; ");
        if (selectionArgs == null) {
            sb.append("selectionArgs is null; ");
        } else if (selectionArgs.length == 0) {
            sb.append("selectionArgs is empty; ");
        } else {
            for (int i = 0; i < selectionArgs.length; ++i) {
                sb.append("selectionArgs[");
                sb.append(i);
                sb.append("] is ");
                sb.append(selectionArgs[i]);
                sb.append("; ");
            }
        }
        sb.append("sort is ");
        sb.append(sort);
        sb.append(".");
        Log.v(TAG, sb.toString());
    }

    private String getUploadIdFromUri(final Uri uri) {
        return uri.getLastPathSegment();
    }

    private SqlSelection getWhereClause(final Uri uri, final String where, final String[] whereArgs,
                                        int uriMatch) {
        SqlSelection sqlSelection = new SqlSelection();
        sqlSelection.appendClause(where, whereArgs);

        if (uriMatch == SINGLE_UPLOAD) {
            sqlSelection.appendClause(UploadContract.UPLOAD_COLUMNS._ID + "= ?", getUploadIdFromUri(uri));
        }
        if (uriMatch == ALL_UPLOADS) {
            sqlSelection.appendClause(UploadContract.UPLOAD_COLUMNS.COLUMN_UID + " = ?", Binder.getCallingUid());
        }

        return sqlSelection;
    }

    private void notifyContentChanged(final Uri uri, int uriMatch) {
        Long uploadId = null;
        if (uriMatch == SINGLE_UPLOAD) {
            uploadId = Long.parseLong(getUploadIdFromUri(uri));
        }
        Uri uriToNotify = UploadContract.UPLOAD_URIS.CONTENT_URI;
        if (uploadId != null) {
            uriToNotify = ContentUris.withAppendedId(UploadContract.UPLOAD_URIS.CONTENT_URI, uploadId);
        }
        getContext().getContentResolver().notifyChange(uriToNotify, null);

    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        int match = sURIMatcher.match(uri);
        switch (match) {
            case SINGLE_UPLOAD: {
                Cursor cursor = query(uri, new String[]{UploadContract.UPLOAD_COLUMNS.COLUMN_FILE_URI},
                        UploadContract.UPLOAD_COLUMNS._ID + " = ?",
                        new String[]{getUploadIdFromUri(uri)}, null);
                if (cursor.moveToFirst()) {
                    String fileUri = cursor.getString(0);
                    return getContext().getContentResolver().openFileDescriptor(Uri.parse(fileUri), mode);
                }
                // if not, fall through
            }
            default:
                return null;
        }
    }

    private void notifyContentChanged(final Uri uri, int uriMatch, long id) {

        if (uriMatch == SINGLE_UPLOAD || uriMatch == ALL_UPLOADS) {
            return;
        }
        Uri uriToNotify = ContentUris.withAppendedId(UploadContract.UPLOAD_URIS.CONTENT_URI, id);
        getContext().getContentResolver().notifyChange(uriToNotify, null);

    }

    private Cursor queryRequestHeaders(SQLiteDatabase db, Uri uri) {
        String where = UploadContract.RequestContent.COLUMN_UPLOAD_ID + "="
                + uri.getPathSegments().get(1);
        String[] projection = new String[]{UploadContract.RequestContent.COLUMN_HEADER_NAME,
                UploadContract.RequestContent.COLUMN_HEADER_VALUE};
        return db.query(UploadContract.RequestContent.REQUEST_CONTENT_DB_TABLE, projection, where,
                null, null, null, null);
    }

    private void insertRequestHeaders(SQLiteDatabase db, long downloadId, ContentValues values) {
        ContentValues rowValues = new ContentValues();
        rowValues.put(UploadContract.RequestContent.COLUMN_UPLOAD_ID, downloadId);
        for (String key : values.keySet()) {
            if (key.startsWith(UploadContract.RequestContent.INSERT_KEY_PREFIX)) {
                String headerLine = values.get(key).toString();
                if (!headerLine.contains(":")) {
                    throw new IllegalArgumentException("Invalid HTTP header line: " + headerLine);
                }
                String[] parts = headerLine.split(":", 2);
                rowValues.put(UploadContract.RequestContent.COLUMN_HEADER_NAME, parts[0].trim());
                rowValues.put(UploadContract.RequestContent.COLUMN_HEADER_VALUE, parts[1].trim());
                db.insert(UploadContract.RequestContent.REQUEST_CONTENT_DB_TABLE, null, rowValues);
            }
        }
    }

    private void deleteRequestContent(SQLiteDatabase db, String where, String[] whereArgs) {
        String[] projection = new String[]{UploadContract.UPLOAD_COLUMNS._ID};
        Cursor cursor = db.query(DB_TABLE, projection, where, whereArgs, null, null, null, null);
        try {
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String idWhere = UploadContract.RequestContent.COLUMN_UPLOAD_ID + "=" + id;
                db.delete(UploadContract.RequestContent.REQUEST_CONTENT_DB_TABLE, idWhere, null);
            }
        } finally {
            cursor.close();
        }
    }

    private Cursor queryRequestBody(SQLiteDatabase db, Uri uri) {
        String where = UploadContract.RequestContent.COLUMN_UPLOAD_ID + "="
                + uri.getPathSegments().get(1);
        String[] projection = new String[]{UploadContract.RequestContent.COLUMN_CD_NAME,
                UploadContract.RequestContent.COLUMN_CD_VALUE};
        return db.query(UploadContract.RequestContent.REQUEST_CONTENT_DB_TABLE, projection, where,
                null, null, null, null);
    }

    private void insertRequestBody(SQLiteDatabase db, long downloadId, ContentValues values) {
        ContentValues rowValues = new ContentValues();
        rowValues.put(UploadContract.RequestContent.COLUMN_UPLOAD_ID, downloadId);
        for (Map.Entry<String, Object> entry : values.valueSet()) {
            String key = entry.getKey();
            if (key.startsWith(UploadContract.RequestContent.INSERT_CD_PREFIX)) {
                String bodyLine = entry.getValue().toString();
                if (!bodyLine.contains(":")) {
                    throw new IllegalArgumentException("Invalid Content-Disposition line: " + bodyLine);
                }
                String[] parts = bodyLine.split(":", 2);
                rowValues.put(UploadContract.RequestContent.COLUMN_CD_NAME, parts[0].trim());
                rowValues.put(UploadContract.RequestContent.COLUMN_CD_VALUE, parts[1].trim());
                db.insert(UploadContract.RequestContent.REQUEST_CONTENT_DB_TABLE, null, rowValues);
            }
        }
    }

    private static class SqlSelection {
        public StringBuilder mWhereClause = new StringBuilder();
        public List<String> mParameters = new ArrayList<String>();

        public <T> void appendClause(String newClause, final T... parameters) {
            if (newClause == null || newClause.isEmpty()) {
                return;
            }
            if (mWhereClause.length() != 0) {
                mWhereClause.append(" AND ");
            }
            mWhereClause.append("(");
            mWhereClause.append(newClause);
            mWhereClause.append(")");
            if (parameters != null) {
                for (Object parameter : parameters) {
                    mParameters.add(parameter.toString());
                }
            }
        }

        public String getSelection() {
            return mWhereClause.toString();
        }

        public String[] getParameters() {
            String[] array = new String[mParameters.size()];
            return mParameters.toArray(array);
        }
    }

    private final class DatabaseHelper extends SQLiteOpenHelper {
        public DatabaseHelper(final Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        //Called when create a new database for the first time.


        @Override
        public void onCreate(final SQLiteDatabase db) {
            createRequestContentTable(db);
            createUploadTable(db);
        }

        private void createUploadTable(SQLiteDatabase db) {

            try {
                db.execSQL("DROP TABLE IF EXISTS " + DB_TABLE);
                db.execSQL
                        ("CREATE TABLE " + DB_TABLE + "(" +
                                UploadContract.UPLOAD_COLUMNS._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                                UploadContract.UPLOAD_COLUMNS._DATA + " TEXT, " +
                                UploadContract.UPLOAD_COLUMNS.COLUMN_TARGET_URL + " TEXT, " +
                                UploadContract.UPLOAD_COLUMNS.COLUMN_FILE_URI + " TEXT, " +
                                UploadContract.UPLOAD_COLUMNS.COLUMN_UID + " INTEGER, " +
                                UploadContract.UPLOAD_COLUMNS.COLUMN_STATUS + " INTEGER DEFAULT 0 , " +
                                UploadContract.UPLOAD_COLUMNS.COLUMN_NUM_FAILED + " INTEGER, " +
                                UploadContract.UPLOAD_COLUMNS.COLUMN_RETRY_AFTER + " INTEGER, " +
                                UploadContract.UPLOAD_COLUMNS.COLUMN_MIME_TYPE + " TEXT, " +
                                UploadContract.UPLOAD_COLUMNS.COLUMN_LAST_MODIFICATION + " BIGINT, " +
                                UploadContract.UPLOAD_COLUMNS.COLUMN_TITLE + " TEXT, " +
                                UploadContract.UPLOAD_COLUMNS.COLUMN_DESCRIPTION + " TEXT, " +
                                UploadContract.UPLOAD_COLUMNS.COLUMN_TOTAL_BYTES + " INTEGER, " +
                                UploadContract.UPLOAD_COLUMNS.COLUMN_CURRENT_BYTES + " INTEGER, " +
                                UploadContract.UPLOAD_COLUMNS.COLUMN_DELETED + " INTEGER DEFAULT 0 , " +
                                UploadContract.UPLOAD_COLUMNS.COLUMN_NOTIFICATION_PACKAGE + " TEXT, " +
                                UploadContract.UPLOAD_COLUMNS.COLUMN_NOTIFICATION_CLASS + " TEXT, " +
                                UploadContract.UPLOAD_COLUMNS.COLUMN_NOTIFICATION_EXTRAS + " TEXT, " +
                                UploadContract.UPLOAD_COLUMNS.RETRY_AFTER_X_REDIRECT_COUNT + " INTEGER, " +
                                UploadContract.UPLOAD_COLUMNS.COLUMN_CONTROL + " INTEGER, " +
                                UploadContract.UPLOAD_COLUMNS.COLUMN_BYPASS_NETWORK_CHANGE + " BOOLEAN, " +
                                UploadContract.UPLOAD_COLUMNS.COLUMN_VISIBILITY + " INTEGER, " +
                                UploadContract.UPLOAD_COLUMNS.COLUMN_ERROR_MSG + " TEXT, " +
                                UploadContract.UPLOAD_COLUMNS.COLUMN_ALLOW_ROAMING + " INTEGER, " +
                                UploadContract.UPLOAD_COLUMNS.COLUMN_SERVER_RESPONSE + " TEXT, " +
                                UploadContract.UPLOAD_COLUMNS.COLOMN_DATA_FIELD_NAME + " TEXT, " +
                                UploadContract.RequestContent.COLUMN_USER_AGENT + " TEXT, " +
                                UploadContract.RequestContent.COLUMN_REFERER + " TEXT" + ");"
                        );
            } catch (SQLException e) {
                Log.e("SQLException", "Couldn't create upload_table in upload database ");
            }

        }

        private void createRequestContentTable(SQLiteDatabase db) {
            try {
                db.execSQL("DROP TABLE IF EXISTS " + UploadContract.RequestContent.REQUEST_CONTENT_DB_TABLE);
                db.execSQL("CREATE TABLE " + UploadContract.RequestContent.REQUEST_CONTENT_DB_TABLE + "(" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        UploadContract.RequestContent.COLUMN_UPLOAD_ID + " INTEGER NOT NULL," +
                        UploadContract.RequestContent.COLUMN_HEADER_NAME + " TEXT," +
                        UploadContract.RequestContent.COLUMN_HEADER_VALUE + " TEXT," +
                        UploadContract.RequestContent.COLUMN_CD_NAME + " TEXT," +
                        UploadContract.RequestContent.COLUMN_CD_VALUE + " TEXT" +
                        ");");
            } catch (SQLException e) {
                Log.e("SQLException", "Couldn't create headers_table in upload database ");
            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        }

        /**
         * Add a column to a table using ALTER TABLE.
         *
         * @param dbTable          name of the table
         * @param columnName       name of the column to add
         * @param columnDefinition SQL for the column definition
         */
        private void addColumn(SQLiteDatabase db, String dbTable, String columnName,
                               String columnDefinition) {
            db.execSQL("ALTER TABLE " + dbTable + " ADD COLUMN " + columnName + " "
                    + columnDefinition);
        }

        private void fillNullValuesForColumn(SQLiteDatabase db, ContentValues values) {
            String column = values.valueSet().iterator().next().getKey();
            db.update(DB_TABLE, values, column + " is null", null);
            values.clear();
        }
    }
}