
package com.huasuan.utils;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.database.Cursor;


/**
 * @author songzhaochun
 *
 */
public final class IoStreamUtils {

    /**
     * 完全读取，直到缓冲区满，或者文件末尾
     * @param in
     * @param dst
     * @param offset
     * @param count
     * @return 返回当前dst位置
     * @throws IOException
     */
    public static final int readFully(InputStream in, byte[] dst, int offset, int count) throws IOException {
        while (count > 0) {
            final int bytesRead = in.read(dst, offset, count);
            if (bytesRead < 0) {
                return offset;
            }
            offset += bytesRead;
            count -= bytesRead;
        }
        return offset;
    }

    /**
     * @param is
     * @param os
     * @throws IOException
     */
    public static final void copyStream(InputStream is, OutputStream os) throws IOException {
        final byte buffer[] = new byte[4096];
        int rc = 0;
        while ((rc = is.read(buffer)) >= 0) {
            if (rc > 0) {
                os.write(buffer, 0, rc);
            }
        }
    }

    /**
     * @param closeable
     */
    public static final void closeSilently(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (final Throwable e) {
                // ignore
            }
        }
    }

    /**
     * @param in
     */
    public static final void closeSilently(BufferedReader in) {
        if (in != null) {
            try {
                in.close();
            } catch (final Throwable e) {
                // ignore
            }
        }
    }

    /**
     * @param in
     */
    public static final void closeSilently(InputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (final Throwable e) {
                // ignore
            }
        }
    }

    /**
     * @param out
     */
    public static final void closeSilently(OutputStream out) {
        if (out != null) {
            try {
                out.close();
            } catch (final Throwable e) {
                // ignore
            }
        }
    }

    /**
     * @param cursor
     */
    public static final void closeQuietly(Cursor cursor) {
        if (cursor != null) {
            try {
                cursor.close();
            } catch (final Throwable e) {
                // ignore
            }
        }
    }

    /**
     * @param in
     * @return
     * @throws IOException
     */
    public static final String readUTF8(InputStream in) throws IOException {
        final StringBuilder sb = new StringBuilder();
        final byte buffer[] = new byte[4096];
        int rc = 0;
        while ((rc = in.read(buffer)) >= 0) {
            if (rc > 0) {
                sb.append(new String(buffer, 0, rc, "UTF-8"));
            }
        }
        return sb.toString();
    }

    /**
     * @param in
     * @return
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    public static final byte[] readMD5(InputStream in) throws NoSuchAlgorithmException, IOException {
        final MessageDigest digest = MessageDigest.getInstance("MD5");
        final byte buffer[] = new byte[4096];
        int rc = 0;
        while ((rc = in.read(buffer)) >= 0) {
            if (rc > 0) {
                digest.update(buffer, 0, rc);
            }
        }
        return digest.digest();
    }
}
