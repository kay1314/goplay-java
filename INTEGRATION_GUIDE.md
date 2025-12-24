# GoPlay Java Client - Integration Guide

This guide explains how to integrate the GoPlay Java client into your project and customize it for your specific server protocol.

## Prerequisites

- Java 8 or higher
- Maven 3.6 or higher
- Protocol Buffer compiler (protoc) - if you need to generate Java classes from proto files

## Installation Steps

### 1. Build the GoPlay Library

```bash
cd java-goplay
mvn clean install
```

This will create a JAR file and install it in your local Maven repository.

### 2. Add Dependency to Your Project

In your project's `pom.xml`:

```xml
<dependency>
    <groupId>com.goplay</groupId>
    <artifactId>goplay-java-client</artifactId>
    <version>0.1.0</version>
</dependency>
```

Or if you're not using Maven, copy the JAR file from `target/` to your project's library folder.

## Generating Protocol Buffer Classes

The GoPlay client requires Java classes generated from your protobuf definitions.

### Step 1: Prepare Your Proto Files

Copy your `.proto` files to a directory, e.g., `src/main/proto/`

Example structure:
```
src/main/proto/
├── core.proto
├── game.proto
└── message.proto
```

### Step 2: Generate Java Classes

Option A: Using Maven Plugin (Recommended)

Add to your `pom.xml`:

```xml
<plugin>
    <groupId>org.xolstice.maven.plugins</groupId>
    <artifactId>protobuf-maven-plugin</artifactId>
    <version>0.6.1</version>
    <configuration>
        <protocArtifact>com.google.protobuf:protoc:3.21.0:exe:${os.detected.classifier}</protocArtifact>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>compile</goal>
            </goals>
        </execution>
    </executions>
</plugin>

<plugin>
    <groupId>kr.motd.maven</groupId>
    <artifactId>os-maven-plugin</artifactId>
    <version>1.7.1</version>
    <executions>
        <execution>
            <phase>initialize</phase>
            <goals>
                <goal>detect</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Then run:
```bash
mvn protobuf:compile
```

Option B: Using Command Line

```bash
protoc --java_out=src/main/java src/main/proto/core.proto
protoc --java_out=src/main/java src/main/proto/game.proto
```

The generated classes will be placed in `src/main/java` following your package structure.

## Customization

### 1. Update Package Processing

In `src/main/java/com/goplay/GoPlay.java`, implement the `processPackage()` method:

```java
private static void processPackage(Package<?> pack) {
    Object header = pack.getHeader();
    
    // Get package type from header
    // int type = header.getPackageInfo().getType();
    
    switch (type) {
        case 1:  // Response
            onResponse(pack);
            break;
        case 2:  // Push
            onPush(pack);
            break;
        case 4:  // Ping
            sendPong(pack);
            break;
        case 5:  // Pong
            onPong(pack);
            break;
        case 6:  // HandshakeResp
            onHandshake(pack);
            break;
        case 7:  // Kick
            onKick(pack);
            break;
        default:
            logger.warn("Unknown package type: {}", type);
    }
}
```

### 2. Implement Handshake

Update the `sendHandshake()` and `onHandshake()` methods:

```java
private static void sendHandshake() {
    try {
        // Create handshake request message
        YourProto.ReqHandshake.Builder builder = YourProto.ReqHandshake.newBuilder();
        builder.setClientVersion(Consts.Info.ClientVersion);
        builder.setServerTag(Consts.Info.ServerTag);
        
        YourProto.ReqHandshake handshake = builder.build();
        
        // Send as package
        // int route = 0;  // Handshake route
        // int type = 6;   // HandshakeReq type
        // Package<?> pack = Package.createFromData(route, handshake, type, getEncodingType());
        // send(pack);
    } catch (Exception e) {
        logger.error("Error sending handshake", e);
    }
}

private static void onHandshake(Package<?> pack) {
    try {
        // Decode handshake response
        Package<YourProto.RespHandshake> decoded = 
            pack.decodeFromRaw(YourProto.RespHandshake.class, getEncodingType());
        
        handShake = decoded.getData();
        
        // Extract routes and heartbeat interval from handshake
        // Start heartbeat
        HeartBeat.start();
        
        // Emit connected event
        emit(Consts.Events.CONNECTED);
        
        // Complete connection task
        if (connectTask != null) {
            connectTask.setResult(true);
        }
    } catch (Exception e) {
        logger.error("Error processing handshake", e);
        if (connectTask != null) {
            connectTask.setException(e);
        }
    }
}
```

### 3. Implement Response Handling

```java
private static void onResponse(Package<?> pack) {
    // Get route and ID from header
    // String key = getCallbackKey(pack.getHeader());
    // Class<?> type = requestMap.get(key);
    
    if (type != null) {
        requestMap.remove(key);
        try {
            Package<?> decoded = pack.decodeFromRaw(type, getEncodingType());
            ResponseResult<?> result = new ResponseResult<>(
                getStatus(pack.getHeader()),
                decoded.getData()
            );
            emit(key, result);
        } catch (Exception e) {
            ResponseResult<?> result = new ResponseResult<>(500, null);
            emit(key, result);
        }
    }
}
```

### 4. Implement Push Message Handling

```java
private static void onPush(Package<?> pack) {
    // Get route from header
    // String route = getRoute(pack.getHeader());
    // String key = getPushKey(pack.getHeader());
    
    Class<?> type = pushMap.get(route);
    if (type != null) {
        try {
            Package<?> decoded = pack.decodeFromRaw(type, getEncodingType());
            emit(key, decoded.getData());
        } catch (Exception e) {
            emit(key, pack.getRawData());
        }
    } else {
        emit(key, pack.getRawData());
    }
}
```

### 5. Update Route Encoding/Decoding

```java
private static int getRouteEncoded(String route) {
    if (handShake == null) return 0;
    
    // Get routes map from handshake
    // Map<String, Integer> routes = handShake.getRoutes();
    
    return routes.getOrDefault(route, 0);
}

private static String getRoute(int encodedRoute) {
    if (handShake == null) return "";
    
    // Get routes map from handshake
    // Map<String, Integer> routes = handShake.getRoutes();
    
    for (Map.Entry<String, Integer> entry : routes.entrySet()) {
        if (entry.getValue() == encodedRoute) {
            return entry.getKey();
        }
    }
    
    return "";
}
```

## Usage Example

After customization, here's how to use the client:

```java
public class GameClient {
    public static void main(String[] args) throws Exception {
        // Configure
        GoPlay.setClientVersion("MyGame/1.0.0");
        GoPlay.debug = true;
        
        // Register event listeners
        GoPlay.on(GoPlay.Consts.Events.CONNECTED, (args) -> {
            System.out.println("Connected to game server");
        });
        
        GoPlay.on(GoPlay.Consts.Events.DISCONNECTED, (args) -> {
            System.out.println("Disconnected from game server");
        });
        
        // Register push listeners
        GoPlay.onType("game.playerMove", PlayerMoveMessage.class, (args) -> {
            PlayerMoveMessage msg = (PlayerMoveMessage) args[0];
            handlePlayerMove(msg);
        });
        
        // Connect
        boolean connected = GoPlay.connect("ws://game-server:8080").get();
        
        if (connected) {
            // Make a login request
            LoginRequest loginReq = LoginRequest.newBuilder()
                .setUsername("player123")
                .setPassword("secret")
                .build();
            
            GoPlay.ResponseResult<LoginResponse> result = 
                GoPlay.request("game.login", loginReq, LoginResponse.class).get();
            
            if (result.status == 0) {
                System.out.println("Login success: " + result.data.getUserId());
            }
            
            // Keep running
            Thread.sleep(Long.MAX_VALUE);
        }
    }
    
    private static void handlePlayerMove(PlayerMoveMessage msg) {
        System.out.println("Player " + msg.getPlayerId() + 
                         " moved to (" + msg.getX() + ", " + msg.getY() + ")");
    }
}
```

## Troubleshooting

### Proto File Compilation Issues

```bash
# Check protoc version
protoc --version

# Compile with verbose output
mvn protobuf:compile -X
```

### Maven Build Issues

```bash
# Clear Maven cache
mvn clean

# Download dependencies
mvn dependency:resolve

# Build with debug info
mvn -X clean install
```

### Runtime Issues

1. **ClassNotFoundException for protobuf classes**
   - Ensure proto files were compiled
   - Check package names match in Java code

2. **Connection timeout**
   - Verify server address and port
   - Check firewall settings
   - Increase timeout: `GoPlay.setTimeout("CONNECT", 10000)`

3. **Message encoding errors**
   - Verify proto definitions match server
   - Check message versions compatibility

## Performance Tuning

### Thread Pool Configuration

Modify `GoPlay.java` to adjust thread pool size:

```java
private static ScheduledExecutorService scheduler = 
    Executors.newScheduledThreadPool(4);  // Increase thread count
```

### Buffer Size

Adjust in `ByteArray.java`:

```java
private void ensureCapacity(int minCapacity) {
    if (minCapacity > data.length) {
        // Adjust growth factor (currently 2x)
        byte[] newData = new byte[Math.max(data.length * 3, minCapacity)];
        System.arraycopy(data, 0, newData, 0, data.length);
        data = newData;
    }
}
```

## Security Considerations

1. Use WSS (WebSocket Secure) for production:
   ```java
   GoPlay.connect("wss://secure-server:8080")
   ```

2. Validate server certificates (if using SSL)

3. Never log sensitive data in debug mode

4. Implement proper authentication/authorization

## Next Steps

1. Generate your protobuf classes from `.proto` files
2. Customize the GoPlay class for your specific protocol
3. Implement message handlers for your game logic
4. Test with your server
5. Deploy and monitor

For more information, refer to:
- [Protocol Buffers Documentation](https://developers.google.com/protocol-buffers)
- [Java-WebSocket Documentation](https://github.com/TooTallNate/Java-WebSocket)
- [Maven Protobuf Plugin](https://www.xolstice.org/protobuf-maven-plugin/)
