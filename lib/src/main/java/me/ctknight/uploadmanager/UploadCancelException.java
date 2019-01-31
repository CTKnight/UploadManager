/*
 * Copyright (c) 2018. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager;

/**
 * Created by laikitman on 7/6/17.
 */

public class UploadCancelException extends UploadException {
    public UploadCancelException() {
        super();
    }

    public UploadCancelException(String detailMessage) {
        super(detailMessage);
    }

    public UploadCancelException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public UploadCancelException(Throwable throwable) {
        super(throwable);
    }
}
