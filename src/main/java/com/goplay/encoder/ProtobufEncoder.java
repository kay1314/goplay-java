package com.goplay.encoder;

import com.goplay.core.ByteArray;
import com.google.protobuf.Message;
import com.google.protobuf.Message.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * ProtobufEncoder implements protobuf encoding/decoding.
 * Works with protobuf-generated Java classes.
 */
public class ProtobufEncoder implements IEncoder {
    private static final Logger logger = LoggerFactory.getLogger(ProtobufEncoder.class);

    @Override
    public ByteArray encode(Object obj) throws Exception {
        if (obj == null) {
            return null;
        }

        if (!(obj instanceof Message)) {
            throw new IllegalArgumentException("Object must be a protobuf Message");
        }

        Message message = (Message) obj;
        return new ByteArray(message.toByteArray());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T decode(Class<T> type, ByteArray bytes) throws Exception {
        if (!Message.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException("Type must be a protobuf Message class");
        }

        try {
            // Get the parseFrom method from the protobuf class
            Method parseFromMethod = type.getMethod("parseFrom", byte[].class);
            return (T) parseFromMethod.invoke(null, bytes.getData());
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Type does not have a parseFrom method: " + type.getName(), e);
        }
    }

    /**
     * Decode with specific bytes
     */
    @SuppressWarnings("unchecked")
    public <T> T decode(Class<T> type, byte[] bytes) throws Exception {
        if (!Message.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException("Type must be a protobuf Message class");
        }

        try {
            Method parseFromMethod = type.getMethod("parseFrom", byte[].class);
            return (T) parseFromMethod.invoke(null, bytes);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Type does not have a parseFrom method: " + type.getName(), e);
        }
    }
}
