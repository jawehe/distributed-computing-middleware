package worker;

import common.Constants;
import common.MasterInterface;

import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Usage : java worker.WorkerMain [IP_MASTER] [--id=W1] [--host=MON_IP] [--port=0]
 *                                [--threads=2] [--crash-after=N]
 */
public class WorkerMain {
    public static void main(String[] args) throws Exception {
        String masterHost = "localhost", id = "worker-" + UUID.randomUUID().toString().substring(0, 4), host = null;
        int port = 0, threads = 2, crashAfter = 0;
        for (String a : args) {
            if (a.startsWith("--id=")) id = a.substring(5);
            else if (a.startsWith("--host=")) host = a.substring(7);
            else if (a.startsWith("--port=")) port = Integer.parseInt(a.substring(7));
            else if (a.startsWith("--threads=")) threads = Integer.parseInt(a.substring(10));
            else if (a.startsWith("--crash-after=")) crashAfter = Integer.parseInt(a.substring(14));
            else if (!a.startsWith("--")) masterHost = a;
        }
        if (host == null)
            host = masterHost.equals("localhost") || masterHost.equals("127.0.0.1")
                    ? "127.0.0.1" : InetAddress.getLocalHost().getHostAddress();
        System.setProperty("java.rmi.server.hostname", host);

        TaskServer tcp = new TaskServer(port, id, crashAfter);
        tcp.start();
        final WorkerImpl impl = new WorkerImpl(id, host, tcp.port(), threads);
        final String wid = id;

        final MasterInterface[] master = { lookup(masterHost) };
        master[0].registerWorker(impl);
        System.out.println("[" + id + "] enregistré auprès du Master " + masterHost + " (TCP " + host + ":" + tcp.port()
                + ", " + threads + " threads)");

        // Heartbeat : signal de vie toutes les 3 s
        ScheduledExecutorService hb = Executors.newSingleThreadScheduledExecutor();
        final String mh = masterHost;
        hb.scheduleAtFixedRate(() -> {
            try {
                if (!master[0].heartbeat(wid, tcp.active.get())) {
                    System.out.println("[" + wid + "] Master ne me connaît plus -> ré-enregistrement");
                    master[0].registerWorker(impl);
                }
            } catch (Exception e) {
                System.out.println("[" + wid + "] Master injoignable, nouvelle tentative...");
                try { master[0] = lookup(mh); master[0].registerWorker(impl); } catch (Exception ignored) { }
            }
        }, Constants.HEARTBEAT_INTERVAL_MS, Constants.HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { master[0].unregisterWorker(wid); } catch (Exception ignored) { }
        }));
        Thread.currentThread().join();
    }

    private static MasterInterface lookup(String host) throws Exception {
        Registry reg = LocateRegistry.getRegistry(host, Constants.RMI_PORT);
        return (MasterInterface) reg.lookup(Constants.MASTER_NAME);
    }
}
