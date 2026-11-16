package master;

import common.WorkerInterface;
import common.WorkerStatus;

import java.io.IOException;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Ce que le Master sait d'un Worker. */
public class WorkerState {
    public final String id, host;
    public final int port, capacity;
    public final WorkerInterface stub;
    public volatile long lastBeat = System.currentTimeMillis();
    public volatile boolean failed = false;
    public volatile int reportedActive = 0;
    public final AtomicInteger inFlight = new AtomicInteger();
    public final AtomicInteger completed = new AtomicInteger();
    /** Connexions TCP en cours : fermées si le Worker est déclaré FAILED. */
    public final Set<Socket> sockets = ConcurrentHashMap.newKeySet();

    public WorkerState(String id, String host, int port, int capacity, WorkerInterface stub) {
        this.id = id; this.host = host; this.port = port; this.capacity = capacity; this.stub = stub;
    }

    public double load() { return Math.min(1.0, (double) inFlight.get() / capacity); }

    public WorkerStatus status() {
        if (failed) return WorkerStatus.FAILED;
        return inFlight.get() >= capacity ? WorkerStatus.BUSY : WorkerStatus.ONLINE;
    }

    public void abortConnections() {
        for (Socket s : sockets) {
            try { s.close(); } catch (IOException ignored) { }
        }
    }
}
