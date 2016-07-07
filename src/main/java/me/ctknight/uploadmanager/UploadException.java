/*
 * Copyright (c) 2016. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
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
