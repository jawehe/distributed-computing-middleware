package client;

import common.Constants;
import common.FilterType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Usage : java client.ClientMain <masterHost> <image|gen:LxH> <sortie.png> <FILTRE> [param] [delayMs] [chunkRows]
 * FILTRE : BRIGHTNESS | GRAYSCALE | INVERT | SEPIA
 */
public class ClientMain {
    public static void main(String[] a) throws Exception {
        if (a.length < 4) {
            System.out.println("Usage: ClientMain <masterHost> <image|gen:1200x800> <sortie.png> <BRIGHTNESS|GRAYSCALE|INVERT|SEPIA> [param=50] [delayMs=0] [chunkRows=auto]");
            return;
        }
        String host = a[0], in = a[1], outFile = a[2];
        FilterType filter = FilterType.valueOf(a[3].toUpperCase());
        int param = a.length > 4 ? Integer.parseInt(a[4]) : 50;
        int delay = a.length > 5 ? Integer.parseInt(a[5]) : 0;
        int chunk = a.length > 6 ? Integer.parseInt(a[6]) : 0;

        byte[] data;
        if (in.startsWith("gen:")) {
            String[] d = in.substring(4).split("x");
            data = generate(Integer.parseInt(d[0]), Integer.parseInt(d[1]));
            System.out.println("Image de test générée : " + in.substring(4));
        } else data = Files.readAllBytes(Paths.get(in));

        long t0 = System.currentTimeMillis();
        try (Socket s = new Socket(host, Constants.DATA_PORT)) {
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream(), 1 << 16));
            out.writeUTF(filter.name());
            out.writeInt(param); out.writeInt(delay); out.writeInt(chunk);
            out.writeInt(data.length);
            out.write(data);
            out.flush();
            System.out.println("Image envoyée au Master (" + data.length / 1024 + " Ko), attente du résultat...");

            DataInputStream din = new DataInputStream(new BufferedInputStream(s.getInputStream(), 1 << 16));
            if (din.readInt() != 0) { System.out.println("ERREUR du Master : " + din.readUTF()); return; }
            byte[] res = new byte[din.readInt()];
            din.readFully(res);
            Files.write(Paths.get(outFile), res);
            System.out.println("Résultat reçu en " + (System.currentTimeMillis() - t0) + " ms -> " + outFile);

            // Vérification : on recalcule localement et on compare avec le résultat distribué
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(data));
            BufferedImage got = ImageIO.read(new ByteArrayInputStream(res));
            int w = src.getWidth(), h = src.getHeight();
            boolean ok = got.getWidth() == w && got.getHeight() == h;
            for (int y = 0; ok && y < h; y++)
                for (int x = 0; x < w; x++)
                    if (filter.apply(src.getRGB(x, y), param) != got.getRGB(x, y)) { ok = false; break; }
            System.out.println("Vérification (calcul local vs calcul distribué) : " + (ok ? "OK, résultats identiques" : "DIFFÉRENT !"));
        }
    }

    private static byte[] generate(int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int r = x * 255 / w, g = y * 255 / h, b = ((x + y) * 255 / (w + h)) ^ ((x / 40 + y / 40) % 2 * 60);
                img.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | (b & 0xFF));
            }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }
}
