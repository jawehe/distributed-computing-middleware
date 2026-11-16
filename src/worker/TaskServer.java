package worker;

import common.FilterType;
import common.Net;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Serveur TCP du Worker : reçoit les bandes d'image, les traite, renvoie le résultat. */
public class TaskServer {
    private final ServerSocket server;
    private final String workerId;
    private final int crashAfter;
    public final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger received = new AtomicInteger();
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "task-handler"); t.setDaemon(true); return t;
    });

    public TaskServer(int port, String workerId, int crashAfter) throws IOException {
        this.server = new ServerSocket(port);
        this.workerId = workerId;
        this.crashAfter = crashAfter;
    }

    public int port() { return server.getLocalPort(); }

    public void start() {
        Thread t = new Thread(() -> {
            while (true) {
                try { Socket s = server.accept(); pool.submit(() -> handle(s)); }
                catch (IOException e) { return; }
            }
        }, "tcp-accept");
        t.setDaemon(true); t.start();
    }

    private void handle(Socket s) {
        try (Socket sock = s) {
            DataInputStream in = new DataInputStream(new BufferedInputStream(sock.getInputStream(), 1 << 16));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream(), 1 << 16));
            String taskId = in.readUTF();
            FilterType filter = FilterType.values()[in.readInt()];
            int param = in.readInt(), delay = in.readInt(), width = in.readInt(), rows = in.readInt();
            int[] pixels = Net.readInts(in, width * rows);

            active.incrementAndGet();
            try {
                int n = received.incrementAndGet();
                System.out.println("[" + workerId + "] tâche " + taskId + " reçue (" + rows + " lignes, " + filter + ")");
                if (crashAfter > 0 && n >= crashAfter) {
                    System.out.println("[" + workerId + "] *** PANNE SIMULÉE (--crash-after=" + crashAfter + ") ***");
                    Runtime.getRuntime().halt(1);   // arrêt brutal : pas de réponse, plus de heartbeat
                }
                long t0 = System.currentTimeMillis();
                try {
                    int[] res = TaskExecutor.run(filter, param, pixels, delay);
                    out.writeInt(0);
                    out.writeLong(System.currentTimeMillis() - t0);
                    Net.writeInts(out, res);
                } catch (Exception e) {
                    out.writeInt(1);
                    out.writeUTF(String.valueOf(e.getMessage()));
                }
                out.flush();
                System.out.println("[" + workerId + "] tâche " + taskId + " terminée, résultat renvoyé");
            } finally { active.decrementAndGet(); }
        } catch (IOException e) {
            System.out.println("[" + workerId + "] connexion interrompue : " + e.getMessage());
        }
    }
}
