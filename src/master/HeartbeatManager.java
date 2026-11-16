package master;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Surveille les heartbeats et déclare FAILED les Workers silencieux. */
public class HeartbeatManager {
    private final WorkerManager workers;
    public HeartbeatManager(WorkerManager workers) { this.workers = workers; }

    public void start() {
        ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "heartbeat-monitor"); t.setDaemon(true); return t;
        });
        ses.scheduleAtFixedRate(workers::checkTimeouts, 1, 1, TimeUnit.SECONDS);
    }
}
