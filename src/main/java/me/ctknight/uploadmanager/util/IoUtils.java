/*
 * Copyright (c) 2016.  All rights reserved. Lai Jiewen <alanljw12345@gmail,com
 */

package me.ctknight.uploadmanager.util;

import org.json.JSONArray;
import org.json.JSONException;

import android.util.Base64;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;

public class IoUtils {

    private static final int BUFFER_SIZE = 4 * 1024;

    private static final String STRING_DELIMITER = "|";

    private static final String STRING_DELIMITER_REGEX = "\\|";


    private IoUtils() {
    }

    public static void close(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String inputStreamToString(InputStream inputStream, String charsetName)
            throws IOException {
        StringBuilder builder = new StringBuilder();
        InputStreamReader reader = new InputStreamReader(inputStream, charsetName);
        char[] buffer = new char[BUFFER_SIZE];
        int length;
        while ((length = reader.read(buffer)) != -1) {
            builder.append(buffer, 0, length);
        }
        return builder.toString();
    }

    public static String byteArrayToBase64(byte[] byteArray) {
        // We are using Base64 in Json so we don't want newlines here.
        return Base64.encodeToString(byteArray, Base64.NO_WRAP);
    }

    public static byte[] base64ToByteArray(String base64) {
        return Base64.decode(base64, Base64.DEFAULT);
    }

    // NOTE: This function is null-tolerant, nulls will be printed as "null" (so it will stay "null"
    // instead of null)
    public static String stringArrayToString(String[] strings, String delimiter) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String string : strings) {
            if (first) {
                first = false;
            } else {
                builder.append(delimiter);
            }
            builder.append(string);
        }
        return builder.toString();
    }

    public static String stringArrayToString(String[] strings) {
        return stringArrayToString(strings, STRING_DELIMITER);
    }

    public static String jsonStringArrayToString(JSONArray jsonArray, String delimiter)
            throws JSONException {
        StringBuilder builder = new StringBuilder();
        int numStrings = jsonArray.length();
        for (int i = 0; i < numStrings; ++i) {
            if (i != 0) {
                builder.append(delimiter);
            }
            builder.append(jsonArray.getString(i));
        }
        return builder.toString();
    }

    public static String jsonStringArrayToString(JSONArray jsonArray) throws JSONException {
        return jsonStringArrayToString(jsonArray, STRING_DELIMITER);
    }

    public static <T> String arrayToString(T[] array, Stringifier<T> stringifier,
                                           String delimiter) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (T object : array) {
            if (first) {
                first = false;
            } else {
                builder.append(delimiter);
            }
            builder.append(stringifier.stringify(object));
        }
        return builder.toString();
    }

    public static <T> String arrayToString(T[] array, Stringifier<T> stringifier) {
        return arrayToString(array, stringifier, STRING_DELIMITER);
    }

    public static <T> String collectionToString(Collection<T> collection,
                                                Stringifier<T> stringifier, String delimiter) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (T object : collection) {
            if (first) {
                first = false;
            } else {
                builder.append(delimiter);
            }
            builder.append(stringifier.stringify(object));
        }
        return builder.toString();
    }

    public static <T> String collectionToString(Collection<T> collection,
                                                Stringifier<T> stringifier) {
        return collectionToString(collection, stringifier, STRING_DELIMITER);
    }

    public static <T> String collectionToString(Collection<T> collection) {
        return collectionToString(collection, new Stringifier<T>() {
            @Override
            public String stringify(T object) {
                return object.toString();
            }
        }, STRING_DELIMITER);
    }

    // Consecutive tokens make an empty string; if you want to avoid this, use regex like "\\|+".
    public static String[] stringToStringArray(String string) {
        return stringToStringArray(string, STRING_DELIMITER_REGEX);
    }

    public static String[] stringToStringArray(String string, String delimiterRegex) {
        // String.split() returns the original String if pattern is not found, but we need to return
        // an empty array when the string is empty, instead an array containing the original empty
        // string.
        if (string.isEmpty()) {
            return new String[0];
        } else {
            return string.split(delimiterRegex);
        }
    }


    public interface Stringifier<T> {
        String stringify(T object);
    }
}
