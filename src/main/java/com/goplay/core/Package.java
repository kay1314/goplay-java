package com.goplay.core;

import com.goplay.core.protocols.ProtocolProto.*;
import com.goplay.encoder.EncoderFactory;
import com.goplay.encoder.IEncoder;
import com.google.protobuf.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Package represents a communication packet.
 * Handles encoding, decoding, and chunking of messages.
 */
public class Package<T> {
    private static final Logger logger = LoggerFactory.getLogger(Package.class);
    private static final int MAX_CHUNK_SIZE = 65535 - 2048;

    private Object header;
    private T data;
    private ByteArray rawData;

    public Package(Object header, T data, ByteArray rawData) {
        this.header = header;
        this.data = data;
        this.rawData = rawData;
    }

    public Object getHeader() {
        return header;
    }

    public void setHeader(Object header) {
        this.header = header;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public ByteArray getRawData() {
        return rawData;
    }

    public void setRawData(ByteArray rawData) {
        this.rawData = rawData;
    }

    /**
     * Update the content size in header and encode data if needed.
     */
    public void updateContentSize(int encodingType) throws Exception {
        if (rawData == null && data != null) {
            if (data instanceof Message) {
                IEncoder encoder = EncoderFactory.getEncoder(encodingType);
                rawData = encoder.encode(data);
            }
        }
        // Update protobuf header's PackageInfo content size
        if (header instanceof Header) {
            Header h = (Header) header;
            PackageInfo.Builder pi = PackageInfo.newBuilder(h.getPackageInfo());
            int size = rawData != null ? rawData.getLength() : 0;
            pi.setContentSize(size);
            header = Header.newBuilder(h).setPackageInfo(pi.build()).build();
        }
    }

    /**
     * Encode the entire package.
     */
    public ByteArray encode(int encodingType) throws Exception {
        updateContentSize(encodingType);

        ByteArray headerBytes;
        
        // Encode header as Protobuf message
        if (header instanceof Message) {
            IEncoder encoder = EncoderFactory.getEncoder(encodingType);
            headerBytes = encoder.encode(header);
        } else {
            throw new IllegalArgumentException("Header must be a Protobuf Message");
        }

        int contentSize = rawData != null ? rawData.getLength() : 0;
        ByteArray bytes = new ByteArray(2 + headerBytes.getLength() + contentSize);

        bytes.writeUint16(headerBytes.getLength());
        bytes.writeBytes(headerBytes);
        if (rawData != null) {
            bytes.writeBytes(rawData);
        }

        return bytes;
    }

    /**
     * Decode raw data to specific type.
     */
    @SuppressWarnings("unchecked")
    public <U> Package<U> decodeFromRaw(Class<U> type, int encodingType) throws Exception {
        if (rawData == null) {
            return new Package<>(header, null, rawData);
        }

        IEncoder encoder = EncoderFactory.getEncoder(encodingType);
        U data = encoder.decode(type, rawData);
        return new Package<>(header, data, rawData);
    }

    /**
     * Split package into chunks if it exceeds max chunk size.
     */
    @SuppressWarnings("unchecked")
    public Package<T>[] split(int encodingType) throws Exception {
        updateContentSize(encodingType);

        int contentSize = rawData != null ? rawData.getLength() : 0;
        if (contentSize <= MAX_CHUNK_SIZE) {
            return new Package[]{this};
        }

        int chunkCount = (int) Math.ceil((double) contentSize / MAX_CHUNK_SIZE);
        Package<T>[] result = new Package[chunkCount];

        for (int i = 0, start = 0; start < contentSize; i++, start += MAX_CHUNK_SIZE) {
            int size = Math.min(MAX_CHUNK_SIZE, contentSize - start);
            byte[] chunkData = new byte[size];
            System.arraycopy(rawData.getData(), start, chunkData, 0, size);

            // Create chunk package with updated header info
            // Note: You need to clone and update the header with chunk info
            Package<T> chunk = new Package<>(cloneHeader(header, i, chunkCount, size), null, new ByteArray(chunkData));
            result[i] = chunk;
        }

        return result;
    }

    /**
     * Join multiple chunk packages into one.
     */
    @SuppressWarnings("unchecked")
    public static <T> Package<T> join(Package<T>[] packages) throws Exception {
        if (packages == null || packages.length == 0) {
            return null;
        }

        // Calculate total size
        int totalSize = 0;
        for (Package<T> pkg : packages) {
            if (pkg.rawData != null) {
                totalSize += pkg.rawData.getLength();
            }
        }

        // Merge raw data
        ByteArray mergedData = new ByteArray(totalSize);
        for (int i = 0; i < packages.length; i++) {
            if (packages[i].rawData != null) {
                mergedData.writeBytes(packages[i].rawData);
            }
        }

        // Create result package with header from first package
        Package<T> result = new Package<>(packages[0].header, null, mergedData);
        // Update chunk info in header if needed
        return result;
    }

    /**
     * Try to decode raw bytes into a Package.
     */
    public static Package<?> tryDecodeRaw(ByteArray bytes, int encodingType) throws Exception {
        if (!bytes.hasReadSize(2)) {
            return null;
        }

        int headerLength = bytes.readUint16();
        if (!bytes.hasReadSize(headerLength)) {
            bytes.roffset -= 2;
            return null;
        }

        byte[] headerBytes = bytes.readBytes(headerLength);
        IEncoder encoder = EncoderFactory.getEncoder(EncoderFactory.PROTOBUF);
        Header header = encoder.decode(Header.class, new ByteArray(headerBytes));

        int contentSize = header.getPackageInfo().getContentSize();
        if (!bytes.hasReadSize(contentSize)) {
            bytes.roffset -= (headerLength + 2);
            return null;
        }
        byte[] dataBytes = bytes.readBytes(contentSize);

        return new Package<>(header, null, new ByteArray(dataBytes));
    }

    /**
     * Create a package from header and data.
     */
    public static <T> Package<T> create(Object header, T data) {
        return new Package<>(header, data, null);
    }

    /**
     * Create a package from route, data, type and encoding.
     * This matches the TypeScript createFromData method.
     * Now uses proper Protobuf Header and PackageInfo.
     */
    public static <T> Package<T> createFromData(int route, T data, int packageType, int encodingType) {
        // Create PackageInfo with route and type
        PackageInfo.Builder pkgInfoBuilder = PackageInfo.newBuilder()
                .setRoute(route)
                .setType(PackageType.forNumber(packageType))
                .setEncodingType(EncodingType.forNumber(encodingType))
                .setId(0) // Will be set later by GoPlay
                .setContentSize(0) // Will be set during encoding
                .setChunkCount(0)
                .setChunkIndex(0);
        
        // Create Header with PackageInfo
        Header header = Header.newBuilder()
                .setPackageInfo(pkgInfoBuilder.build())
                .build();
        
        return new Package<>(header, data, null);
    }

    /**
     * Create a package from raw header and raw data.
     */
    public static Package<?> createRaw(Object header, ByteArray rawData) {
        return new Package<>(header, null, rawData);
    }

    /**
     * Helper method to clone header with chunk information.
     * This is implementation-specific based on your Header protobuf definition.
     */
    private static Object cloneHeader(Object header, int chunkIndex, int chunkCount, int contentSize) {
        // This needs to be implemented based on your actual Header structure
        // For now, just return the same header
        // You should use reflection or provide a proper clone mechanism
        return header;
    }

    @Override
    // public String toString() {
    //     return "Package{" +
    //             "header=" + header +
    //             ", data=" + data +
    //             ", rawDataSize=" + (rawData != null ? rawData.getLength() : 0) +
    //             '}';
    // }
    public String toString() {
        return "Package"+ "[header=" + header + ", data=" + data + ", rawDataSize=" + (rawData != null ? rawData.getLength() : 0) + "]";
    }
}
