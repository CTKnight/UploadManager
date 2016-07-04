/*
 * Copyright (c) 2016 Zhang Hai <Dreaming.in.Code.ZH@Gmail.com>
 * All Rights Reserved.
 */

package me.ctknight.uploadmanager;

public class UploadException extends Exception {

    public UploadException() {}

    public UploadException(String detailMessage) {
        super(detailMessage);
    }

    public UploadException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public UploadException(Throwable throwable) {
        super(throwable);
    }
}
