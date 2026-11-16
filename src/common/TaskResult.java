package common;

public class TaskResult {
    public final String taskId, workerId;
    public final int[] pixels;
    public final long durationMs;

    public TaskResult(String taskId, String workerId, int[] pixels, long durationMs) {
        this.taskId = taskId; this.workerId = workerId; this.pixels = pixels; this.durationMs = durationMs;
    }
}
