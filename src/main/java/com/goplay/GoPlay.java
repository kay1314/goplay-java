package com.goplay;

import com.goplay.core.*;
import com.goplay.core.Package;
import com.goplay.core.protocols.ProtocolProto.*;
import com.goplay.encoder.EncoderFactory;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;

/**
 * GoPlay is the main WebSocket client framework.
 * Handles connection, message sending/receiving, heartbeat, and event dispatching.
 */
public class GoPlay {
    private static final Logger logger = LoggerFactory.getLogger(GoPlay.class);

    // Constants
    public static class Consts {
        public static class Info {
            public static String ClientVersion = "GoPlay/Java;0.1";
            // ServerTag should be set based on server definition
            public static int ServerTag = 1;
        }

        public static class Events {
            public static final String CONNECTED = "__ON_CONNECTED";
            public static final String DISCONNECTED = "__ON_DISCONNECTED";
            public static final String ERROR = "__ON_ERROR";
            public static final String KICKED = "__ON_KICKED";
            public static final String BEFORE_SEND = "__ON_BEFORE_SEND";
            public static final String BEFORE_RECV = "__ON_BEFORE_RECV";
        }

        public static class TimeOut {
            public static long CONNECT = 3000;
            public static long HEARTBEAT = 3000;
            public static int MAX_TIMEOUT = 3;
            public static long REQUEST = 3000;
        }
    }

    public static boolean debug = false;

    private static GoPlayWebSocketClient ws;
    private static String url;
    private static ByteArray buffer;
    private static Emitter emitter = new Emitter();

    private static TaskCompletionSource<Boolean> connectTask;
    private static ScheduledFuture<?> connectTimeOutId;
    private static TaskCompletionSource<Boolean> disconnectTask;

    private static Object handShake;
    private static Map<String, Class<?>> requestMap = new ConcurrentHashMap<>();
    private static Map<String, Class<?>> pushMap = new ConcurrentHashMap<>();
    private static Map<String, Queue<Package<?>>> chunkMap = new ConcurrentHashMap<>();

    private static IdGen idGen = new IdGen(255);
    private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // Static initialization
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                disconnect().get();
            } catch (Exception e) {
                logger.error("Error during shutdown", e);
            }
        }));
    }

    public static int getEncodingType() {
        return EncoderFactory.PROTOBUF;
    }

    // Event methods
    public static void emit(String event, Object... args) {
        emitter.emit(event, args);
    }

    public static void on(String event, Emitter.Function fn) {
        emitter.on(event, fn);
    }

    public static void off(String event, Emitter.Function fn) {
        emitter.off(event, fn);
    }

    public static void off(String event) {
        emitter.off(event);
    }

    public static void once(String event, Emitter.Function fn) {
        emitter.once(event, fn);
    }

    public static List<Emitter.Listener> listeners(String event) {
        return emitter.listeners(event);
    }

    public static boolean hasListeners(String event) {
        return emitter.hasListeners(event);
    }

    public static void removeAllListeners() {
        emitter.removeAllListeners();
    }

    public static Emitter getEmitter() {
        return emitter;
    }

    /**
     * Wait for a specific event to occur (returns CompletableFuture).
     */
    public static CompletableFuture<Object[]> waitForEvent(String event) {
        CompletableFuture<Object[]> future = new CompletableFuture<>();
        once(event, (args) -> future.complete(args));
        return future;
    }

    /**
     * Wait for a specific event with timeout.
     */
    public static CompletableFuture<Object[]> waitForEvent(String event, long timeoutMs) {
        CompletableFuture<Object[]> future = waitForEvent(event);
        
        scheduler.schedule(() -> {
            if (!future.isDone()) {
                future.completeExceptionally(new TimeoutException("Event timeout: " + event));
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);
        
        return future;
    }

    /**
     * Wait for connection to be established.
     */
    public static CompletableFuture<Void> waitForConnection() {
        if (isConnected()) {
            return CompletableFuture.completedFuture(null);
        }
        return waitForEvent(Consts.Events.CONNECTED).thenApply(args -> null);
    }

    // Configuration methods
    public static void setClientVersion(String version) {
        Consts.Info.ClientVersion = version;
    }

    public static void setServerTag(int tag) {
        Consts.Info.ServerTag = tag;
    }

    public static void setTimeout(String key, long value) {
        if ("CONNECT".equals(key)) Consts.TimeOut.CONNECT = value;
        else if ("HEARTBEAT".equals(key)) Consts.TimeOut.HEARTBEAT = value;
        else if ("REQUEST".equals(key)) Consts.TimeOut.REQUEST = value;
    }

    // Connection status
    public static boolean isConnected() {
        if (ws == null) return false;
        if (!ws.isOpen()) return false;
        if (handShake == null) return false;
        return true;
    }

    /**
     * Connect to server.
     */
    public static CompletableFuture<Boolean> connect(String wsUrl) throws URISyntaxException {
        if (isConnected() && url.equals(wsUrl)) {
            GoPlayLogger.logConnect("Already connected", wsUrl);
            return CompletableFuture.completedFuture(true);
        }

        if (isConnected() && !url.equals(wsUrl)) {
            return disconnect().thenCompose(v -> {
                try {
                    return connect(wsUrl);
                } catch (URISyntaxException e) {
                    CompletableFuture<Boolean> cf = new CompletableFuture<>();
                    cf.completeExceptionally(e);
                    return cf;
                }
            });
        }

        if (connectTask != null) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            scheduler.submit(() -> {
                try {
                    future.complete(connectTask.getResult());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            return future;
        }

        url = wsUrl;
        try {
            GoPlayLogger.logConnect("Connecting", wsUrl);
            ws = new GoPlayWebSocketClient(new URI(wsUrl));
            ws.connect();

            connectTask = new TaskCompletionSource<>();
            connectTimeOutId = scheduler.schedule(() -> {
                if (isConnected()) return;
                if (connectTask == null) return;

                connectTask.setResult(false);
                connectTask = null;
                connectTimeOutId = null;
            }, Consts.TimeOut.CONNECT, TimeUnit.MILLISECONDS);

            CompletableFuture<Boolean> future = new CompletableFuture<>();
            scheduler.submit(() -> {
                try {
                    Boolean result = connectTask.getResult();
                    future.complete(result);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });

            return future;
        } catch (Exception e) {
            CompletableFuture<Boolean> cf = new CompletableFuture<>();
            cf.completeExceptionally(e);
            return cf;
        }
    }

    /**
     * Disconnect from server.
     */
    public static CompletableFuture<Boolean> disconnect() {
        if (!isConnected()) {
            return CompletableFuture.completedFuture(true);
        }

        disconnectTask = new TaskCompletionSource<>();
        if (ws != null && ws.isOpen()) {
            ws.close();
        } else {
            disconnectTask.setResult(true);
            cleanup();
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                future.complete(disconnectTask.getResult());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        executor.shutdown();

        return future;
    }

    /**
     * Send a package.
     */
    public static void send(Package<?> pack) {
        emit(Consts.Events.BEFORE_SEND, pack);

        try {
            Package<?>[] packs = pack.split(getEncodingType());
            for (Package<?> p : packs) {
                ByteArray data = p.encode(getEncodingType());
                if (debug) GoPlayLogger.logPackage("Send", p);

                ByteArray buffer = new ByteArray(2 + data.getLength());
                buffer.writeUint16(data.getLength());
                buffer.writeBytes(data);

                ws.send(buffer.getData());
            }
        } catch (Exception e) {
            logger.error("Error sending package", e);
        }
    }

    /**
     * Send a heartbeat ping.
     */
    private static void sendHeartbeat() {
        try {
            // Create ping package (type 4)
            // Package<?> pack = Package.createFromData(0, null, 4, getEncodingType());
            // send(pack);
        } catch (Exception e) {
            logger.error("Error sending heartbeat", e);
        }
    }

    /**
     * Make a request and wait for response.
     */
    public static <T, RT> CompletableFuture<ResponseResult<RT>> request(String route, T data, Class<RT> resultType) {
        CompletableFuture<ResponseResult<RT>> future = new CompletableFuture<>();

        try {
            // Encode route
            int encodedRoute = getRouteEncoded(route);

            // Create request package with PackageType.Request (type = 1)
            Package<?> pack = Package.createFromData(encodedRoute, data, PackageType.Request_VALUE, getEncodingType());
            
            // Set the request ID in the header
            Object header = pack.getHeader();
            if (header instanceof Header) {
                Header originalHeader = (Header) header;
                // Rebuild header with the new ID
                Header newHeader = Header.newBuilder(originalHeader)
                        .setPackageInfo(PackageInfo.newBuilder(originalHeader.getPackageInfo())
                                .setId(idGen.next())
                                .build())
                        .build();
                pack.setHeader(newHeader);
            }
            
            String key = getCallbackKey(pack.getHeader());
            requestMap.put(key, resultType);

            // Set timeout - emit timeout status if response doesn't arrive in time
            ScheduledFuture<?> timeoutHandle = scheduler.schedule(() -> {
                if (requestMap.containsKey(key)) {
                    // Remove the request mapping
                    requestMap.remove(key);
                    
                    // Create a timeout response with status code 1000 (timeout)
                    ResponseResult<RT> result = new ResponseResult<>(1000, null);
                    
                    // Emit the timeout event to trigger the once handler
                    emit(key, result);
                }
            }, Consts.TimeOut.REQUEST, TimeUnit.MILLISECONDS);

            // Register response handler - will be called when response arrives or timeout occurs
            once(key, (args) -> {
                if (args.length > 0 && args[0] instanceof ResponseResult) {
                    @SuppressWarnings("unchecked")
                    ResponseResult<RT> result = (ResponseResult<RT>) args[0];
                    
                    // Cancel timeout if response arrived in time
                    timeoutHandle.cancel(false);
                    
                    // Remove from request map
                    requestMap.remove(key);
                    
                    // Complete the future
                    future.complete(result);
                }
            });

            // Send the request package
            send(pack);
            
        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * Send a notify message (no response expected).
     */
    public static <T> void notify(String route, T data) {
        try {
            int encodedRoute = getRouteEncoded(route);
            Package<?> pack = Package.createFromData(encodedRoute, data, PackageType.Notify_VALUE, getEncodingType());
            send(pack);
        } catch (Exception e) {
            logger.error("Error sending notify", e);
        }
    }

    /**
     * Register a push handler with type.
     */
    public static <T> void onType(String event, Class<T> type, Emitter.Function fn) {
        if (pushMap.containsKey(event) && !pushMap.get(event).equals(type)) {
            throw new IllegalStateException("Event already registered with different type");
        }
        pushMap.put(event, type);
        emitter.on(event, fn);
    }

    /**
     * Register a one-time push handler with type.
     */
    public static <T> void onceType(String event, Class<T> type, Emitter.Function fn) {
        if (pushMap.containsKey(event) && !pushMap.get(event).equals(type)) {
            throw new IllegalStateException("Event already registered with different type");
        }
        pushMap.put(event, type);
        emitter.once(event, fn);
    }

    // Private helper methods

    private static String getChunkKey(Package<?> pack) {
        // Implement based on header structure
        return "chunk_key";
    }

    private static Package<?> resolveChunk(Package<?> pack) {
        String key = getChunkKey(pack);
        Queue<Package<?>> chunks = chunkMap.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>());
        chunks.offer(pack);

        // Check if all chunks received
        // If yes, join and return; if no, return null

        return null;
    }

    private static Package<?> recv() throws Exception {
        if (buffer == null || buffer.getLength() == 0) return null;

        if (!buffer.hasReadSize(2)) return null;
        int packSize = buffer.readUint16();
        if (packSize <= 0) {
            // Invalid or empty frame; reset and skip
            buffer.roffset -= 2;
            return null;
        }

        if (!buffer.hasReadSize(packSize)) {
            buffer.roffset -= 2;
            return null;
        }

        byte[] data = buffer.readBytes(packSize);
        Package<?> pack = null;
        try {
            pack = Package.tryDecodeRaw(new ByteArray(data), getEncodingType());
        } catch (Exception ex) {
            logger.error("Error decoding package", ex);
        }

        if (debug && pack != null) logger.info("Recv: {}", pack);

        if (pack == null) return null;

        // Handle chunked messages
        // if (pack.header.PackageInfo.ChunkCount > 1) {
        //     pack = resolveChunk(pack);
        //     if (pack.header.PackageInfo.ChunkCount > 1) return null;
        // }

        emit(Consts.Events.BEFORE_RECV, pack);
        return pack;
    }

    private static int getRouteEncoded(String route) {
        if (handShake == null) {
            throw new IllegalStateException("Handshake not completed; routes unavailable");
        }
        if (handShake instanceof RespHandShake) {
            RespHandShake hs = (RespHandShake) handShake;
            Integer id = hs.getRoutesMap().get(route);
            if (id == null) {
                throw new IllegalArgumentException("Route not found: " + route);
            }
            return id;
        }
        throw new IllegalStateException("Invalid handshake state");
    }

    private static String getRoute(int encodedRoute) {
        if (handShake == null) return "";
        if (handShake instanceof RespHandShake) {
            RespHandShake hs = (RespHandShake) handShake;
            for (Map.Entry<String, Integer> e : hs.getRoutesMap().entrySet()) {
                if (e.getValue() == encodedRoute) return e.getKey();
            }
        }
        return "";
    }

    private static String getCallbackKey(Object header) {
        if (header instanceof Header) {
            Header h = (Header) header;
            return h.getPackageInfo().getRoute() + "-" + h.getPackageInfo().getId();
        }
        return "";
    }

    private static String getPushKey(Object header) {
        if (header instanceof Header) {
            Header h = (Header) header;
            return getRoute(h.getPackageInfo().getRoute());
        }
        return "";
    }

    private static void cleanup() {
        ws = null;
        handShake = null;
        buffer = null;
        requestMap.clear();
        chunkMap.clear();
    }

    private static void onHandshake(Package<?> pack) {
        GoPlayLogger.logHandshake("Received");
        try {
            Package<RespHandShake> p = pack.decodeFromRaw(RespHandShake.class, getEncodingType());
            handShake = p.getData();
            HeartBeat.start();
            emit(Consts.Events.CONNECTED);
            if (connectTask != null) {
                connectTask.setResult(true);
                if (connectTimeOutId != null) connectTimeOutId.cancel(false);
                connectTask = null;
                connectTimeOutId = null;
            }
        } catch (Exception e) {
            logger.error("Error parsing handshake", e);
        }
    }

    private static void onResponse(Package<?> pack) {
        // Get callback key from header
        String key = getCallbackKey(pack.getHeader());
        
        if (!requestMap.containsKey(key)) {
            if (debug) logger.warn("Response received for unknown request: {}", key);
            return;
        }
        
        try {
            // Get the expected result type for this response
            Class<?> resultType = requestMap.get(key);
            
            // Decode the response data
            Package<?> decodedPack = pack.decodeFromRaw(resultType, getEncodingType());
            
            // Create response result (status code 0 = success)
            ResponseResult<?> result = new ResponseResult<>(0, decodedPack.getData());
            
            // Emit the response event to trigger the once handler registered in request()
            emit(key, result);
            
            if (debug) logger.info("Response processed for: {}", key);
        } catch (Exception e) {
            logger.error("Error processing response for: {}", key, e);
            // Emit error response
            ResponseResult<?> result = new ResponseResult<>(500, null);
            emit(key, result);
        }
    }

    private static void onPush(Package<?> pack) {
        // TODO: Implement push processing
    }

    private static void onKick(Package<?> pack) {
        emit(Consts.Events.KICKED);
        try {
            disconnect();
        } catch (Exception e) {
            logger.error("Error disconnecting on kick", e);
        }
    }

    // WebSocket client implementation
    private static class GoPlayWebSocketClient extends WebSocketClient {
        public GoPlayWebSocketClient(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            GoPlayLogger.logEvent("WebSocket Connected");
            sendHandshake();
        }

        @Override
        public void onMessage(String message) {
            logger.warn("Received text message (binary expected): {}", message);
        }

        @Override
        public void onMessage(ByteBuffer message) {
            ByteArray data = new ByteArray(message);

            if (buffer == null) {
                buffer = data;
            } else {
                buffer.writeBytes(data);
            }

            Package<?> pack = null;
            try {
                pack = recv();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            while (pack != null) {
                processPackage(pack);
                try {
                    pack = recv();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public void onError(Exception ex) {
            GoPlayLogger.logError("WebSocket Error", ex);
            emit(Consts.Events.ERROR, ex);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            GoPlayLogger.logEvent("WebSocket Closed", "code=" + code, "reason=" + reason);
            HeartBeat.stop();

            if (disconnectTask != null) {
                disconnectTask.setResult(true);
            }
            if (connectTask != null) {
                connectTask.setResult(false);
            }

            cleanup();
            emit(Consts.Events.DISCONNECTED);
        }
    }

    private static void processPackage(Package<?> pack) {
        Object hdr = pack.getHeader();
        if (!(hdr instanceof Header)) return;
        Header h = (Header) hdr;
        int typeVal = h.getPackageInfo().getTypeValue();
        if (typeVal == PackageType.Response_VALUE) {
            onResponse(pack);
        } else if (typeVal == PackageType.Push_VALUE) {
            onPush(pack);
        } else if (typeVal == PackageType.Ping_VALUE) {
            try {
                Package<?> pong = Package.createFromData(0, null, PackageType.Pong_VALUE, getEncodingType());
                send(pong);
            } catch (Exception ex) {
                logger.error("Error sending pong", ex);
            }
        } else if (typeVal == PackageType.Pong_VALUE) {
            HeartBeat.onPong(h.getPackageInfo().getId());
        } else if (typeVal == PackageType.HankShakeResp_VALUE) {
            onHandshake(pack);
        } else if (typeVal == PackageType.Kick_VALUE) {
            onKick(pack);
        }
    }

    private static void sendHandshake() {
        try {
            GoPlayLogger.logHandshake("Sending", Consts.Info.ClientVersion);
            ReqHankShake data = ReqHankShake.newBuilder()
                    .setClientVersion(Consts.Info.ClientVersion)
                    .setServerTag(ServerTag.forNumber(Consts.Info.ServerTag))
                    .build();
            Package<?> pack = Package.createFromData(0, data, PackageType.HankShakeReq_VALUE, getEncodingType());
            send(pack);
        } catch (Exception e) {
            logger.error("Error sending handshake", e);
            if (connectTask != null) {
                connectTask.setException(e);
            }
        }
    }

    // Heartbeat implementation
    private static class HeartBeat {
        private static ScheduledFuture<?> intervalId;
        private static Map<Integer, ScheduledFuture<?>> timeoutMap = new ConcurrentHashMap<>();
        private static int timeoutCount = 0;

        public static void start() {
            if (intervalId != null) return;

            intervalId = scheduler.scheduleAtFixedRate(() -> {
                try {
                    sendHeartbeat();
                } catch (Exception e) {
                    logger.error("Error in heartbeat", e);
                }
            }, Consts.TimeOut.HEARTBEAT, Consts.TimeOut.HEARTBEAT, TimeUnit.MILLISECONDS);
        }

        public static void stop() {
            if (intervalId != null) {
                intervalId.cancel(false);
                intervalId = null;
            }
            timeoutMap.forEach((k, v) -> v.cancel(false));
            timeoutMap.clear();
            timeoutCount = 0;
        }

        public static void onPong(int id) {
            ScheduledFuture<?> timeout = timeoutMap.remove(id);
            if (timeout != null) {
                timeout.cancel(false);
                timeoutCount = 0;
            }
        }
    }

    // Response result wrapper
    public static class ResponseResult<T> {
        public int status;
        public T data;

        public ResponseResult() {
            this(0, null);
        }

        public ResponseResult(int status, T data) {
            this.status = status;
            this.data = data;
        }
    }
}
