package master;

import common.Constants;

import java.awt.GraphicsEnvironment;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Usage : java master.MasterMain [--host=IP_DU_MASTER] [--console]
 * --host est nécessaire quand les Workers sont sur d'autres machines.
 */
public class MasterMain {
    public static void main(String[] args) throws Exception {
        boolean console = false;
        for (String a : args) {
            if (a.startsWith("--host=")) System.setProperty("java.rmi.server.hostname", a.substring(7));
            if (a.equals("--console")) console = true;
        }
        MasterImpl master = new MasterImpl();
        Registry registry = LocateRegistry.createRegistry(Constants.RMI_PORT);
        registry.rebind(Constants.MASTER_NAME, master);
        master.log("MASTER    démarré, registre RMI sur le port " + Constants.RMI_PORT);

        master.scheduler.start();
        new HeartbeatManager(master.workers).start();
        Thread t = new Thread(new ClientListener(master), "client-listener");
        t.setDaemon(true); t.start();

        // Dashboard web : formulaire "Nouvelle tâche" (upload image + paramètres), progression, résultat
        int webPort = Integer.getInteger("web.port", 8080);
        new WebServer(master).start(webPort);
        String url = "http://localhost:" + webPort;
        master.log("DASHBOARD web disponible : " + url);
        try {
            if (!GraphicsEnvironment.isHeadless() && java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE))
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (Exception ignored) { }

        if (console) {
            ConsoleDashboard.start(master);
        } else if (GraphicsEnvironment.isHeadless()) {
            master.log("DASHBOARD Java est en mode headless (java.awt.headless=true) : fenêtre impossible -> dashboard texte.");
            master.log("DASHBOARD Causes possibles : JDK/JRE 'headless', WSL, SSH, ou -Djava.awt.headless=true dans la config.");
            ConsoleDashboard.start(master);
        } else {
            javax.swing.SwingUtilities.invokeLater(() -> {
                try {
                    DashboardFrame f = new DashboardFrame(master);
                    f.setLocationRelativeTo(null);
                    f.setVisible(true);
                    f.toFront();
                    f.setAlwaysOnTop(true);   // passe devant l'IDE
                    f.setAlwaysOnTop(false);
                    master.log("DASHBOARD fenêtre graphique ouverte (regarde dans la barre des tâches si elle est cachée).");
                } catch (Throwable e) {
                    master.log("DASHBOARD fenêtre impossible (" + e + ") -> dashboard texte.");
                    ConsoleDashboard.start(master);
                }
            });
        }
        Thread.currentThread().join();
    }
}
