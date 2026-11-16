package master;

import common.FilterType;
import common.Task;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** Une demande de calcul = une image découpée en bandes (tâches). */
public class Job {
    public final String id;
    public final int width, height;
    public final int[] out;
    public final List<Task> tasks = new ArrayList<>();
    public final CompletableFuture<int[]> future = new CompletableFuture<>();
    private final AtomicInteger remaining;

    public Job(String id, int[] pixels, int width, int height, FilterType f, int param, int delayMs, int chunkRows) {
        this.id = id; this.width = width; this.height = height; this.out = new int[pixels.length];
        int n = 0;
        for (int r = 0; r < height; r += chunkRows) {
            int rows = Math.min(chunkRows, height - r);
            int[] strip = Arrays.copyOfRange(pixels, r * width, (r + rows) * width);
            tasks.add(new Task(id + "-T" + (++n), id, r, rows, width, f, param, delayMs, strip));
        }
        remaining = new AtomicInteger(tasks.size());
    }

    /** Assemblage : place le résultat d'une bande à sa position dans l'image finale. */
    public void store(Task t, int[] px) {
        System.arraycopy(px, 0, out, t.rowStart * width, px.length);
        if (remaining.decrementAndGet() == 0) future.complete(out);
    }

    public int total() { return tasks.size(); }
    public int done() { return tasks.size() - remaining.get(); }

    public void fail(String msg) { future.completeExceptionally(new RuntimeException(msg)); }
}
