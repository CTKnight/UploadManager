/*
 * Copyright (c) 2016.  All rights reserved. Lai Jiewen <alanljw12345@gmail,com
 */

package me.ctknight.uploadmanager.util;

import android.content.Context;
import android.widget.Toast;

public class ToastUtils {

    public static void show(CharSequence text, int duration, Context context) {
        Toast.makeText(context, text, duration).show();
    }
    public static void show(int resId, int duration, Context context) {
        show(context.getText(resId), duration, context);
    }

    public static void show(CharSequence text, Context context) {
        show(text, Toast.LENGTH_SHORT, context);
    }

    public static void show(int resId, Context context) {
        show(context.getText(resId), context);
    }
}
