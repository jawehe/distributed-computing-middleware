package master;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import common.FilterType;
import common.Snapshot;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Backend HTTP du Dashboard (Java pur, sans dépendance).
 *   GET  /                      page du Dashboard (web/index.html)
 *   GET  /api/status            état des Workers, compteurs, historique (JSON)
 *   POST /api/jobs?filter=&param=&delay=&chunk=&name=   corps = fichier image (upload) -> crée un Job
 *   GET  /api/jobs/{id}         progression du Job (JSON)
 *   GET  /api/jobs/{id}/result  image résultat (PNG)
 * Le Job créé suit ensuite exactement le même chemin que pour un client TCP :
 * Master -> LoadBalancer -> Workers (TCP) -> assemblage.
 */
public class WebServer {
    private static class WebJob {
        final Job job; final String name, filter; final long start = System.currentTimeMillis();
        volatile byte[] png; volatile String error; volatile long end;
        WebJob(Job job, String name, String filter) { this.job = job; this.name = name; this.filter = filter; }
    }

    private final MasterImpl master;
    private final Map<String, WebJob> jobs = new ConcurrentHashMap<>();

    public WebServer(MasterImpl master) { this.master = master; }

    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::route);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "http"); t.setDaemon(true); return t;
        }));
        server.start();
    }

    private void route(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath(), method = ex.getRequestMethod();
            if (path.equals("/") && method.equals("GET")) page(ex);
            else if (path.equals("/api/status")) send(ex, 200, "application/json", statusJson());
            else if (path.equals("/api/jobs") && method.equals("POST")) submit(ex);
            else if (path.startsWith("/api/jobs/") && method.equals("GET")) jobInfo(ex, path);
            else send(ex, 404, "application/json", "{\"error\":\"Not found\"}");
        } catch (Exception e) {
            send(ex, 500, "application/json", "{\"error\":" + q(String.valueOf(e.getMessage())) + "}");
        }
    }

    private void page(HttpExchange ex) throws IOException {
        Path p = Paths.get("web", "index.html");
        if (!Files.exists(p)) {
            send(ex, 404, "text/plain; charset=utf-8", "web/index.html introuvable : lance le Master depuis le dossier du projet.");
            return;
        }
        sendBytes(ex, 200, "text/html; charset=utf-8", Files.readAllBytes(p));
    }

    /** Upload de l'image + paramètres -> création du Job -> envoi au Scheduler. */
    private void submit(HttpExchange ex) throws IOException {
        Map<String, String> p = query(ex);
        FilterType filter;
        try { filter = FilterType.valueOf(p.getOrDefault("filter", "BRIGHTNESS").toUpperCase()); }
        catch (IllegalArgumentException e) { send(ex, 400, "application/json", "{\"error\":\"Filtre inconnu\"}"); return; }
        int param = num(p, "param", 50), delay = Math.max(0, num(p, "delay", 0)), chunk = num(p, "chunk", 0);
        String name = safeName(p.getOrDefault("name", "resultat.png"));

        byte[] data = ex.getRequestBody().readAllBytes();
        BufferedImage img = data.length == 0 ? null : ImageIO.read(new ByteArrayInputStream(data));
        if (img == null) { send(ex, 400, "application/json", "{\"error\":\"Fichier image invalide (jpg, png, gif, bmp)\"}"); return; }

        int w = img.getWidth(), h = img.getHeight();
        int[] px = img.getRGB(0, 0, w, h, null, 0, w);
        if (chunk <= 0) chunk = Math.max(1, (int) Math.ceil(h / (Math.max(1, master.workers.aliveCount()) * 4.0)));

        Job job = new Job("J" + master.jobCounter.incrementAndGet(), px, w, h, filter, param, delay, chunk);
        WebJob wj = new WebJob(job, name, filter.name());
        jobs.put(job.id, wj);
        master.log("UPLOAD    Dashboard : " + name + " (" + data.length / 1024 + " Ko, filtre " + filter + ")");
        master.scheduler.submit(job);

        job.future.whenCompleteAsync((res, err) -> {
            wj.end = System.currentTimeMillis();
            if (err != null) { wj.error = err.getCause() != null ? err.getCause().getMessage() : err.getMessage(); return; }
            try {
                BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                out.setRGB(0, 0, w, h, res, 0, w);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ImageIO.write(out, "png", bos);
                Files.createDirectories(Paths.get("results"));
                Files.write(Paths.get("results", name), bos.toByteArray());   // fichier de sortie côté Master
                wj.png = bos.toByteArray();
                master.jobsDone.incrementAndGet();
                master.log("JOB       " + job.id + " terminé, résultat assemblé -> results/" + name);
            } catch (Exception e) { wj.error = "Assemblage impossible : " + e.getMessage(); }
        });
        send(ex, 200, "application/json", "{\"id\":" + q(job.id) + ",\"total\":" + job.total()
                + ",\"width\":" + w + ",\"height\":" + h + "}");
    }

    private void jobInfo(HttpExchange ex, String path) throws IOException {
        String[] parts = path.split("/");           // "", "api", "jobs", id, [result]
        WebJob wj = parts.length > 3 ? jobs.get(parts[3]) : null;
        if (wj == null) { send(ex, 404, "application/json", "{\"error\":\"Job inconnu\"}"); return; }
        if (parts.length > 4 && parts[4].equals("result")) {
            if (wj.png == null) send(ex, 404, "application/json", "{\"error\":\"Résultat pas encore prêt\"}");
            else sendBytes(ex, 200, "image/png", wj.png);
            return;
        }
        String state = wj.error != null ? "ERROR" : wj.png != null ? "DONE" : "RUNNING";
        long elapsed = (wj.end > 0 ? wj.end : System.currentTimeMillis()) - wj.start;
        send(ex, 200, "application/json", "{\"id\":" + q(wj.job.id) + ",\"state\":\"" + state + "\",\"done\":" + wj.job.done()
                + ",\"total\":" + wj.job.total() + ",\"elapsedMs\":" + elapsed + ",\"filter\":" + q(wj.filter)
                + ",\"name\":" + q(wj.name) + ",\"error\":" + (wj.error == null ? "null" : q(wj.error)) + "}");
    }

    private String statusJson() {
        Snapshot s = master.snapshot();
        StringBuilder sb = new StringBuilder("{\"workers\":[");
        for (int i = 0; i < s.workers.size(); i++) {
            Snapshot.WorkerRow w = s.workers.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"id\":").append(q(w.id)).append(",\"host\":").append(q(w.host + ":" + w.port))
              .append(",\"status\":\"").append(w.status).append("\",\"load\":").append(Math.round(w.load * 100))
              .append(",\"inFlight\":").append(w.inFlight).append(",\"capacity\":").append(w.capacity)
              .append(",\"completed\":").append(w.completed).append(",\"beatAgoMs\":").append(w.lastBeatAgoMs).append('}');
        }
        sb.append("],\"pending\":").append(s.pending).append(",\"running\":").append(s.running)
          .append(",\"completed\":").append(s.completed).append(",\"retried\":").append(s.retried)
          .append(",\"jobsDone\":").append(s.jobsDone).append(",\"history\":[");
        for (int i = 0; i < Math.min(60, s.history.size()); i++) { if (i > 0) sb.append(','); sb.append(q(s.history.get(i))); }
        return sb.append("]}").toString();
    }

    // ---------- utilitaires ----------
    private static Map<String, String> query(HttpExchange ex) {
        Map<String, String> m = new HashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null) return m;
        for (String kv : raw.split("&")) {
            String[] p = kv.split("=", 2);
            m.put(URLDecoder.decode(p[0], StandardCharsets.UTF_8), p.length > 1 ? URLDecoder.decode(p[1], StandardCharsets.UTF_8) : "");
        }
        return m;
    }
    private static int num(Map<String, String> m, String k, int def) {
        try { return Integer.parseInt(m.get(k).trim()); } catch (Exception e) { return def; }
    }
    /** Évite les chemins du type ../../x : on garde uniquement un nom de fichier .png. */
    private static String safeName(String n) {
        String s = Paths.get(n.isBlank() ? "resultat.png" : n).getFileName().toString().replaceAll("[^\\w.\\- ]", "_");
        return s.toLowerCase().endsWith(".png") ? s : s + ".png";
    }
    private static String q(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c == '\n') sb.append("\\n");
            else if (c < 0x20) sb.append(' ');
            else sb.append(c);
        }
        return sb.append('"').toString();
    }
    private static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        sendBytes(ex, code, type.contains("charset") ? type : type + "; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }
    private static void sendBytes(HttpExchange ex, int code, String type, byte[] b) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, b.length == 0 ? -1 : b.length);
        try (OutputStream os = ex.getResponseBody()) { if (b.length > 0) os.write(b); }
    }
}
