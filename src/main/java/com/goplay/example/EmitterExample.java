package com.goplay.example;

import com.goplay.core.Emitter;

/**
 * Example of using Emitter for event-driven communication.
 */
public class EmitterExample {
    public static void main(String[] ag) throws InterruptedException {
        Emitter emitter = new Emitter();

        // Register listeners
        emitter.on("user-login", (args) -> {
            String username = (String) args[0];
            int userId = (int) args[1];
            System.out.println("User logged in: " + username + " (ID: " + userId + ")");
        });

        emitter.on("user-logout", (args) -> {
            String username = (String) args[0];
            System.out.println("User logged out: " + username);
        });

        // Register one-time listener
        emitter.once("game-start", (args) -> {
            System.out.println("Game started (first time only)");
        });

        // Emit events
        System.out.println("Emitting events...\n");

        emitter.emit("user-login", "Alice", 101);
        emitter.emit("user-login", "Bob", 102);

        emitter.emit("game-start", "Level 1");
        emitter.emit("game-start", "Level 2");  // This won't trigger the once listener

        emitter.emit("user-logout", "Alice");

        // Check if event has listeners
        System.out.println("\nHas listeners for 'user-login': " + emitter.hasListeners("user-login"));
        System.out.println("Has listeners for 'unknown-event': " + emitter.hasListeners("unknown-event"));

        // Get all listeners for an event
        System.out.println("Listeners for 'user-login': " + emitter.listeners("user-login").size());

        // Remove listener
        System.out.println("\nRemoving 'user-logout' listeners...");
        emitter.off("user-logout");
        System.out.println("Has listeners for 'user-logout': " + emitter.hasListeners("user-logout"));

        emitter.emit("user-logout", "Bob");  // Won't print anything

        // Remove all listeners
        System.out.println("\nRemoving all listeners...");
        emitter.removeAllListeners();
        System.out.println("Callbacks count: " + emitter.getCallbacks().size());

        emitter.emit("user-login", "Charlie", 103);  // Won't print anything
    }
}
