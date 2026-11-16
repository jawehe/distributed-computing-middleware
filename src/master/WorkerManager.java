package master;

import common.Constants;

import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Gère la liste des Workers (enregistrement dynamique, pannes). */
public class WorkerManager {
    private final Map<String, WorkerState> workers = new ConcurrentHashMap<>();
    private final Consumer<String> log;

    public WorkerManager(Consumer<String> log) { this.log = log; }

    public Collection<WorkerState> all() { return workers.values(); }

    public long aliveCount() { return workers.values().stream().filter(w -> !w.failed).count(); }

    public void register(common.WorkerInterface stub) throws RemoteException {
        WorkerState ws = new WorkerState(stub.getId(), stub.getHost(), stub.getTcpPort(), stub.getCapacity(), stub);
        WorkerState old = workers.put(ws.id, ws);
        if (old != null) old.abortConnections();
        log.accept("REGISTER  " + ws.id + " enregistré via RMI (" + ws.host + ":" + ws.port
                + ", capacité " + ws.capacity + ")");
    }

    public void unregister(String id) {
        WorkerState ws = workers.remove(id);
        if (ws != null) { ws.abortConnections(); log.accept("UNREGISTER " + id + " s'est déconnecté proprement"); }
    }

    public boolean heartbeat(String id, int active) {
        WorkerState ws = workers.get(id);
        if (ws == null) return false;
        ws.lastBeat = System.currentTimeMillis();
        ws.reportedActive = active;
        if (ws.failed) { ws.failed = false; log.accept("RECOVERED " + id + " envoie à nouveau des heartbeats -> ONLINE"); }
        return true;
    }

    public synchronized void markFailed(WorkerState ws, String reason) {
        if (ws.failed) return;
        ws.failed = true;
        log.accept("FAILED    " + ws.id + " déclaré en panne (" + reason + ")");
        ws.abortConnections();
    }

    /** Appelé périodiquement par le HeartbeatManager. */
    public void checkTimeouts() {
        long now = System.currentTimeMillis();
        for (WorkerState ws : workers.values()) {
            long silence = now - ws.lastBeat;
            if (!ws.failed && silence > Constants.HEARTBEAT_TIMEOUT_MS)
                markFailed(ws, "aucun heartbeat depuis " + silence / 1000 + " s");
        }
    }
}
