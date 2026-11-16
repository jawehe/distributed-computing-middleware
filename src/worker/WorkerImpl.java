package worker;

import common.WorkerInterface;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/** Objet RMI du Worker : permet au Master de l'identifier et de le contacter en TCP. */
public class WorkerImpl extends UnicastRemoteObject implements WorkerInterface {
    private final String id, host;
    private final int tcpPort, capacity;

    public WorkerImpl(String id, String host, int tcpPort, int capacity) throws RemoteException {
        super();
        this.id = id; this.host = host; this.tcpPort = tcpPort; this.capacity = capacity;
    }
    @Override public String getId() { return id; }
    @Override public String getHost() { return host; }
    @Override public int getTcpPort() { return tcpPort; }
    @Override public int getCapacity() { return capacity; }
    @Override public String ping() { return "pong from " + id; }
}
