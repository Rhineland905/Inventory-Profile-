package dev.schematicprogress;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;

/**
 * A very small HTTP server (no extra libraries) for the dashboard.
 * Listens on 127.0.0.1 unless allowLan=true in the config.
 */
final class WebServer {
    private WebServer() {}

    static volatile int port = -1;
    private static byte[] page;
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "SchematicProgress-web");
        t.setDaemon(true);
        return t;
    });

    static void start() {
        try (InputStream in = WebServer.class.getResourceAsStream("/assets/schematicprogress/web/index.html")) {
            page = in == null ? "dashboard missing".getBytes(StandardCharsets.UTF_8) : in.readAllBytes();
        } catch (IOException e) {
            page = "dashboard missing".getBytes(StandardCharsets.UTF_8);
        }
        for (int p = Config.port; p < Config.port + 10; p++) {
            try {
                ServerSocket server = new ServerSocket();
                server.setReuseAddress(true);
                InetAddress addr = Config.allowLan ? null : InetAddress.getLoopbackAddress();
                server.bind(addr == null ? new InetSocketAddress(p) : new InetSocketAddress(addr, p), 32);
                port = p;
                Thread t = new Thread(() -> acceptLoop(server), "SchematicProgress-accept");
                t.setDaemon(true);
                t.start();
                SchematicProgressClient.LOG.info("Schematic Progress dashboard: http://localhost:{}/", p);
                return;
            } catch (IOException e) {
                SchematicProgressClient.LOG.warn("Port {} is busy: {}", p, e.getMessage());
            }
        }
    }

    static String url() {
        return "http://localhost:" + port + "/";
    }

    private static void acceptLoop(ServerSocket server) {
        while (true) {
            try {
                Socket s = server.accept();
                POOL.execute(() -> handle(s));
            } catch (IOException e) {
                SchematicProgressClient.LOG.warn("Web server stopped", e);
                return;
            }
        }
    }

    private static void handle(Socket socket) {
        try (socket) {
            socket.setSoTimeout(5000);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String line = in.readLine();
            if (line == null) return;
            String[] parts = line.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String target = parts[1];
            String h;
            while ((h = in.readLine()) != null && !h.isEmpty()) { /* headers are not needed */ }
            String path = target;
            Map<String, String> q = new HashMap<>();
            int qi = target.indexOf('?');
            if (qi >= 0) {
                path = target.substring(0, qi);
                for (String kv : target.substring(qi + 1).split("&")) {
                    int e = kv.indexOf('=');
                    if (e > 0) q.put(dec(kv.substring(0, e)), dec(kv.substring(e + 1)));
                    else if (!kv.isEmpty()) q.put(dec(kv), "");
                }
            }
            OutputStream out = socket.getOutputStream();
            if (path.equals("/") || path.equals("/index.html")) {
                send(out, 200, "text/html; charset=utf-8", page);
            } else if (path.equals("/api/state")) {
                json(out, Tracker.stateJson());
            } else if (path.equals("/api/layer")) {
                json(out, Tracker.layerJson(intOr(q.get("y"), -1)));
            } else if (path.equals("/api/files")) {
                json(out, filesJson());
            } else if (path.startsWith("/api/") && method.equals("POST")) {
                json(out, command(path.substring(5), q));
            } else {
                send(out, 404, "text/plain; charset=utf-8", "not found".getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            // client went away; nothing to do
        }
    }

    private static String command(String cmd, Map<String, String> q) {
        Minecraft mc = Minecraft.getInstance();
        switch (cmd) {
            case "load" -> {
                Path dir = Config.schematicsDir().toAbsolutePath().normalize();
                Path file = dir.resolve(q.getOrDefault("file", "")).normalize();
                if (!file.startsWith(dir) || !Files.isRegularFile(file)) return err("Файл не найден");
                try {
                    Schematic s = Schematic.load(file); // parsing happens here, off the game thread
                    mc.execute(() -> Tracker.activate(s, file));
                    return ok();
                } catch (Exception e) {
                    Tracker.lastError = "Не удалось прочитать " + file.getFileName() + ": " + e.getMessage();
                    return err(Tracker.lastError);
                }
            }
            case "unload" -> mc.execute(Tracker::unload);
            case "here" -> mc.execute(Tracker::originHere);
            case "origin" -> {
                int x = intOr(q.get("x"), Integer.MIN_VALUE), y = intOr(q.get("y"), Integer.MIN_VALUE), z = intOr(q.get("z"), Integer.MIN_VALUE);
                if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || z == Integer.MIN_VALUE) return err("Нужны x, y, z");
                mc.execute(() -> Tracker.setOrigin(x, y, z));
            }
            case "nudge" -> {
                int dx = intOr(q.get("dx"), 0), dy = intOr(q.get("dy"), 0), dz = intOr(q.get("dz"), 0);
                mc.execute(() -> Tracker.nudge(dx, dy, dz));
            }
            case "rotate" -> {
                int r = intOr(q.get("r"), 0);
                mc.execute(() -> Tracker.rotate(r));
            }
            case "layer" -> {
                int y = intOr(q.get("y"), -1); // -1 = auto
                mc.execute(() -> Tracker.setLayer(y));
            }
            default -> { return err("Неизвестная команда"); }
        }
        return ok();
    }

    private static String filesJson() {
        Path dir = Config.schematicsDir();
        List<String> names = new ArrayList<>();
        try {
            Files.createDirectories(dir);
            try (Stream<Path> walk = Files.walk(dir, 6)) {
                walk.filter(Files::isRegularFile).forEach(p -> {
                    String n = p.getFileName().toString().toLowerCase();
                    if (n.endsWith(".litematic") || n.endsWith(".schem") || n.endsWith(".schematic") || n.endsWith(".nbt"))
                        names.add(dir.relativize(p).toString().replace('\\', '/'));
                });
            }
        } catch (IOException ignored) {}
        names.sort(String.CASE_INSENSITIVE_ORDER);
        Json j = new Json().open().kv("dir", dir.toAbsolutePath().normalize().toString()).key("files").arr();
        for (String n : names) j.v(n);
        return j.end().close().toString();
    }

    private static String ok() { return "{\"ok\":true}"; }

    private static String err(String m) { return new Json().open().kv("ok", false).kv("error", m).close().toString(); }

    private static void json(OutputStream out, String body) throws IOException {
        send(out, 200, "application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(OutputStream out, int code, String type, byte[] body) throws IOException {
        String head = "HTTP/1.1 " + code + (code == 200 ? " OK" : " Not Found") + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private static String dec(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private static int intOr(String s, int def) {
        try {
            return s == null ? def : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
