package master;

import common.Constants;
import common.Net;
import common.Task;
import common.TaskResult;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Découpage -> Load Balancing -> transfert TCP -> récupération des résultats.
 * Si un Worker tombe, ses tâches sont remises dans la file et redistribuées.
 */
public class TaskScheduler {
    private final WorkerManager workers;
    private final LoadBalancer balancer;
    private final Consumer<String> log;
    private final BlockingQueue<Task> pending = new LinkedBlockingQueue<>();
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "task-sender"); t.setDaemon(true); return t;
    });
    public final AtomicInteger running = new AtomicInteger(), completed = new AtomicInteger(), retried = new AtomicInteger();

    public TaskScheduler(WorkerManager workers, LoadBalancer balancer, Consumer<String> log) {
        this.workers = workers; this.balancer = balancer; this.log = log;
    }

    public int pendingCount() { return pending.size(); }

    public void start() {
        Thread t = new Thread(this::dispatchLoop, "dispatcher");
        t.setDaemon(true); t.start();
    }

    public void submit(Job job) {
        jobs.put(job.id, job);
        pending.addAll(job.tasks);
        log.accept("JOB       " + job.id + " reçu : " + job.width + "x" + job.height + " px, "
                + job.tasks.size() + " tâches");
        job.future.whenComplete((r, e) -> jobs.remove(job.id));
    }

    private void dispatchLoop() {
        while (true) {
            try {
                Task t = pending.take();
                Job job = jobs.get(t.jobId);
                if (job == null || job.future.isDone()) continue;
                WorkerState w;
                while ((w = balancer.pick(workers.all())) == null) Thread.sleep(100); // attend un Worker libre
                w.inFlight.incrementAndGet();
                running.incrementAndGet();
                final WorkerState target = w;
                pool.submit(() -> execute(t, job, target));
            } catch (InterruptedException e) { return; }
        }
    }

    private void execute(Task t, Job job, WorkerState w) {
        log.accept("DISPATCH  " + t.id + " -> " + w.id + " (charge " + Math.round(w.load() * 100) + " %)");
        try {
            TaskResult r = send(t, w);
            if (t.done.compareAndSet(false, true)) {
                job.store(t, r.pixels);
                t.pixels = null;
                w.completed.incrementAndGet();
                completed.incrementAndGet();
                log.accept("DONE      " + t.id + " terminée par " + w.id + " en " + r.durationMs + " ms");
            }
        } catch (IOException e) {
            workers.markFailed(w, "erreur TCP : " + e.getClass().getSimpleName());
            retry(t, job, w, "perte du Worker");
        } catch (Exception e) {
            retry(t, job, w, e.getMessage());
        } finally {
            w.inFlight.decrementAndGet();
            running.decrementAndGet();
        }
    }

    private void retry(Task t, Job job, WorkerState from, String why) {
        if (job.future.isDone()) return;
        if (t.attempts.incrementAndGet() >= Constants.MAX_ATTEMPTS) {
            job.fail("Tâche " + t.id + " abandonnée après " + Constants.MAX_ATTEMPTS + " essais");
            log.accept("ABORT     " + t.id + " abandonnée (" + why + ")");
            return;
        }
        retried.incrementAndGet();
        pending.add(t);
        log.accept("REASSIGN  " + t.id + " retirée de " + from.id + " (" + why + ") -> remise en file");
    }

    /** Envoi d'une bande au Worker par Socket TCP, et lecture du résultat sur la même connexion. */
    private TaskResult send(Task t, WorkerState w) throws IOException {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(w.host, w.port), 3000);
            s.setSoTimeout(120_000);
            w.sockets.add(s);
            try {
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream(), 1 << 16));
                out.writeUTF(t.id);
                out.writeInt(t.filter.ordinal());
                out.writeInt(t.param);
                out.writeInt(t.delayMs);
                out.writeInt(t.width);
                out.writeInt(t.rowCount);
                Net.writeInts(out, t.pixels);
                out.flush();

                DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream(), 1 << 16));
                int status = in.readInt();
                if (status != 0) throw new IllegalStateException("erreur Worker : " + in.readUTF());
                long dur = in.readLong();
                int[] px = Net.readInts(in, t.rowCount * t.width);
                return new TaskResult(t.id, w.id, px, dur);
            } finally {
                w.sockets.remove(s);
            }
        }
    }
}
