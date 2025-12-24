package com.goplay.encoder;

import com.goplay.core.ByteArray;

/**
 * IEncoder interface for encoding/decoding support.
 */
public interface IEncoder {
    /**
     * Encode an object to ByteArray.
     */
    ByteArray encode(Object obj) throws Exception;

    /**
     * Decode ByteArray to object of specified type.
     */
    <T> T decode(Class<T> type, ByteArray bytes) throws Exception;
}
