package master;

import java.util.Collection;
import java.util.Comparator;

/** Algorithme "Least Loaded Worker" : le Worker le moins chargé qui a encore de la place. */
public class LoadBalancer {
    public WorkerState pick(Collection<WorkerState> workers) {
        return workers.stream()
                .filter(w -> !w.failed && w.inFlight.get() < w.capacity)
                .min(Comparator.comparingDouble(WorkerState::load)
                        .thenComparingInt(w -> w.completed.get()))   // à charge égale : le moins sollicité
                .orElse(null);
    }
}
