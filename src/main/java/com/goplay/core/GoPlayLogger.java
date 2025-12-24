package com.goplay.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * GoPlayLogger provides beautiful and structured logging for GoPlay framework.
 * Adds timestamps, colors, and consistent formatting to all log outputs.
 */
public class GoPlayLogger {
    private static final Logger logger = LoggerFactory.getLogger("com.goplay");
    private static final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    
    // ANSI Color codes for console output
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String CYAN = "\u001B[36m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String BLUE = "\u001B[34m";
    private static final String MAGENTA = "\u001B[35m";

    // Logging configuration flags
    public static boolean logPackages = true;      // Log send/recv packages
    public static boolean logHandshake = true;     // Log handshake events
    public static boolean logEvents = true;        // Log connection/network events
    public static boolean logRequests = true;      // Log request/response details
    public static boolean logConnections = true;   // Log connect/disconnect events

    public static void info(String message) {
        logger.info(message);
    }

    public static void info(String format, Object... args) {
        logger.info(format, args);
    }

    public static void debug(String message) {
        logger.debug(message);
    }

    public static void debug(String format, Object... args) {
        logger.debug(format, args);
    }

    public static void warn(String message) {
        logger.warn(message);
    }

    public static void warn(String format, Object... args) {
        logger.warn(format, args);
    }

    public static void error(String message, Throwable ex) {
        logger.error(message, ex);
    }

    public static void error(String format, Object... args) {
        logger.error(String.format(format, args));
    }

    // Pretty-print event logs
    public static void logEvent(String eventName, Object... details) {
        if (!logEvents) return;
        StringBuilder sb = new StringBuilder();
        sb.append(CYAN).append("【EVENT】").append(RESET).append(" ");
        sb.append(BOLD).append(eventName).append(RESET);
        if (details.length > 0) {
            sb.append(" ");
            for (int i = 0; i < details.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(details[i]);
            }
        }
        logger.info(sb.toString());
    }

    // Pretty-print network package events
    public static void logPackage(String action, Object pkg) {
        if (!logPackages) return;
        StringBuilder sb = new StringBuilder();
        sb.append(BLUE).append("【PKG】").append(RESET).append(" ");
        sb.append(BOLD).append(action).append(RESET).append(": ");
        sb.append(pkg);
        logger.info(sb.toString());
    }

    // Pretty-print handshake events
    public static void logHandshake(String status, Object... details) {
        if (!logHandshake) return;
        StringBuilder sb = new StringBuilder();
        sb.append(GREEN).append("【SHAKE】").append(RESET).append(" ");
        sb.append(BOLD).append(status).append(RESET);
        if (details.length > 0) {
            sb.append(" ");
            for (int i = 0; i < details.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(details[i]);
            }
        }
        logger.info(sb.toString());
    }

    // Pretty-print request/response events
    public static void logRequest(String route, int id, Object data) {
        if (!logRequests) return;
        StringBuilder sb = new StringBuilder();
        sb.append(MAGENTA).append("【REQ】").append(RESET).append(" ");
        sb.append("route: ").append(BOLD).append(route).append(RESET);
        sb.append(", id: ").append(id);
        if (data != null) {
            sb.append(", data: ").append(data);
        }
        logger.info(sb.toString());
    }

    public static void logResponse(int id, int status, Object data) {
        if (!logRequests) return;
        StringBuilder sb = new StringBuilder();
        sb.append(MAGENTA).append("【RESP】").append(RESET).append(" ");
        sb.append("id: ").append(id);
        sb.append(", status: ").append(status == 0 ? GREEN + "OK" + RESET : RED + "FAIL" + RESET);
        if (data != null) {
            sb.append(", data: ").append(data);
        }
        logger.info(sb.toString());
    }

    // Pretty-print error events
    public static void logError(String message, Throwable ex) {
        StringBuilder sb = new StringBuilder();
        sb.append(RED).append("【ERROR】").append(RESET).append(" ");
        sb.append(BOLD).append(message).append(RESET);
        if (ex != null) {
            sb.append(": ").append(ex.getMessage());
        }
        logger.error(sb.toString(), ex);
    }

    // Pretty-print connection status
    public static void logConnect(String status, String url) {
        if (!logConnections) return;
        StringBuilder sb = new StringBuilder();
        sb.append(CYAN).append("【CONN】").append(RESET).append(" ");
        sb.append(BOLD).append(status).append(RESET).append(" ");
        sb.append(url);
        logger.info(sb.toString());
    }

    // Get current timestamp string
    public static String timestamp() {
        return timeFormatter.format(LocalDateTime.now());
    }

    /**
     * Set logging verbosity level.
     * @param level "quiet" (all off), "minimal" (only errors/important), "normal" (default), "verbose" (all on)
     */
    public static void setLogLevel(String level) {
        switch (level.toLowerCase()) {
            case "quiet":
                logPackages = false;
                logHandshake = false;
                logEvents = false;
                logRequests = false;
                logConnections = false;
                break;
            case "minimal":
                logPackages = false;
                logHandshake = false;
                logEvents = false;
                logRequests = true;
                logConnections = true;
                break;
            case "normal":
            default:
                logPackages = true;
                logHandshake = true;
                logEvents = true;
                logRequests = true;
                logConnections = true;
                break;
            case "verbose":
                logPackages = true;
                logHandshake = true;
                logEvents = true;
                logRequests = true;
                logConnections = true;
                break;
        }
    }

    /**
     * Disable all logging except errors.
     */
    public static void disableAll() {
        setLogLevel("quiet");
    }

    /**
     * Enable all logging.
     */
    public static void enableAll() {
        setLogLevel("verbose");
    }
}
