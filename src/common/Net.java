package common;

import java.io.*;
import java.nio.ByteBuffer;

/** Utilitaires TCP : envoi / réception rapide de tableaux d'entiers (pixels). */
public final class Net {
    public static void writeInts(DataOutputStream out, int[] a) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(a.length * 4);
        bb.asIntBuffer().put(a);
        out.write(bb.array());
    }

    public static int[] readInts(DataInputStream in, int n) throws IOException {
        byte[] b = new byte[n * 4];
        in.readFully(b);
        int[] a = new int[n];
        ByteBuffer.wrap(b).asIntBuffer().get(a);
        return a;
    }
    private Net() {}
}
