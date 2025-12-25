package com.goplay.example;

import com.goplay.GoPlay;
import com.goplay.core.GoPlayLogger;
import com.goplay.core.protocols.ProtocolProto;
import com.goplay.example.dto.SCTimeInfo;
import java.util.concurrent.CompletableFuture;

/**
 * Simple example of using GoPlay client.
 */
public class SimpleExample {
    public static void main(String[] args) {
        try {
            // Configure client
            GoPlay.setClientVersion("MyGame/1.0.0");
            GoPlay.debug = false;

            // Configure logging
            // Options: GoPlayLogger.setLogLevel("quiet|minimal|normal|verbose")
            // GoPlayLogger.disableAll();  // Hide all logs
            GoPlayLogger.setLogLevel("normal");  // Default: show all

            // Register event listeners
            registerListeners();

            // Connect to server
            System.out.println("Connecting to server...");
            boolean connected = GoPlay.connect("ws://localhost:8080").get();

            if (connected) {
                System.out.println("Connected successfully!");

                // Wait a bit for handshake
                Thread.sleep(2000);

                // Example 2: Make a request with response handling
                System.out.println("\n--- Request Example ---");
                System.out.println("Making a request to 'time.utc.now'...");
                CompletableFuture<GoPlay.ResponseResult<SCTimeInfo>> requestFuture = 
                    GoPlay.request("time.utc.now", null, SCTimeInfo.class);
                
                // You can handle the response asynchronously
                requestFuture.thenAccept(result -> {
                    if (result != null) {
                        System.out.println("Response status: IsSucceed" + (result.status.getCode() == ProtocolProto.StatusCode.Success_VALUE));
                        System.out.println("Response data: " + result.data);
                    }
                }).exceptionally(ex -> {
                    System.err.println("Request failed: " + ex.getMessage());
                    return null;
                });

                // Or wait for it synchronously (with timeout)
                // GoPlay.ResponseResult<SCTimeInfo> result = requestFuture.get(5, TimeUnit.SECONDS);

                System.out.println("Demo: Successfully connected and set up request handlers!");

                // Keep the connection alive
                Thread.sleep(3000);

                // Disconnect
                System.out.println("\nDisconnecting...");
                GoPlay.disconnect().get();
                System.out.println("Disconnected");
            } else {
                System.err.println("Failed to connect");
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void registerListeners() {
        // Connected event
        GoPlay.on(GoPlay.Consts.Events.CONNECTED, (args) -> {
            GoPlayLogger.logEvent("Connected to server");
        });

        // Disconnected event
        GoPlay.on(GoPlay.Consts.Events.DISCONNECTED, (args) -> {
            GoPlayLogger.logEvent("Disconnected from server");
        });

        // Error event
        GoPlay.on(GoPlay.Consts.Events.ERROR, (args) -> {
            GoPlayLogger.logError("Error event", (Exception) args[0]);
        });

        // Kicked event
        GoPlay.on(GoPlay.Consts.Events.KICKED, (args) -> {
            GoPlayLogger.logEvent("Kicked from server");
        });

        // Before send event
        GoPlay.on(GoPlay.Consts.Events.BEFORE_SEND, (args) -> {
            GoPlayLogger.logPackage("Send", args[0]);
        });

        // Before receive event
        GoPlay.on(GoPlay.Consts.Events.BEFORE_RECV, (args) -> {
            GoPlayLogger.logPackage("Recv", args[0]);
        });
    }
}
