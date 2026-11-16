package master;

import common.Constants;
import common.FilterType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reçoit les demandes des clients par Socket TCP (le fichier image peut être volumineux),
 * lance le Job puis renvoie l'image finale sur la même connexion.
 */
public class ClientListener implements Runnable {
    private final MasterImpl master;
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "client-handler"); t.setDaemon(true); return t;
    });
    private final AtomicInteger counter = new AtomicInteger();

    public ClientListener(MasterImpl master) { this.master = master; }

    @Override public void run() {
        try (ServerSocket ss = new ServerSocket(Constants.DATA_PORT)) {
            master.log("MASTER    écoute les clients (TCP) sur le port " + Constants.DATA_PORT);
            while (true) {
                Socket s = ss.accept();
                pool.submit(() -> handle(s));
            }
        } catch (IOException e) { master.log("ERREUR    ClientListener : " + e); }
    }

    private void handle(Socket s) {
        try (Socket sock = s) {
            DataInputStream in = new DataInputStream(new BufferedInputStream(sock.getInputStream(), 1 << 16));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream(), 1 << 16));
            try {
                FilterType filter = FilterType.valueOf(in.readUTF());
                int param = in.readInt(), delay = in.readInt(), chunk = in.readInt();
                byte[] data = new byte[in.readInt()];
                in.readFully(data);

                BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
                if (img == null) throw new IllegalArgumentException("fichier image invalide");
                int w = img.getWidth(), h = img.getHeight();
                int[] px = img.getRGB(0, 0, w, h, null, 0, w);
                if (chunk <= 0) chunk = Math.max(1, (int) Math.ceil(h / (Math.max(1, master.workers.aliveCount()) * 4.0)));

                Job job = new Job("J" + master.jobCounter.incrementAndGet(), px, w, h, filter, param, delay, chunk);
                master.scheduler.submit(job);
                int[] result = job.future.get(10, TimeUnit.MINUTES);

                BufferedImage outImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                outImg.setRGB(0, 0, w, h, result, 0, w);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ImageIO.write(outImg, "png", bos);
                master.jobsDone.incrementAndGet();
                master.log("JOB       " + job.id + " terminé, résultat assemblé (" + bos.size() / 1024 + " Ko)");
                out.writeInt(0);
                out.writeInt(bos.size());
                out.write(bos.toByteArray());
            } catch (Exception e) {
                Throwable c = e.getCause() != null ? e.getCause() : e;
                master.log("ERREUR    job : " + c.getMessage());
                out.writeInt(1);
                out.writeUTF(String.valueOf(c.getMessage()));
            }
            out.flush();
        } catch (IOException ignored) { }
    }
}
