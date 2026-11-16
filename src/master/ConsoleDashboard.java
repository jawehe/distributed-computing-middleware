package master;

import common.Snapshot;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Dashboard texte (utilisé si pas d'écran, ou avec --console). */
public class ConsoleDashboard {
    public static void start(MasterImpl m) {
        ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "console-dashboard"); t.setDaemon(true); return t;
        });
        ses.scheduleAtFixedRate(() -> {
            Snapshot s = m.snapshot();
            StringBuilder sb = new StringBuilder("\n========== DASHBOARD ==========\n");
            sb.append(String.format("Workers: %d | En attente: %d | En cours: %d | Terminées: %d | Réassignées: %d | Jobs: %d%n",
                    s.workers.size(), s.pending, s.running, s.completed, s.retried, s.jobsDone));
            for (Snapshot.WorkerRow w : s.workers)
                sb.append(String.format("  %-10s %-7s charge %3d%%  en cours %d/%d  terminées %-3d  dernier HB il y a %.1fs%n",
                        w.id, w.status, Math.round(w.load * 100), w.inFlight, w.capacity, w.completed, w.lastBeatAgoMs / 1000.0));
            sb.append("===============================");
            System.out.println(sb);
        }, 3, 3, TimeUnit.SECONDS);
    }
}
