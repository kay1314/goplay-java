package com.goplay.codegen;

import com.goplay.core.GoPlayLogger;
import java.io.IOException;

/**
 * Command-line interface for GoPlay code generator.
 * Supports generating Java client code from C# backend files or JSON config.
 */
public class CodeGenCLI {

    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }

        try {
            String command = args[0];

            switch (command) {
                case "from-csharp":
                    generateFromCSharp(args);
                    break;
                case "from-json":
                    generateFromJson(args);
                    break;
                case "analyze-csharp":
                    analyzeCSharp(args);
                    break;
                default:
                    System.err.println("Unknown command: " + command);
                    printUsage();
                    System.exit(1);
            }

        } catch (Exception e) {
            GoPlayLogger.error("Code generation failed", e);
            System.exit(1);
        }
    }

    private static void generateFromCSharp(String[] args) throws IOException {
        if (args.length < 5) {
            System.err.println("Usage: CodeGenCLI from-csharp <csharp-dir> <package> <class-name> <output-file>");
            System.exit(1);
        }

        String csharpDir = args[1];
        String packageName = args[2];
        String className = args[3];
        String outputFile = args[4];
        String protobufPackage = args.length > 5 ? args[5] : packageName + ".protocols";

        GoPlayLogger.info("=== GoPlay Code Generator ===");
        GoPlayLogger.info("Source: C# directory");
        GoPlayLogger.info("Input: {}", csharpDir);
        GoPlayLogger.info("Package: {}", packageName);
        GoPlayLogger.info("Class: {}", className);
        GoPlayLogger.info("Output: {}", outputFile);
        GoPlayLogger.info("");

        // Parse C# files
        CSharpRouteParser parser = new CSharpRouteParser();
        RouteConfig config = parser.parseDirectory(csharpDir, protobufPackage);
        config.setPackageName(packageName);
        config.setClassName(className);

        // Generate code
        GoPlayLogger.info("");
        GoPlayLogger.info("Generating Java code...");
        JavaCodeGenerator generator = new JavaCodeGenerator(config);
        generator.writeToFile(outputFile);

        // Print summary
        GoPlayLogger.info("");
        GoPlayLogger.info("=== Generation Summary ===");
        GoPlayLogger.info("Routes: {}", config.getRoutes().size());
        GoPlayLogger.info("Pushes: {}", config.getPushes().size());
        GoPlayLogger.info("✓ Code generation completed successfully!");
    }

    private static void generateFromJson(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("Usage: CodeGenCLI from-json <config.json> <output-file>");
            System.exit(1);
        }

        String configPath = args[1];
        String outputPath = args[2];

        GoPlayLogger.info("=== GoPlay Code Generator ===");
        GoPlayLogger.info("Source: JSON config");
        GoPlayLogger.info("Config: {}", configPath);
        GoPlayLogger.info("Output: {}", outputPath);
        GoPlayLogger.info("");

        RouteConfig config = ConfigParser.parseJson(configPath);

        JavaCodeGenerator generator = new JavaCodeGenerator(config);
        generator.writeToFile(outputPath);

        GoPlayLogger.info("✓ Code generation completed successfully!");
    }

    private static void analyzeCSharp(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: CodeGenCLI analyze-csharp <csharp-dir>");
            System.exit(1);
        }

        String csharpDir = args[1];

        GoPlayLogger.info("=== Analyzing C# Backend ===");
        GoPlayLogger.info("Directory: {}", csharpDir);
        GoPlayLogger.info("");

        CSharpRouteParser parser = new CSharpRouteParser();
        RouteConfig config = parser.parseDirectory(csharpDir, "");

        GoPlayLogger.info("");
        GoPlayLogger.info("=== Analysis Results ===");
        GoPlayLogger.info("Total Routes: {}", config.getRoutes().size());
        GoPlayLogger.info("Total Pushes: {}", config.getPushes().size());
        GoPlayLogger.info("");

        GoPlayLogger.info("Routes:");
        config.getRoutes().forEach((key, route) -> {
            GoPlayLogger.info("  {} -> {}", key, route.route);
            if (route.requestType != null) {
                GoPlayLogger.info("    Request: {}", route.requestType);
            }
            if (route.responseType != null) {
                GoPlayLogger.info("    Response: {}", route.responseType);
            }
        });

        if (!config.getPushes().isEmpty()) {
            GoPlayLogger.info("");
            GoPlayLogger.info("Pushes:");
            config.getPushes().forEach((key, push) -> {
                GoPlayLogger.info("  {} -> {}", key, push.pushKey);
            });
        }
    }

    private static void printUsage() {
        System.out.println("GoPlay Code Generator - Generate strongly-typed Java client from backend");
        System.out.println("");
        System.out.println("Usage:");
        System.out.println("  CodeGenCLI from-csharp <csharp-dir> <package> <class-name> <output-file> [protobuf-package]");
        System.out.println("    Generate Java client from C# backend processor files");
        System.out.println("");
        System.out.println("  CodeGenCLI from-json <config.json> <output-file>");
        System.out.println("    Generate Java client from JSON configuration");
        System.out.println("");
        System.out.println("  CodeGenCLI analyze-csharp <csharp-dir>");
        System.out.println("    Analyze C# backend and print route information");
        System.out.println("");
        System.out.println("Examples:");
        System.out.println("  # Generate from C# backend");
        System.out.println("  java -cp target/classes com.goplay.codegen.CodeGenCLI from-csharp \\");
        System.out.println("    D:\\Backend\\Processors \\");
        System.out.println("    com.example.client \\");
        System.out.println("    GameClient \\");
        System.out.println("    src/main/java/com/example/client/GameClient.java \\");
        System.out.println("    com.example.protocols");
        System.out.println("");
        System.out.println("  # Analyze backend routes");
        System.out.println("  java -cp target/classes com.goplay.codegen.CodeGenCLI analyze-csharp \\");
        System.out.println("    D:\\Backend\\Processors");
    }
}