package master;

import common.*;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.time.LocalTime;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/** Le Master : objet RMI + point central (WorkerManager, LoadBalancer, Scheduler). */
public class MasterImpl extends UnicastRemoteObject implements MasterInterface {
    public final WorkerManager workers;
    public final LoadBalancer balancer = new LoadBalancer();
    public final TaskScheduler scheduler;
    public final AtomicInteger jobsDone = new AtomicInteger();
    /** Compteur partagé (clients TCP + Dashboard web) pour des identifiants de job uniques. */
    public final AtomicInteger jobCounter = new AtomicInteger();
    private final Deque<String> history = new ConcurrentLinkedDeque<>();

    public MasterImpl() throws RemoteException {
        super();
        workers = new WorkerManager(this::log);
        scheduler = new TaskScheduler(workers, balancer, this::log);
    }

    public void log(String msg) {
        String line = LocalTime.now().withNano(0) + "  " + msg;
        history.addFirst(line);
        while (history.size() > 300) history.pollLast();
        System.out.println(line);
    }

    @Override public void registerWorker(WorkerInterface w) throws RemoteException { workers.register(w); }
    @Override public void unregisterWorker(String id) { workers.unregister(id); }
    @Override public boolean heartbeat(String id, int active) { return workers.heartbeat(id, active); }
    @Override public Snapshot getSnapshot() { return snapshot(); }

    public Snapshot snapshot() {
        Snapshot s = new Snapshot();
        long now = System.currentTimeMillis();
        for (WorkerState w : workers.all()) {
            Snapshot.WorkerRow r = new Snapshot.WorkerRow();
            r.id = w.id; r.host = w.host; r.port = w.port; r.capacity = w.capacity;
            r.inFlight = w.inFlight.get(); r.completed = w.completed.get(); r.load = w.load();
            r.status = w.status(); r.lastBeatAgoMs = now - w.lastBeat;
            s.workers.add(r);
        }
        s.workers.sort((a, b) -> a.id.compareTo(b.id));
        s.pending = scheduler.pendingCount();
        s.running = scheduler.running.get();
        s.completed = scheduler.completed.get();
        s.retried = scheduler.retried.get();
        s.jobsDone = jobsDone.get();
        s.history.addAll(history);
        return s;
    }
}
