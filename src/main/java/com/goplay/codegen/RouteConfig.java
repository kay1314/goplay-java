package com.goplay.codegen;

import java.util.*;

/**
 * Route configuration model for code generation.
 */
public class RouteConfig {
    private Map<String, RouteInfo> routes = new HashMap<>();
    private Map<String, PushInfo> pushes = new HashMap<>();
    private String packageName;
    private String className;
    private String protobufPackage;

    public static class RouteInfo {
        public String methodName;
        public String route;
        public String requestType;   // Full qualified class name
        public String responseType;  // Full qualified class name
        public boolean hasRequest = true;
        public boolean hasResponse = true;

        public RouteInfo(String methodName, String route) {
            this.methodName = methodName;
            this.route = route;
        }
    }

    public static class PushInfo {
        public String eventName;
        public String pushKey;
        public String dataType;

        public PushInfo(String eventName, String pushKey) {
            this.eventName = eventName;
            this.pushKey = pushKey;
        }
    }

    public void addRoute(String key, String route, String requestType, String responseType) {
        RouteInfo info = new RouteInfo(key, route);
        info.requestType = requestType;
        info.responseType = responseType;
        info.hasRequest = requestType != null && !requestType.equals("null");
        info.hasResponse = responseType != null && !responseType.equals("null");
        routes.put(key, info);
    }

    public void addPush(String key, String pushKey, String dataType) {
        PushInfo info = new PushInfo(key, pushKey);
        info.dataType = dataType;
        pushes.put(key, info);
    }

    public Map<String, RouteInfo> getRoutes() { return routes; }
    public Map<String, PushInfo> getPushes() { return pushes; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getProtobufPackage() { return protobufPackage; }
    public void setProtobufPackage(String protobufPackage) { this.protobufPackage = protobufPackage; }
}