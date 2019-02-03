/*
 * Copyright (c) 2019. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager;

import android.content.ContentResolver;
import android.net.Uri;

import me.ctknight.uploadmanager.UploadInfo;

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
    return ;
  }

  public static final class Constants {


    /**
     * The buffer size used to stream the data
     */

    public static final int BUFFER_SIZE = 8192;
    /**
     * The minimum amount of progress that has to be done before the progress bar gets updated
     */
    public static final int MIN_PROGRESS_STEP = 1024;
    /**
     * The minimum amount of time that has to elapse before the progress bar gets updated, in
     * ms
     */
    public static final long MIN_PROGRESS_TIME = 2000;
    /**
     * The number of times that the upload manager will retry its network
     * operations when no progress is happening before it gives up.
     */
    public static final int MAX_RETRIES = 5;
    /**
     * The minimum amount of time that the upload manager accepts for
     * a Retry-After response header with a parameter in delta-seconds.
     */
    public static final int MIN_RETRY_AFTER = 5; // 5s
    /**
     * The maximum amount of time that the upload manager accepts for
     * a Retry-After response header with a parameter in delta-seconds.
     */
    public static final int MAX_RETRY_AFTER = 5 * 60; // 5 minutes
    /**
     * The maximum number of redirects.
     */
    public static final int MAX_REDIRECTS = 5; // can't be more than 7.
    /**
     * The time between a failure and the first retry after an IOException.
     * Each subsequent retry grows exponentially, doubling each time.
     * The time is in seconds.
     */
    public static final int RETRY_FIRST_DELAY = 5;
  }

  public enum UploadStatus {
    PENDING(0),
    PAUSED(1),
    RUNNING(2),

    WAITING_TO_RETRY(10),
    WAITING_FOR_NETWORK(11),
    WAITING_FOR_WIFI(12),

    SUCCESS(20),

    CAN_NOT_RESUME(40),
    CANCELED(41),
    // HTTP status code is not 2XX,
    HTTP_CODE_ERROR(42),
    HTTP_DATA_ERROR(43),
    /**
     * error when reading file
     */
    FILE_ERROR(44),
    UNKNOWN_ERROR(50),

    FAILED(60);
    private int status;

    UploadStatus(int status) {
      this.status = status;
    }

    public int getStatus() {
      return status;
    }
  }
  public enum Visibility {
    VISIBLE,
    VISIBLE_UNTIL_COMPLETE,
    HIDDEN_UNTIL_COMPLETE,
    HIDDEN
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
