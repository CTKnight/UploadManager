/*
 * Copyright (c) 2016 Zhang Hai <Dreaming.in.Code.ZH@Gmail.com>
 * All Rights Reserved.
 */

package me.ctknight.uploadmanager.upload;

public class UploadNetworkException extends UploadException {

    public UploadNetworkException() {}

    public UploadNetworkException(String detailMessage) {
        super(detailMessage);
    }

    public UploadNetworkException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public UploadNetworkException(Throwable throwable) {
        super(throwable);
    }
}
