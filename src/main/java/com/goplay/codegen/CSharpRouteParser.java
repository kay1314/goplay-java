package com.goplay.codegen;

import com.goplay.core.GoPlayLogger;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Parses C# backend processor files to extract route information.
 * Supports [Processor], [Request], [Notify], and [Push] attributes.
 */
public class CSharpRouteParser {

    private static final Pattern SERVER_TAG_PATTERN = Pattern.compile(
            "\\[ServerTag\\(Tag\\s*=\\s*ServerTag\\.FrontEnd\\)\\]"
    );

    private static final Pattern PROCESSOR_PATTERN = Pattern.compile(
            "\\[Processor\\(\"([^\"]+)\"\\)\\]"
    );

    private static final Pattern REQUEST_PATTERN = Pattern.compile(
            "\\[Request\\(\"([^\"]+)\"\\)\\]\\s*public\\s+(\\w+)\\s+(\\w+)\\s*\\([^)]*\\)"
    );

    private static final Pattern NOTIFY_PATTERN = Pattern.compile(
            "\\[Notify\\(\"([^\"]+)\"\\)\\]\\s*public\\s+void\\s+(\\w+)\\s*\\([^)]*\\)"
    );

    private static final Pattern PUSH_PATTERN = Pattern.compile(
            "public override string\\[\\] Pushes => new\\[\\] \\{([^}]+)\\}"
    );

    private static final Pattern METHOD_PARAM_PATTERN = Pattern.compile(
            "\\((Header header(?:,\\s*(\\w+)\\s+(\\w+))?)?\\)"
    );


    public RouteConfig parse(String csharpFilePath) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(csharpFilePath)));
        RouteConfig config = new RouteConfig();

        // Check for ServerTag.FrontEnd
        if (!hasFrontEndServerTag(content)) {
            GoPlayLogger.info("Skipping file as it does not have ServerTag.FrontEnd: {}", csharpFilePath);
            return config; // Skip processing this file
        }

        // Extract processor name
        String processorName = extractProcessorName(content);
        if (processorName == null) {
            throw new IllegalArgumentException("No [Processor] attribute found in file");
        }

        GoPlayLogger.info("Found processor: {}", processorName);

        // Extract requests
        extractRequests(content, processorName, config);

        // Extract notifies
        extractNotifies(content, processorName, config);

        // Extract pushes
        extractPushes(content, processorName, config);

        return config;
    }

    private boolean hasFrontEndServerTag(String content) {
        Matcher matcher = SERVER_TAG_PATTERN.matcher(content);
        return matcher.find();
    }

    private String extractProcessorName(String content) {
        Matcher matcher = PROCESSOR_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private void extractRequests(String content, String processorName, RouteConfig config) {
        Matcher matcher = REQUEST_PATTERN.matcher(content);

        while (matcher.find()) {
            String route = matcher.group(1);
            String responseType = matcher.group(2);
            String methodName = matcher.group(3);

            // Parse method parameters
            String fullRoute = processorName + "." + route;
            String requestType = extractRequestType(content, methodName);

            // Generate method name: Time_GetUtcNow
            String generatedMethodName = toPascalCase(processorName) + "_" + methodName;

            config.addRoute(
                    generatedMethodName,
                    fullRoute,
                    requestType,
                    responseType
            );

            GoPlayLogger.info("  [Request] {} -> {} ({} -> {})",
                    generatedMethodName, fullRoute, requestType, responseType);
        }
    }

    private void extractNotifies(String content, String processorName, RouteConfig config) {
        Matcher matcher = NOTIFY_PATTERN.matcher(content);

        while (matcher.find()) {
            String route = matcher.group(1);
            String methodName = matcher.group(2);

            String fullRoute = processorName + "." + route;
            String requestType = extractRequestType(content, methodName);

            String generatedMethodName = toPascalCase(processorName) + "_" + methodName;

            config.addRoute(
                    generatedMethodName,
                    fullRoute,
                    requestType,
                    null  // Notify has no response
            );

            GoPlayLogger.info("  [Notify] {} -> {} ({})",
                    generatedMethodName, fullRoute, requestType);
        }
    }

    private void extractPushes(String content, String processorName, RouteConfig config) {
        Matcher matcher = PUSH_PATTERN.matcher(content);

        if (matcher.find()) {
            String pushesStr = matcher.group(1);
            String[] pushes = pushesStr.split(",");

            for (String push : pushes) {
                push = push.trim().replace("\"", "");
                if (!push.isEmpty()) {
                    String fullPushKey = processorName + "." + push;
                    String eventName = toPascalCase(processorName) + "_" + toPascalCase(push);

                    config.addPush(eventName, fullPushKey, null);
                    GoPlayLogger.info("  [Push] {} -> {}", eventName, fullPushKey);
                }
            }
        }
    }

    private String extractRequestType(String content, String methodName) {
        // Find method signature
        Pattern methodPattern = Pattern.compile(
                "public\\s+\\w+\\s+" + methodName + "\\s*\\(([^)]*)\\)"
        );
        Matcher matcher = methodPattern.matcher(content);

        if (matcher.find()) {
            String params = matcher.group(1);

            // Skip "Header header" and find actual request parameter
            String[] paramParts = params.split(",");
            for (String param : paramParts) {
                param = param.trim();
                if (!param.isEmpty() && !param.startsWith("Header")) {
                    // Extract type name: "CSTimeInfo request" -> "CSTimeInfo"
                    String[] parts = param.split("\\s+");
                    if (parts.length >= 1) {
                        return parts[0];
                    }
                }
            }
        }

        return null;  // No request parameter
    }

    private String toPascalCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // Handle dot notation: "utc.now" -> "UtcNow"
        if (input.contains(".")) {
            String[] parts = input.split("\\.");
            StringBuilder result = new StringBuilder();
            for (String part : parts) {
                result.append(capitalize(part));
            }
            return result.toString();
        }

        return capitalize(input);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    /**
     * Parse all C# files in a directory.
     */
    public RouteConfig parseDirectory(String dirPath, String protobufPackage) throws IOException {
        RouteConfig mergedConfig = new RouteConfig();
        mergedConfig.setProtobufPackage(protobufPackage);

        Files.walk(Paths.get(dirPath))
                .filter(path -> path.toString().endsWith(".cs"))
                .forEach(path -> {
                    try {
                        GoPlayLogger.info("Parsing: {}", path.getFileName());
                        RouteConfig config = parse(path.toString());

                        // Merge routes
                        config.getRoutes().forEach((key, value) ->
                                mergedConfig.addRoute(key, value.route, value.requestType, value.responseType)
                        );

                        // Merge pushes
                        config.getPushes().forEach((key, value) ->
                                mergedConfig.addPush(key, value.pushKey, value.dataType)
                        );

                    } catch (Exception e) {
                        GoPlayLogger.error("Failed to parse: " + path, e);
                    }
                });

        return mergedConfig;
    }


}