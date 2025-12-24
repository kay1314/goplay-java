package com.goplay.codegen;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;

/**
 * Parses route configuration from JSON file.
 */
public class ConfigParser {

    public static RouteConfig parseJson(String jsonPath) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(jsonPath)));
        JsonObject json = JsonParser.parseString(content).getAsJsonObject();

        RouteConfig config = new RouteConfig();
        config.setPackageName(json.get("packageName").getAsString());
        config.setClassName(json.get("className").getAsString());

        if (json.has("protobufPackage")) {
            config.setProtobufPackage(json.get("protobufPackage").getAsString());
        }

        // Parse routes
        if (json.has("routes")) {
            JsonObject routes = json.getAsJsonObject("routes");
            for (String key : routes.keySet()) {
                JsonObject routeObj = routes.getAsJsonObject(key);
                String route = routeObj.get("route").getAsString();
                String requestType = routeObj.has("request") ? routeObj.get("request").getAsString() : null;
                String responseType = routeObj.has("response") ? routeObj.get("response").getAsString() : null;
                config.addRoute(key, route, requestType, responseType);
            }
        }

        // Parse pushes
        if (json.has("pushes")) {
            JsonObject pushes = json.getAsJsonObject("pushes");
            for (String key : pushes.keySet()) {
                JsonObject pushObj = pushes.getAsJsonObject(key);
                String pushKey = pushObj.get("key").getAsString();
                String dataType = pushObj.has("dataType") ? pushObj.get("dataType").getAsString() : null;
                config.addPush(key, pushKey, dataType);
            }
        }

        return config;
    }
}