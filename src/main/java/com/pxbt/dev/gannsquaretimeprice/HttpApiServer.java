package com.pxbt.dev.gannsquaretimeprice;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.pxbt.dev.gannsquaretimeprice.Service.GannSquareService;
import com.pxbt.dev.gannsquaretimeprice.Gateway.PriceDataGateway;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;

/**
 * Lightweight HTTP server using Java's built-in HttpServer.
 * Serves the web frontend and provides REST API endpoints.
 */
public class HttpApiServer {

    private final GannSquareService gannService;
    private final int port;
    private final String webRoot;

    public HttpApiServer(int port) {
        this.port = port;
        this.gannService = new GannSquareService(new PriceDataGateway());

        // Resolve web root relative to where the app is run from
        String userDir = System.getProperty("user.dir");
        this.webRoot = userDir + File.separator + "web";
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // API endpoints
        server.createContext("/api/gann", this::handleGannAnalysis);
        server.createContext("/api/klines", this::handleKlines);

        // Static file serving (must be last — catch-all)
        server.createContext("/", this::handleStaticFiles);

        server.setExecutor(null); // default executor
        server.start();

        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Gann Square of Time & Price Server        ║");
        System.out.println("║   Running on http://localhost:" + port + "          ║");
        System.out.println("╚══════════════════════════════════════════════╝");
    }

    // ── /api/gann ──────────────────────────────────────────────────

    private void handleGannAnalysis(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "text/plain", "Method not allowed");
            return;
        }

        String query = exchange.getRequestURI().getQuery();
        String symbol = getParam(query, "symbol", "BTC");
        String interval = getParam(query, "interval", "1d");
        int limit = Integer.parseInt(getParam(query, "limit", "180"));

        String json = gannService.runFullAnalysis(symbol, interval, limit);

        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        sendResponse(exchange, 200, "application/json", json);
    }

    // ── /api/klines ────────────────────────────────────────────────

    private void handleKlines(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "text/plain", "Method not allowed");
            return;
        }

        String query = exchange.getRequestURI().getQuery();
        String symbol = getParam(query, "symbol", "BTC");
        String interval = getParam(query, "interval", "1d");
        int limit = Integer.parseInt(getParam(query, "limit", "180"));

        String json = gannService.getRawKlines(symbol, interval, limit);

        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        sendResponse(exchange, 200, "application/json", json);
    }

    // ── Static file serving ────────────────────────────────────────

    private void handleStaticFiles(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/"))
            path = "/index.html";

        // Security: prevent directory traversal
        Path filePath = Paths.get(webRoot, path).normalize();
        if (!filePath.startsWith(webRoot)) {
            sendResponse(exchange, 403, "text/plain", "Forbidden");
            return;
        }

        File file = filePath.toFile();
        if (!file.exists() || !file.isFile()) {
            sendResponse(exchange, 404, "text/plain", "Not found: " + path);
            return;
        }

        String contentType = getContentType(path);
        byte[] bytes = Files.readAllBytes(filePath);

        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ── Utilities ──────────────────────────────────────────────────

    private void sendResponse(HttpExchange exchange, int code, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes("UTF-8");
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String getParam(String query, String key, String defaultValue) {
        if (query == null)
            return defaultValue;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv[0].equals(key) && kv.length == 2) {
                return kv[1];
            }
        }
        return defaultValue;
    }

    private String getContentType(String path) {
        if (path.endsWith(".html"))
            return "text/html; charset=utf-8";
        if (path.endsWith(".css"))
            return "text/css; charset=utf-8";
        if (path.endsWith(".js"))
            return "application/javascript; charset=utf-8";
        if (path.endsWith(".json"))
            return "application/json";
        if (path.endsWith(".png"))
            return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg"))
            return "image/jpeg";
        if (path.endsWith(".svg"))
            return "image/svg+xml";
        if (path.endsWith(".ico"))
            return "image/x-icon";
        return "application/octet-stream";
    }
}
