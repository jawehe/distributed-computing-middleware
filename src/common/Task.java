package common;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Une tâche = une bande horizontale de l'image à traiter par un Worker. */
public class Task {
    public final String id, jobId;
    public final int rowStart, rowCount, width;
    public final FilterType filter;
    public final int param, delayMs;
    public volatile int[] pixels;
    public final AtomicInteger attempts = new AtomicInteger();
    public final AtomicBoolean done = new AtomicBoolean();

    public Task(String id, String jobId, int rowStart, int rowCount, int width,
                FilterType filter, int param, int delayMs, int[] pixels) {
        this.id = id; this.jobId = jobId; this.rowStart = rowStart; this.rowCount = rowCount;
        this.width = width; this.filter = filter; this.param = param; this.delayMs = delayMs;
        this.pixels = pixels;
    }
}
