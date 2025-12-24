# GoPlay Java Client

A WebSocket-based client framework for GoPlay Game Server, implemented in Java.

This is a port of the TypeScript GoPlay client to Java, maintaining the same architecture and API patterns.

## Features

- WebSocket communication with binary protocol
- Protobuf message serialization/deserialization
- Automatic reconnection and heartbeat
- Event-driven architecture (Emitter pattern)
- Request/Response and Push message patterns
- Message chunking for large payloads
- Route encoding/decoding
- Async/await style operations using CompletableFuture

## Project Structure

```
java-goplay/
├── src/main/java/com/goplay/
│   ├── GoPlay.java                 # Main client class
│   ├── core/
│   │   ├── ByteArray.java          # Binary data handling
│   │   ├── Emitter.java            # Event emitter
│   │   ├── Package.java            # Message package
│   │   ├── TaskCompletionSource.java # Async task wrapper
│   │   └── IdGen.java              # ID generator
│   └── encoder/
│       ├── IEncoder.java           # Encoder interface
│       ├── ProtobufEncoder.java    # Protobuf implementation
│       └── EncoderFactory.java     # Encoder factory
├── src/test/java/com/goplay/       # Unit tests
├── pom.xml                         # Maven configuration
└── README.md                       # This file
```

## Dependencies

- Java 8+
- Maven
- java-websocket 1.5.3 - WebSocket client library
- protobuf-java 3.21.0 - Protocol Buffers
- slf4j 1.7.36 - Logging API
- logback 1.2.11 - Logging implementation
- junit 4.13.2 - Unit testing (test scope)

## Installation

1. Add to your Maven project's `pom.xml`:

```xml
<dependency>
    <groupId>com.goplay</groupId>
    <artifactId>goplay-java-client</artifactId>
    <version>0.1.0</version>
</dependency>
```

Or build from source:

```bash
cd java-goplay
mvn clean install
```

## Basic Usage

### Connect to Server

```java
try {
    boolean connected = GoPlay.connect("ws://localhost:8080").get();
    if (connected) {
        System.out.println("Connected to server");
    }
} catch (Exception e) {
    System.err.println("Connection failed: " + e.getMessage());
}
```

### Listen to Events

```java
// Connection events
GoPlay.on(GoPlay.Consts.Events.CONNECTED, (args) -> {
    System.out.println("Server connected");
});

GoPlay.on(GoPlay.Consts.Events.DISCONNECTED, (args) -> {
    System.out.println("Server disconnected");
});

GoPlay.on(GoPlay.Consts.Events.ERROR, (args) -> {
    System.err.println("Error: " + args[0]);
});

GoPlay.on(GoPlay.Consts.Events.KICKED, (args) -> {
    System.out.println("Kicked from server");
});
```

### Send Requests

```java
// Make a request and wait for response
try {
    GoPlay.ResponseResult<YourResponseType> result = 
        GoPlay.request("game.login", loginData, YourResponseType.class).get();
    
    if (result.status == 0) {
        System.out.println("Login successful: " + result.data);
    } else {
        System.err.println("Login failed");
    }
} catch (Exception e) {
    System.err.println("Request error: " + e.getMessage());
}
```

### Send Notifications

```java
// Send notification (no response expected)
GoPlay.notify("game.move", moveData);
```

### Listen to Push Messages

```java
// Register push listener with expected response type
GoPlay.onType("game.update", GameUpdateMessage.class, (args) -> {
    GameUpdateMessage update = (GameUpdateMessage) args[0];
    System.out.println("Game updated: " + update);
});
```

### Disconnect

```java
try {
    GoPlay.disconnect().get();
    System.out.println("Disconnected");
} catch (Exception e) {
    System.err.println("Disconnect error: " + e.getMessage());
}
```

## Configuration

### Set Timeouts

```java
GoPlay.setTimeout("CONNECT", 5000);     // 5 seconds
GoPlay.setTimeout("HEARTBEAT", 3000);   // 3 seconds
GoPlay.setTimeout("REQUEST", 10000);    // 10 seconds
```

### Set Client Version

```java
GoPlay.setClientVersion("MyGame/1.0.0");
```

### Set Server Tag

```java
GoPlay.setServerTag(1); // FrontEnd server
```

### Enable Debug Logging

```java
GoPlay.debug = true;
```

## Core Classes

### ByteArray

Handles binary data operations with little-endian encoding:

```java
ByteArray data = new ByteArray(1024);
data.writeUint8(10);
data.writeUint16(1000);
data.writeUint32(100000);
data.writeString("Hello");
data.writeBytes(otherData);

// Reading
int val8 = data.readUint8();
int val16 = data.readUint16();
long val32 = data.readUint32();
String str = data.readString(5);
byte[] bytes = data.readBytes(10);
```

### Emitter

Event-driven communication:

```java
Emitter emitter = new Emitter();

// Register listener
emitter.on("my-event", (args) -> {
    System.out.println("Event: " + args[0]);
});

// Register one-time listener
emitter.once("my-event", (args) -> {
    System.out.println("First event only: " + args[0]);
});

// Emit event
emitter.emit("my-event", "data");

// Remove listener
emitter.off("my-event");

// Remove all listeners
emitter.removeAllListeners();
```

### Package

Represents a communication package:

```java
// Create from data
Package<?> pkg = Package.create(header, data);

// Encode to bytes
ByteArray bytes = pkg.encode(encodingType);

// Decode from bytes
Package<?> pkg = Package.tryDecodeRaw(bytes, encodingType);
Package<MyType> typedPkg = pkg.decodeFromRaw(MyType.class, encodingType);

// Handle chunking
Package<?>[] chunks = pkg.split(encodingType);
Package<?> merged = Package.join(chunks);
```

### TaskCompletionSource

Async task wrapper for synchronization:

```java
TaskCompletionSource<String> task = new TaskCompletionSource<>();

// In another thread
task.setResult("Done");

// Wait for result
try {
    String result = task.getResult();  // Blocks until result is set
    String resultWithTimeout = task.getResult(5000);  // Wait max 5 seconds
} catch (Exception e) {
    e.printStackTrace();
}
```

## Implementation Notes

1. **Protobuf Integration**: The `Package` and `GoPlay` classes are designed to work with generated protobuf classes. You need to generate Java classes from your `.proto` files using the protobuf compiler.

2. **Header Structure**: The actual header structure and package types depend on your protobuf definitions. The framework provides the infrastructure, but you'll need to implement the specific message handling for your server protocol.

3. **Thread Safety**: Most classes use `ConcurrentHashMap` and `CopyOnWriteArrayList` for thread-safe operations. However, the WebSocket message processing is handled sequentially.

4. **Async Operations**: Connection and request operations return `CompletableFuture` for async handling. You can use `.get()`, `.thenApply()`, `.thenAccept()`, etc.

## Comparison with TypeScript Version

| Feature | TypeScript | Java |
|---------|-----------|------|
| WebSocket | ws/native | Java-WebSocket |
| Protobuf | protobufjs | protobuf-java |
| Async | Promise | CompletableFuture |
| Events | Custom Emitter | Custom Emitter |
| Data Buffer | Uint8Array | ByteArray |
| Threading | Single-threaded | Multi-threaded |

## Building

```bash
# Compile
mvn clean compile

# Run tests
mvn test

# Package
mvn package

# Install to local repository
mvn install
```

## Testing

Unit tests are located in `src/test/java/com/goplay/`:

```bash
mvn test
```

## License

Same as the original TypeScript version.

## Notes for Development

1. The framework needs to be integrated with your actual protobuf message definitions
2. Update the `Package` class header cloning and manipulation methods based on your protobuf schema
3. Implement the `processPackage()` method with your actual package type handling
4. Generate Java protobuf classes using: `protoc --java_out=. your_proto_file.proto`

## Migration Guide from TypeScript

The API is designed to be familiar to TypeScript developers:

| TypeScript | Java |
|-----------|------|
| `goplay.connect(url)` | `GoPlay.connect(url)` |
| `goplay.disconnect()` | `GoPlay.disconnect()` |
| `goplay.send(pack)` | `GoPlay.send(pack)` |
| `goplay.request()` | `GoPlay.request()` |
| `goplay.notify()` | `GoPlay.notify()` |
| `goplay.on(event, fn)` | `GoPlay.on(event, fn)` |
| `goplay.emit(event)` | `GoPlay.emit(event)` |
| `new ByteArray()` | `new ByteArray()` |
| `new Emitter()` | `new Emitter()` |

## Troubleshooting

### Connection Timeout
- Check server address and port
- Verify firewall settings
- Increase timeout: `GoPlay.setTimeout("CONNECT", 10000)`

### Message Encoding Error
- Ensure protobuf classes are generated from the same schema as the server
- Verify the encoding type matches server configuration

### WebSocket Connection Refused
- Check if the server is running
- Verify the WebSocket protocol URL (ws:// or wss://)

## Contributing

Please refer to the original TypeScript project for contribution guidelines.
