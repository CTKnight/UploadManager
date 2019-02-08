/*
 * Copyright (c) 2019. All rights reserved. Lai Jiewen <alanljw12345@gmail.com>
 */

package me.ctknight.uploadmanager.util;

import android.os.ParcelFileDescriptor;
import android.system.Os;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.internal.Util;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;

public class OkHttpUtils {
  private OkHttpUtils() {
    throw new AssertionError("No constructor for this");
  }

  public static RequestBody createRequestFromFile(final MediaType mediaType, final ParcelFileDescriptor pfd) {
    return new RequestBody() {
      @Override
      public MediaType contentType() {
        return mediaType;
      }

      @Override
      public long contentLength() {
        return pfd.getStatSize();
      }

      @Override
      public void writeTo(BufferedSink sink) throws IOException {
        InputStream inputStream = null;
        try {
          FileDescriptor fd = pfd.getFileDescriptor();
          inputStream = new FileInputStream(fd);
          Source source = Okio.buffer(Okio.source(inputStream));
          sink.writeAll(source);
        } finally {
          Util.closeQuietly(inputStream);
        }
        // not closing the pfd here, because okhttp may try to use this several time (http 308)
      }
    };
  }
}
