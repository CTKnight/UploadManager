/*
 * Copyright (c) 2016 Zhang Hai <Dreaming.in.Code.ZH@Gmail.com>
 * All Rights Reserved.
 */

package me.ctknight.uploadmanager.util;

//Copyright(c)2016 Lai Jiewen<alanljw12345@gmail.com>
//        All Rights Reserved.

public class LogUtils {

    private LogUtils() {
    }

    public static String makeTag(Class tClass) {
        return tClass.getSimpleName();
    }
}
