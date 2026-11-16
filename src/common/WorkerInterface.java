package common;

import java.rmi.Remote;
import java.rmi.RemoteException;

/** Interface RMI exposée par chaque Worker. */
public interface WorkerInterface extends Remote {
    String getId() throws RemoteException;
    String getHost() throws RemoteException;
    /** Port TCP sur lequel le Worker reçoit les données volumineuses. */
    int getTcpPort() throws RemoteException;
    /** Nombre de tâches pouvant s'exécuter en parallèle. */
    int getCapacity() throws RemoteException;
    String ping() throws RemoteException;
}
