package common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Photo de l'état du système, utilisée par le Dashboard. */
public class Snapshot implements Serializable {
    public List<WorkerRow> workers = new ArrayList<>();
    public int pending, running, completed, retried, jobsDone;
    public List<String> history = new ArrayList<>();

    public static class WorkerRow implements Serializable {
        public String id, host;
        public int port, capacity, inFlight, completed;
        public double load;           // 0..1
        public WorkerStatus status;
        public long lastBeatAgoMs;
    }
}
