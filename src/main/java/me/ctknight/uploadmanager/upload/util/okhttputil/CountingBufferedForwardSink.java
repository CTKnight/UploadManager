/*
 * Copyright (c) 2016 Zhang Hai <Dreaming.in.Code.ZH@Gmail.com>
 * All Rights Reserved.
 */

package me.ctknight.uploadmanager.upload.util.okhttputil;/* Copyright (c) 2016 Lai Jiewen <alanljw12345@gmail.com>
All Rights Reserved. */

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

import okio.Buffer;
import okio.BufferedSink;
import okio.ByteString;
import okio.Sink;
import okio.Source;
import okio.Timeout;

public class CountingBufferedForwardSink implements BufferedSink, Sink {
    private final CountingCallback callback;
    private long total;
    private long written = 0;
    private BufferedSink delegate;

    public CountingBufferedForwardSink(BufferedSink sink, CountingCallback callback) {
        this.delegate = sink;
        this.callback = callback;
    }

    @Override
    public void write(Buffer source, long byteCount) throws IOException {
        delegate.write(source, byteCount);
        delegate.flush();
        written += byteCount;
        callback.notifyWritten(written);
    }

    /**
     * Pushes all buffered bytes to their final destination and releases the
     * resources held by this sink. It is an error to write a closed sink. It is
     * safe to close a sink more than once.
     */
    @Override
    public void close() throws IOException {
        delegate.close();
    }

    /** Returns the timeout for this sink. */
    @Override
    public Timeout timeout() {
        return delegate.timeout();
    }

    /** Pushes all buffered bytes to their final destination. */
    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    /** Returns this sink's internal buffer. */
    @Override
    public Buffer buffer() {
        return delegate.buffer();
    }

    @Override
    public BufferedSink write(ByteString byteString) throws IOException {
        delegate.flush();
        written += byteString.size();
        callback.notifyWritten(written);
        return delegate.write(byteString);
    }

    /**
     * Like {@link OutputStream#write(byte[], int, int)}, this writes {@code byteCount}
     * bytes of {@code source}, starting at {@code offset}.
     */
    @Override
    public BufferedSink write(byte[] source, int offset, int byteCount) throws IOException {
        delegate.flush();
        written += byteCount;
        callback.notifyWritten(written);
        return delegate.write(source, offset, byteCount);
    }

    /**
     * Removes all bytes from {@code source} and appends them to this sink. Returns the
     * number of bytes read which will be 0 if {@code source} is exhausted.
     */
    @Override
    public long writeAll(Source source) throws IOException {
        return delegate.writeAll(source);
    }

    /**
     * Like {@link OutputStream#write(byte[])}, this writes a complete byte array to
     * this sink.
     */
    @Override
    public BufferedSink write(byte[] source) throws IOException {
        delegate.flush();
        written += source.length;
        callback.notifyWritten(written);
        return delegate.write(source);
    }

    /** Returns an output stream that writes to this sink. */
    @Override
    public OutputStream outputStream() {
        return delegate.outputStream();
    }

    /**
     * Writes all buffered data to the underlying sink, if one exists. Like {@link #flush}, but
     * weaker. Call this before this buffered sink goes out of scope so that its data can reach its
     * destination.
     */
    @Override
    public BufferedSink emit() throws IOException {
        return delegate.emit();
    }

    /**
     * Writes complete segments to the underlying sink, if one exists. Like {@link #flush}, but
     * weaker. Use this to limit the memory held in the buffer to a single segment.
     */
    @Override
    public BufferedSink emitCompleteSegments() throws IOException {
        return delegate.emitCompleteSegments();
    }

    /**
     * Writes a long to this sink in hexadecimal form (i.e., as a string in base 16).
     */
    @Override
    public BufferedSink writeHexadecimalUnsignedLong(long v) throws IOException {
        return delegate.writeHexadecimalUnsignedLong(v);
    }

    /**
     * Writes a long to this sink in signed decimal form (i.e., as a string in base 10).
     */
    @Override
    public BufferedSink writeDecimalLong(long v) throws IOException {
        return delegate.writeDecimalLong(v);
    }

    /**
     * Writes a big-endian long to this sink using eight bytes.
     */
    @Override
    public BufferedSink writeLong(long v) throws IOException {
        return delegate.writeLong(v);
    }

    /**
     * Writes a little-endian long to this sink using eight bytes.
     */
    @Override
    public BufferedSink writeLongLe(long v) throws IOException {
        return delegate.writeLongLe(v);
    }

    /**
     * Writes a little-endian int to this sink using four bytes.
     */
    @Override
    public BufferedSink writeIntLe(int i) throws IOException {
        return delegate.writeIntLe(i);
    }

    /**
     * Writes a big-endian int to this sink using four bytes.
     */
    @Override
    public BufferedSink writeInt(int i) throws IOException {
        return delegate.writeInt(i);
    }

    /**
     * Writes a little-endian short to this sink using two bytes.
     */
    @Override
    public BufferedSink writeShortLe(int s) throws IOException {
        return delegate.writeShortLe(s);
    }

    /**
     * Writes a big-endian short to this sink using two bytes.
     */
    @Override
    public BufferedSink writeShort(int s) throws IOException {
        return delegate.writeShort(s);
    }

    /**
     * Writes a byte to this sink.
     */
    @Override
    public BufferedSink writeByte(int b) throws IOException {
        return delegate.writeByte(b);
    }

    /**
     * Encodes the characters at {@code beginIndex} up to {@code endIndex} from {@code string} in
     * {@code charset} and writes it to this sink.
     */
    @Override
    public BufferedSink writeString(String string, int beginIndex, int endIndex, Charset charset) throws IOException {
        return delegate.writeString(string, beginIndex, endIndex, charset);
    }

    /**
     * Encodes {@code string} in {@code charset} and writes it to this sink.
     */
    @Override
    public BufferedSink writeString(String string, Charset charset) throws IOException {
        return delegate.writeString(string, charset);
    }

    /**
     * Encodes {@code codePoint} in UTF-8 and writes it to this sink.
     */
    @Override
    public BufferedSink writeUtf8CodePoint(int codePoint) throws IOException {
        return delegate.writeUtf8CodePoint(codePoint);
    }

    /**
     * Encodes the characters at {@code beginIndex} up to {@code endIndex} from {@code string} in
     * UTF-8 and writes it to this sink.
     */
    @Override
    public BufferedSink writeUtf8(String string, int beginIndex, int endIndex) throws IOException {
        return delegate.writeUtf8(string, beginIndex, endIndex);
    }

    /**
     * Encodes {@code string} in UTF-8 and writes it to this sink.
     */
    @Override
    public BufferedSink writeUtf8(String string) throws IOException {
        return delegate.writeUtf8(string);
    }

    /**
     * Removes {@code byteCount} bytes from {@code source} and appends them to this sink.
     */
    @Override
    public BufferedSink write(Source source, long byteCount) throws IOException {
        delegate.flush();
        written += byteCount;
        callback.notifyWritten(written);
        return delegate.write(source, byteCount);
    }

    public long getWritten(long written) {
        return written;
    }

    public interface CountingCallback {
        void notifyWritten(long written);
    }
}
