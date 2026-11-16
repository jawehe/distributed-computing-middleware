package common;

import java.rmi.Remote;
import java.rmi.RemoteException;

/** Interface RMI exposée par le Master (commandes, enregistrement, heartbeat). */
public interface MasterInterface extends Remote {
    void registerWorker(WorkerInterface worker) throws RemoteException;
    void unregisterWorker(String workerId) throws RemoteException;
    /** @return false si le Master ne connaît pas ce Worker (=> il doit se ré-enregistrer). */
    boolean heartbeat(String workerId, int activeTasks) throws RemoteException;
    Snapshot getSnapshot() throws RemoteException;
}
