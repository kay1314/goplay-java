package com.goplay.example;

import com.goplay.core.ByteArray;

/**
 * Example of using ByteArray for binary data operations.
 */
public class ByteArrayExample {
    public static void main(String[] args) {
        // Create a ByteArray
        ByteArray array = new ByteArray(1024);

        // Write various data types
        array.writeUint8(42);
        array.writeUint16(1000);
        array.writeUint32(100000);
        array.writeString("Hello GoPlay");
        array.writeBytes(new byte[]{1, 2, 3, 4, 5});

        System.out.println("Written " + array.woffset + " bytes");

        // Read back the data
        array.roffset = 0;  // Reset read offset

        int val8 = array.readUint8();
        int val16 = array.readUint16();
        long val32 = array.readUint32();
        String str = array.readString("Hello GoPlay".length());
        byte[] bytes = array.readBytes(5);

        System.out.println("Read values:");
        System.out.println("  uint8: " + val8);
        System.out.println("  uint16: " + val16);
        System.out.println("  uint32: " + val32);
        System.out.println("  string: " + str);
        System.out.println("  bytes: " + java.util.Arrays.toString(bytes));

        // Test string encoding/decoding
        String testStr = "Hello 世界 🌍";
        byte[] encoded = ByteArray.strEncode(testStr);
        String decoded = ByteArray.strDecode(encoded);

        System.out.println("\nString encoding/decoding:");
        System.out.println("  original: " + testStr);
        System.out.println("  decoded: " + decoded);
        System.out.println("  match: " + testStr.equals(decoded));
    }
}
