/*
 * Copyright (c) 2016. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager;

import android.content.ContentResolver;
import android.net.Uri;

public final class UploadContract {

    public static final String ACTION_OPEN = "me.ctknight.uploadmanager.action.UPLOAD_OPEN";
    public static final String ACTION_LIST = "me.ctknight.uploadmanager.action.UPLOAD_LIST";
    public static final String ACTION_RETRY = "me.ctknight.uploadmanager.action.UPLOAD_RETRY";
    // redo is manually triggered by
    public static final String ACTION_MANUAL_REDO = "me.ctknight.uploadmanager.action.UPLOAD_REDO";
    public static final String ACTION_CANCEL = "me.ctknight.uploadmanager.action.UPLOAD_CANCEL";
    public static final String ACTION_HIDE = "me.ctknight.uploadmanager.action.UPLOAD_HIDE";

    public static boolean isOnGoing(UploadInfo upload) {
        return upload.mStatus == UPLOAD_STATUS.RUNNING;
    }

    public static boolean isComplete(UploadInfo upload) {
        return upload.mStatus == UPLOAD_STATUS.SUCCESS;
    }

    public static boolean isComplete(int status) {
        return status == UPLOAD_STATUS.SUCCESS;
    }

    public static boolean isFailed(UploadInfo upload) {
        return upload.mStatus == UPLOAD_STATUS.FAILED ||
                upload.mStatus == UPLOAD_STATUS.CANCELED ||
                upload.mStatus == UPLOAD_STATUS.FILE_NOT_FOUND ||
                upload.mStatus == UPLOAD_STATUS.UNKNOWN_ERROR ||
                upload.mStatus == UPLOAD_STATUS.DEVICE_NOT_FOUND_ERROR ||
                upload.mStatus == UPLOAD_STATUS.HTTP_DATA_ERROR ||
                upload.mStatus == UPLOAD_STATUS.FILE_ERROR;
    }

    public static boolean isFailed(int status) {
        return status == UPLOAD_STATUS.FAILED ||
                status == UPLOAD_STATUS.CANCELED ||
                status == UPLOAD_STATUS.FILE_NOT_FOUND ||
                status == UPLOAD_STATUS.UNKNOWN_ERROR ||
                status == UPLOAD_STATUS.DEVICE_NOT_FOUND_ERROR ||
                status == UPLOAD_STATUS.HTTP_DATA_ERROR ||
                status == UPLOAD_STATUS.FILE_ERROR;
    }

    public static boolean isStatusError(int status) {
        return (status >= 400 && status < 600);
    }

    public static boolean isWaiting(UploadInfo upload) {
        return upload.mStatus == UPLOAD_STATUS.WAITING_FOR_NETWORK ||
                upload.mStatus == UPLOAD_STATUS.WAITING_FOR_WIFI ||
                upload.mStatus == UPLOAD_STATUS.WAITING_TO_RETRY;
    }

    public static final class Constants {


        /** The buffer size used to stream the data */

        public static final int BUFFER_SIZE = 8192;
        /** The minimum amount of progress that has to be done before the progress bar gets updated */
        public static final int MIN_PROGRESS_STEP = 65536;
        /**
         * The minimum amount of time that has to elapse before the progress bar gets updated, in
         * ms
         */
        public static final long MIN_PROGRESS_TIME = 2000;
        /**
         * The number of times that the download manager will retry its network
         * operations when no progress is happening before it gives up.
         */
        public static final int MAX_RETRIES = 5;
        /**
         * The minimum amount of time that the download manager accepts for
         * a Retry-After response header with a parameter in delta-seconds.
         */
        public static final int MIN_RETRY_AFTER = 30; // 30s
        /**
         * The maximum amount of time that the download manager accepts for
         * a Retry-After response header with a parameter in delta-seconds.
         */
        public static final int MAX_RETRY_AFTER = 24 * 60 * 60; // 24h
        /**
         * The maximum number of redirects.
         */
        public static final int MAX_REDIRECTS = 5; // can't be more than 7.
        /**
         * The time between a failure and the first retry after an IOException.
         * Each subsequent retry grows exponentially, doubling each time.
         * The time is in seconds.
         */
        public static final int RETRY_FIRST_DELAY = 30;
    }

    public static final class UPLOAD_COLUMNS implements android.provider.BaseColumns {
//        public static final String _DATA = "_data";
        //FileName
        public static final String COLUMN_TARGET_URL = "url";
        public static final String COLUMN_FILE_URI = "uri";
        public static final String COLUMN_UID = "uid";
        public static final String COLUMN_MIME_TYPE = "mimetype";
        public static final String COLUMN_STATUS = "status";
        public static final String COLUMN_NUM_FAILED = "numfailed";
        public static final String COLUMN_RETRY_AFTER = "retryafter";
        public static final String COLUMN_LAST_MODIFICATION = "lastmod";
        public static final String COLUMN_TOTAL_BYTES = "totalbytes";
        public static final String COLUMN_CURRENT_BYTES = "currentbytes";
        //for notification
        public static final String COLUMN_TITLE = "title";
        public static final String COLUMN_DESCRIPTION = "description";
        public static final String COLUMN_DELETED = "deleted";
        public static final String COLUMN_NOTIFICATION_PACKAGE = "notificationpackage";
        public static final String COLUMN_NOTIFICATION_CLASS = "notificationclass";
        public static final String COLUMN_NOTIFICATION_EXTRAS = "notificationextras";
        public static final String COLUMN_VISIBILITY = "visibility";
        public static final String RETRY_AFTER_X_REDIRECT_COUNT = "method";
        public static final String COLUMN_CONTROL = "control";
        public static final String COLUMN_BYPASS_NETWORK_CHANGE = "bypassnetworkchange";
        public static final String COLUMN_ALLOW_ROAMING = "allowroaming";
        public static final String COLUMN_ERROR_MSG = "errormsg";
        public static final String COLUMN_SERVER_RESPONSE = "response";
        public static final String COLOMN_DATA_FIELD_NAME = "datafiled";
        //TODO: check if all columns are initialized in database onCreate() and UploadInfo

    }

    public static final class UPLOAD_URIS {
        public static final String TABLE_NAME = "uploads";
        public static final String UPLOAD_AUTHORITY = "me.ctknight.uploadmanager.uploadprovider";
        public static final Uri UPLOAD_AUTHORITY_URI = Uri.parse(ContentResolver.SCHEME_CONTENT + "://" + UPLOAD_AUTHORITY);
        public static final Uri CONTENT_URI = UPLOAD_AUTHORITY_URI.buildUpon().appendPath(TABLE_NAME).build();
    }

    public static final class UPLOAD_STATUS {

        /*not started yet*/
        public static final int PENDING = 0;
        public static final int RUNNING = 192;

        public static final int WAITING_TO_RETRY = 194;
        public static final int WAITING_FOR_NETWORK = 195;
        public static final int WAITING_FOR_WIFI = 196;

        public static final int SUCCESS = 200;

        public static final int MIN_ARTIFICIAL_ERROR_STATUS = 488;
        public static final int CANNOT_RESUME = 489;
        public static final int CANCELED = 490;
        public static final int UNKNOWN_ERROR = 491;
        public static final int FILE_ERROR = 492;
        public static final int UNHANDLED_REDIRECT = 493;
        public static final int UNHANDLED_HTTP_CODE = 494;
        public static final int HTTP_DATA_ERROR = 495;
        public static final int TOO_MANY_REDIRECTS = 497;
        public static final int FAILED = 504;
        public static final int FILE_NOT_FOUND = 508;
        public static final int DEVICE_NOT_FOUND_ERROR = 599;
    }

    public static final class VISIBILITY_STATUS {
        public static final int VISIBLE = 0;
        public static final int VISIBLE_COMPLETE = 1;
        public static final int HIDDEN_COMPLETE = 2;
        public static final int VISIBLE_ONLY_COMPLETION = 3;
        public static final int HIDDEN = 5;
    }

    public static final class CONTROL {
        public static final int RUN = 0;
        public static final int PAUSED = 1;

    }

    public static final class RequestContent {
        public static final String REQUEST_CONTENT_DB_TABLE = "requestcontent";
        public static final String COLUMN_UPLOAD_ID = "uploadid";
        public static final String COLUMN_HEADER_NAME = "headername";
        public static final String COLUMN_HEADER_VALUE = "headervalue";

        public static final String URI_SEGMENT = "headers";
        public static final String INSERT_KEY_PREFIX = "http_header_";

        public static final String DEFAULT_USER_AGENT = "me.ctknight.uploadmanager";
        //see:https://developer.chrome.com/multidevice/user-agent

        public static final String COLUMN_USER_AGENT = "useragent";
        public static final String COLUMN_REFERER = "referer";

        //Content-Disposition key, value pairs

        // TODO: 2015/11/23 add this in api doc
        public static final String COLUMN_CD_NAME = "cdname";
        public static final String COLUMN_CD_VALUE = "cdvalue";

        public static final String INSERT_CD_PREFIX = "content_disposition_";
        public static final String CD_URI_SEGMENT = "cd";

    }

}
