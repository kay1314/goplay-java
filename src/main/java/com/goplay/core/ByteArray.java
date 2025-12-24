package com.goplay.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * ByteArray provides byte-level read and write operations.
 * Supports reading/writing various data types in little-endian format.
 */
public class ByteArray {
    private byte[] data;
    public int woffset = 0;  // write offset
    public int roffset = 0;  // read offset

    public ByteArray(int capacity) {
        this.data = new byte[capacity];
    }

    public ByteArray(byte[] data) {
        this.data = data.clone();
        this.woffset = this.data.length;
    }

    public ByteArray(ByteArray other) {
        this.data = other.data.clone();
        this.woffset = other.woffset;
        this.roffset = other.roffset;
    }

    public ByteArray(ByteBuffer buffer) {
        ByteBuffer dup = buffer.slice();
        int len = dup.remaining();
        this.data = new byte[len];
        dup.get(this.data);
        this.woffset = this.data.length;
    }

    public byte[] getData() {
        return data;
    }

    public int getLength() {
        return data.length;
    }

    public ByteBuffer getBuffer() {
        return ByteBuffer.wrap(data);
    }

    public byte[] slice(int start, int end) {
        byte[] result = new byte[end - start];
        System.arraycopy(data, start, result, 0, end - start);
        return result;
    }

    public ByteArray writeUint8(int val) {
        ensureCapacity(woffset + 1);
        data[woffset++] = (byte) (val & 0xff);
        return this;
    }

    public ByteArray writeUint16(int val) {
        ensureCapacity(woffset + 2);
        data[woffset++] = (byte) (val & 0xff);
        data[woffset++] = (byte) ((val >> 8) & 0xff);
        return this;
    }

    public ByteArray writeUint32(long val) {
        ensureCapacity(woffset + 4);
        data[woffset++] = (byte) (val & 0xff);
        data[woffset++] = (byte) ((val >> 8) & 0xff);
        data[woffset++] = (byte) ((val >> 16) & 0xff);
        data[woffset++] = (byte) ((val >> 24) & 0xff);
        return this;
    }

    public ByteArray writeString(String val) {
        if (val == null || val.length() == 0) {
            return this;
        }
        byte[] bytes = strEncode(val);
        return writeBytes(bytes);
    }

    public ByteArray writeBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return this;
        }
        ensureCapacity(woffset + bytes.length);
        System.arraycopy(bytes, 0, data, woffset, bytes.length);
        woffset += bytes.length;
        return this;
    }

    public ByteArray writeBytes(ByteArray bytes) {
        if (bytes == null || bytes.getLength() == 0) {
            return this;
        }
        return writeBytes(bytes.data);
    }

    public int readUint8() {
        if (roffset + 1 > getLength()) {
            return -1;
        }
        return data[roffset++] & 0xff;
    }

    public int readUint16() {
        int l = readUint8();
        int h = readUint8();
        if (l == -1 || h == -1) {
            return -1;
        }
        return (h << 8) | l;
    }

    public long readUint32() {
        int l = readUint16();
        int h = readUint16();
        if (l == -1 || h == -1) {
            return -1;
        }
        return ((long) h << 16) | (l & 0xffffffffL);
    }

    public byte[] readBytes(int len) {
        if (len <= 0) {
            return null;
        }
        if (roffset + len > getLength()) {
            return null;
        }
        byte[] result = new byte[len];
        System.arraycopy(data, roffset, result, 0, len);
        roffset += len;
        return result;
    }

    public String readString(int len) {
        byte[] bytes = readBytes(len);
        if (bytes == null) {
            return "";
        }
        return strDecode(bytes);
    }

    public boolean hasReadSize(int len) {
        return len <= getLength() - roffset;
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity > data.length) {
            byte[] newData = new byte[Math.max(data.length * 2, minCapacity)];
            System.arraycopy(data, 0, newData, 0, data.length);
            data = newData;
        }
    }

    /**
     * Encode string to UTF-8 bytes (matches TypeScript strencode)
     */
    public static byte[] strEncode(String str) {
        ByteArray byteArray = new ByteArray(str.length() * 3);
        int offset = 0;

        for (int i = 0; i < str.length(); i++) {
            int charCode = str.charAt(i);
            byte[] codes;

            if (charCode <= 0x7f) {
                codes = new byte[]{(byte) charCode};
            } else if (charCode <= 0x7ff) {
                codes = new byte[]{
                    (byte) (0xc0 | (charCode >> 6)),
                    (byte) (0x80 | (charCode & 0x3f))
                };
            } else {
                codes = new byte[]{
                    (byte) (0xe0 | (charCode >> 12)),
                    (byte) (0x80 | ((charCode & 0xfc0) >> 6)),
                    (byte) (0x80 | (charCode & 0x3f))
                };
            }

            for (byte code : codes) {
                byteArray.data[offset++] = code;
            }
        }

        ByteArray result = new ByteArray(offset);
        System.arraycopy(byteArray.data, 0, result.data, 0, offset);
        return result.data;
    }

    /**
     * Decode UTF-8 bytes to string (matches TypeScript strdecode)
     */
    public static String strDecode(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        int offset = 0;

        while (offset < bytes.length) {
            int charCode;
            if ((bytes[offset] & 0x80) == 0) {
                charCode = bytes[offset];
                offset += 1;
            } else if ((bytes[offset] & 0xe0) == 0xc0) {
                charCode = ((bytes[offset] & 0x3f) << 6) + (bytes[offset + 1] & 0x3f);
                offset += 2;
            } else {
                charCode = ((bytes[offset] & 0x0f) << 12) +
                        ((bytes[offset + 1] & 0x3f) << 6) +
                        (bytes[offset + 2] & 0x3f);
                offset += 3;
            }
            result.append((char) charCode);
        }

        return result.toString();
    }

    public static byte[] copyArray(ByteArray dest, int doffset, ByteArray src, int soffset, int length) {
        byte[] result = new byte[Math.max(dest.getLength(), doffset + length)];
        System.arraycopy(dest.data, 0, result, 0, dest.getLength());
        System.arraycopy(src.data, soffset, result, doffset, length);
        return result;
    }
}
