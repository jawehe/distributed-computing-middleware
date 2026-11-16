package common;

/** Les traitements d'image disponibles (appliqués pixel par pixel). */
public enum FilterType {
    BRIGHTNESS, GRAYSCALE, INVERT, SEPIA;

    public int apply(int argb, int param) {
        int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        switch (this) {
            case BRIGHTNESS:
                r = clamp(r + param); g = clamp(g + param); b = clamp(b + param); break;
            case GRAYSCALE: {
                int y = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                r = y; g = y; b = y; break;
            }
            case INVERT:
                r = 255 - r; g = 255 - g; b = 255 - b; break;
            case SEPIA: {
                int nr = clamp((int) (0.393 * r + 0.769 * g + 0.189 * b));
                int ng = clamp((int) (0.349 * r + 0.686 * g + 0.168 * b));
                int nb = clamp((int) (0.272 * r + 0.534 * g + 0.131 * b));
                r = nr; g = ng; b = nb; break;
            }
        }
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int clamp(int v) { return v < 0 ? 0 : Math.min(v, 255); }
}
