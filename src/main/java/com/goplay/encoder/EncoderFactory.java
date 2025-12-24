package com.goplay.encoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EncoderFactory creates and manages encoder instances.
 */
public class EncoderFactory {
    private static final Logger logger = LoggerFactory.getLogger(EncoderFactory.class);

    // Encoding type constants (must match server protobuf definitions)
    // According to TypeScript definitions: Protobuf=0, Json=1
    public static final int PROTOBUF = 0;
    public static final int JSON = 1;

    private static final ProtobufEncoder protobufEncoder = new ProtobufEncoder();

    /**
     * Get encoder for the specified encoding type.
     */
    public static IEncoder getEncoder(int encodingType) throws IllegalArgumentException {
        switch (encodingType) {
            case PROTOBUF:
                return protobufEncoder;
            case JSON:
                throw new IllegalArgumentException("JSON encoding not yet supported");
            default:
                throw new IllegalArgumentException("Unsupported encoding type: " + encodingType);
        }
    }

    /**
     * Get the default protobuf encoder.
     */
    public static ProtobufEncoder getProtobufEncoder() {
        return protobufEncoder;
    }
}
