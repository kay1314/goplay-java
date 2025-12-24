package com.goplay;

import com.goplay.core.ByteArray;
import com.goplay.core.Package;
import com.goplay.core.protocols.ProtocolProto.*;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the request method implementation.
 */
public class RequestMethodTest {

    @Test
    public void testCreateFromData() {
        // Test that createFromData creates package with Protobuf Header
        Package<?> pack = Package.createFromData(50, "test data", PackageType.Request_VALUE, EncodingType.Protobuf_VALUE);
        
        assertNotNull("Package should not be null", pack);
        assertNotNull("Header should not be null", pack.getHeader());
        assertTrue("Header should be Header instance", pack.getHeader() instanceof Header);
        
        Header header = (Header) pack.getHeader();
        assertNotNull("PackageInfo should not be null", header.getPackageInfo());
        assertEquals("Route should be 50", 50, header.getPackageInfo().getRoute());
        assertEquals("PackageType should be Request", PackageType.Request, header.getPackageInfo().getType());
    }

    @Test
    public void testHeaderCreation() {
        // Test that Header can be created
        PackageInfo pkgInfo = PackageInfo.newBuilder()
                .setRoute(100)
                .setType(PackageType.Request)
                .setEncodingType(EncodingType.Protobuf)
                .setId(1)
                .build();
        
        Header header = Header.newBuilder()
                .setPackageInfo(pkgInfo)
                .build();
        
        assertNotNull("Header should not be null", header);
        assertEquals("Route should be 100", 100, header.getPackageInfo().getRoute());
        assertEquals("ID should be 1", 1, header.getPackageInfo().getId());
    }

    @Test
    public void testPackageInfoFields() {
        // Test PackageInfo fields
        PackageInfo pkgInfo = PackageInfo.newBuilder()
                .setRoute(75)
                .setType(PackageType.Response)
                .setEncodingType(EncodingType.Json)
                .setId(42)
                .setContentSize(256)
                .setChunkCount(2)
                .setChunkIndex(0)
                .build();
        
        assertEquals("Route should be 75", 75, pkgInfo.getRoute());
        assertEquals("Type should be Response", PackageType.Response, pkgInfo.getType());
        assertEquals("ID should be 42", 42, pkgInfo.getId());
        assertEquals("ContentSize should be 256", 256, pkgInfo.getContentSize());
        assertEquals("ChunkCount should be 2", 2, pkgInfo.getChunkCount());
    }

    @Test
    public void testResponseResult() {
        // Test ResponseResult creation
        GoPlay.ResponseResult<String> result = new GoPlay.ResponseResult<>(200, "success");
        
        assertEquals("Status should be 200", 200, result.status);
        assertEquals("Data should be 'success'", "success", result.data);
    }

    @Test
    public void testResponseResultWithNoArgsConstructor() {
        // Test ResponseResult default constructor
        GoPlay.ResponseResult<Object> result = new GoPlay.ResponseResult<>();
        
        assertEquals("Status should be 0", 0, result.status);
        assertNull("Data should be null", result.data);
    }

    @Test
    public void testTimeoutResponseResult() {
        // Test timeout response (status 1000)
        GoPlay.ResponseResult<Void> timeoutResult = new GoPlay.ResponseResult<>(1000, null);
        
        assertEquals("Timeout status should be 1000", 1000, timeoutResult.status);
        assertNull("Timeout data should be null", timeoutResult.data);
    }

    @Test
    public void testErrorResponseResult() {
        // Test error response (status 500)
        GoPlay.ResponseResult<Void> errorResult = new GoPlay.ResponseResult<>(500, null);
        
        assertEquals("Error status should be 500", 500, errorResult.status);
        assertNull("Error data should be null", errorResult.data);
    }

    @Test
    public void testHeaderInfoEncoding() throws Exception {
        // Test that Header can be encoded
        PackageInfo pkgInfo = PackageInfo.newBuilder()
                .setRoute(100)
                .setType(PackageType.Request)
                .setEncodingType(EncodingType.Protobuf)
                .setId(5)
                .build();
        
        Header header = Header.newBuilder()
                .setPackageInfo(pkgInfo)
                .build();
        
        Package<String> pack = new Package<>(header, "test", null);
        
        // Should not throw an exception (use Protobuf encoding type = 0)
        ByteArray encoded = pack.encode(EncodingType.Protobuf_VALUE);
        assertNotNull("Encoded should not be null", encoded);
        assertTrue("Encoded length should be > 0", encoded.getLength() > 0);
    }

    @Test
    public void testResponseResultTypeParameter() {
        // Test that ResponseResult can hold different types
        
        // Test with String
        GoPlay.ResponseResult<String> stringResult = new GoPlay.ResponseResult<>(0, "data");
        assertEquals("String data should match", "data", stringResult.data);
        
        // Test with Integer
        GoPlay.ResponseResult<Integer> intResult = new GoPlay.ResponseResult<>(0, 42);
        assertEquals("Integer data should match", Integer.valueOf(42), intResult.data);
        
        // Test with null data
        GoPlay.ResponseResult<Object> nullResult = new GoPlay.ResponseResult<>(0, null);
        assertNull("Null data should be null", nullResult.data);
    }
}
