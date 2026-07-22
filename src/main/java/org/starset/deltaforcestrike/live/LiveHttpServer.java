package org.starset.deltaforcestrike.live;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.starset.deltaforcestrike.DeltaForceStrike;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Token 鉴权的导播 HTTP 服务。
 * <ul>
 *   <li>GET /api/live — JSON 快照</li>
 *   <li>GET / /overlay — HTML 覆盖层</li>
 *   <li>GET /health — 无需 token</li>
 * </ul>
 */
public final class LiveHttpServer {

    private final DeltaForceStrike plugin;
    private final LiveSnapshotService snapshots;
    private HttpServer server;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public LiveHttpServer(DeltaForceStrike plugin, LiveSnapshotService snapshots) {
        this.plugin = plugin;
        this.snapshots = snapshots;
    }

    public synchronized void start() {
        stop();
        if (!plugin.getConfig().getBoolean("live.enabled", false)) {
            plugin.getLogger().info("[Live] 导播接口未启用 (live.enabled=false)");
            return;
        }
        String token = plugin.getConfig().getString("live.token", "");
        if (token == null || token.isBlank()) {
            plugin.getLogger().warning("[Live] live.token 为空，拒绝启动（请设置强 token）");
            return;
        }

        int port = plugin.getConfig().getInt("live.port", 8765);
        String bind = plugin.getConfig().getString("live.bind", "0.0.0.0");
        try {
            server = HttpServer.create(new InetSocketAddress(bind, port), 0);
            server.createContext("/health", this::handleHealth);
            server.createContext("/api/live", this::handleApi);
            server.createContext("/overlay", this::handleOverlay);
            server.createContext("/", this::handleRoot);
            server.setExecutor(Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "dfs-live-http");
                t.setDaemon(true);
                return t;
            }));
            server.start();
            running.set(true);
            plugin.getLogger().info("[Live] 导播接口已启动 http://" + bind + ":" + port
                    + "  (鉴权: Bearer / ?token=)");
        } catch (IOException e) {
            plugin.getLogger().severe("[Live] 启动失败: " + e.getMessage());
            server = null;
            running.set(false);
        }
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        running.set(false);
    }

    public boolean isRunning() {
        return running.get();
    }

    private void handleHealth(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            send(ex, 405, "text/plain; charset=utf-8", "Method Not Allowed");
            return;
        }
        send(ex, 200, "application/json; charset=utf-8",
                "{\"ok\":true,\"live\":" + running.get() + "}");
    }

    private void handleApi(HttpExchange ex) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            addCors(ex.getResponseHeaders());
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())
                && !"HEAD".equalsIgnoreCase(ex.getRequestMethod())) {
            send(ex, 405, "text/plain; charset=utf-8", "Method Not Allowed");
            return;
        }
        if (!authorize(ex)) {
            send(ex, 401, "application/json; charset=utf-8",
                    "{\"error\":\"unauthorized\"}");
            return;
        }
        addCors(ex.getResponseHeaders());
        String json = snapshots.getJson();
        if ("HEAD".equalsIgnoreCase(ex.getRequestMethod())) {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(200, bytes.length);
            ex.close();
            return;
        }
        send(ex, 200, "application/json; charset=utf-8", json);
    }

    private void handleOverlay(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            send(ex, 405, "text/plain; charset=utf-8", "Method Not Allowed");
            return;
        }
        if (!authorize(ex)) {
            send(ex, 401, "text/html; charset=utf-8",
                    "<html><body style='background:#111;color:#f66;font-family:sans-serif'>"
                            + "<h2>Unauthorized</h2><p>需要 ?token= 或 Authorization: Bearer</p>"
                            + "</body></html>");
            return;
        }
        send(ex, 200, "text/html; charset=utf-8", LiveOverlayHtml.page());
    }

    private void handleRoot(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path == null || path.equals("/") || path.isEmpty()) {
            handleOverlay(ex);
            return;
        }
        send(ex, 404, "text/plain; charset=utf-8", "Not Found");
    }

    private boolean authorize(HttpExchange ex) {
        String expected = plugin.getConfig().getString("live.token", "");
        if (expected == null || expected.isBlank()) {
            return false;
        }
        // Header
        List<String> auth = ex.getRequestHeaders().get("Authorization");
        if (auth != null) {
            for (String h : auth) {
                if (h == null) {
                    continue;
                }
                String v = h.trim();
                if (v.regionMatches(true, 0, "Bearer ", 0, 7)) {
                    String t = v.substring(7).trim();
                    if (expected.equals(t)) {
                        return true;
                    }
                }
                if (expected.equals(v)) {
                    return true;
                }
            }
        }
        // Query
        String q = ex.getRequestURI().getRawQuery();
        if (q != null) {
            for (String part : q.split("&")) {
                int i = part.indexOf('=');
                if (i <= 0) {
                    continue;
                }
                String k = part.substring(0, i);
                String v = java.net.URLDecoder.decode(part.substring(i + 1), StandardCharsets.UTF_8);
                if ("token".equalsIgnoreCase(k) && expected.equals(v)) {
                    return true;
                }
            }
        }
        // Optional header X-DFS-Token
        List<String> xt = ex.getRequestHeaders().get("X-DFS-Token");
        if (xt != null) {
            for (String h : xt) {
                if (expected.equals(h)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void addCors(Headers h) {
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Headers", "Authorization, X-DFS-Token, Content-Type");
        h.set("Cache-Control", "no-store");
    }

    private static void send(HttpExchange ex, int code, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", contentType);
        addCors(h);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
