package worker;

import common.FilterType;

/** Exécute réellement le calcul sur une bande de pixels. */
public final class TaskExecutor {
    /** @param delayMs durée artificielle ajoutée pour simuler un calcul lourd (démo/soutenance). */
    public static int[] run(FilterType f, int param, int[] pixels, int delayMs) throws InterruptedException {
        int[] out = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) out[i] = f.apply(pixels[i], param);
        if (delayMs > 0) Thread.sleep(delayMs);
        return out;
    }
    private TaskExecutor() {}
}
