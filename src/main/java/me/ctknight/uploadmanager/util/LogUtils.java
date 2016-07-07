/*
 * Copyright (c) 2016.  All rights reserved. Lai Jiewen <alanljw12345@gmail,com
 */

package me.ctknight.uploadmanager.util;

public class LogUtils {

    private LogUtils() {
    }

    public static String makeTag(Class tClass) {
        return tClass.getSimpleName();
    }
}
