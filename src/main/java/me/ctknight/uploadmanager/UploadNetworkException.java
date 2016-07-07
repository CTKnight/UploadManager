/*
 * Copyright (c) 2016.  All rights reserved. Lai Jiewen <alanljw12345@gmail,com
 */

package me.ctknight.uploadmanager;

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
