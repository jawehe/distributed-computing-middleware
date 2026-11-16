package common;

/** Constantes partagées (modifiables via -D pour les tests). */
public final class Constants {
    public static final int RMI_PORT = Integer.getInteger("rmi.port", 1099);
    public static final int DATA_PORT = Integer.getInteger("data.port", 5000);
    public static final String MASTER_NAME = "MasterService";
    /** Un Worker envoie un heartbeat toutes les 3 s. */
    public static final long HEARTBEAT_INTERVAL_MS = Long.getLong("hb.interval", 3000);
    /** Sans heartbeat pendant 10 s => FAILED. */
    public static final long HEARTBEAT_TIMEOUT_MS = Long.getLong("hb.timeout", 10000);
    public static final int MAX_ATTEMPTS = 5;
    private Constants() {}
}
