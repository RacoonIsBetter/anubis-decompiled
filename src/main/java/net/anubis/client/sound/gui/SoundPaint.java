package net.anubis.client.sound.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Minimal stand-in for Donut Client's UiPaint and GlassPanel: rounded rectangles drawn as stepped
 * fills, outlines, a soft shadow, and an opacity stack that fades whatever is drawn inside it.
 */
final class SoundPaint {
    private static double opacity = 1;

    private SoundPaint() { }

    static double pushOpacity(double value) { double previous = opacity; opacity = previous * Math.clamp(value, 0, 1); return previous; }
    static void popOpacity(double previous) { opacity = previous; }

    static int withOpacity(int argb) {
        int alpha = (int) Math.round(((argb >>> 24) & 0xFF) * opacity);
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    /** A filled rectangle with corners rounded by insetting the top and bottom rows. */
    static void round(DrawContext g, float x, float y, float w, float h, int radius, int argb) {
        int x0 = Math.round(x), y0 = Math.round(y), x1 = Math.round(x + w), y1 = Math.round(y + h);
        int color = withOpacity(argb);
        int r = Math.max(0, Math.min(radius, Math.min((x1 - x0) / 2, (y1 - y0) / 2)));
        if (r == 0) { g.fill(x0, y0, x1, y1, color); return; }
        g.fill(x0, y0 + r, x1, y1 - r, color);
        for (int row = 0; row < r; row++) {
            double dy = r - row - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            g.fill(x0 + inset, y0 + row, x1 - inset, y0 + row + 1, color);
            g.fill(x0 + inset, y1 - row - 1, x1 - inset, y1 - row, color);
        }
    }

    static void gradient(DrawContext g, float x, float y, float w, float h, int radius, int top, int bottom) {
        int x0 = Math.round(x), y0 = Math.round(y), x1 = Math.round(x + w), y1 = Math.round(y + h);
        int r = Math.max(0, Math.min(radius, Math.min((x1 - x0) / 2, (y1 - y0) / 2)));
        round(g, x0, y0, x1 - x0, r, r, top);
        round(g, x0, y1 - r, x1 - x0, r, r, bottom);
        g.fillGradient(x0, y0 + r / 2, x1, y1 - r / 2, withOpacity(top), withOpacity(bottom));
    }

    static void outline(DrawContext g, float x, float y, float w, float h, int radius, int argb) {
        g.drawStrokedRectangle(Math.round(x), Math.round(y), Math.max(1, Math.round(w)), Math.max(1, Math.round(h)), withOpacity(argb));
    }

    static void shadow(DrawContext g, float x, float y, float w, float h, int radius, int argb) {
        for (int spread = 3; spread >= 1; spread--) {
            int alpha = ((argb >>> 24) & 0xFF) / (spread + 1);
            round(g, x - spread, y - spread + 1, w + spread * 2, h + spread * 2, radius + spread, (alpha << 24) | (argb & 0xFFFFFF));
        }
    }

    static void text(DrawContext g, TextRenderer font, String text, float x, float y, int argb) {
        g.drawText(font, text, Math.round(x), Math.round(y), withOpacity(argb), true);
    }

    static void strong(DrawContext g, TextRenderer font, String text, float x, float y, int argb) {
        g.drawText(font, Text.literal(text).formatted(Formatting.BOLD), Math.round(x), Math.round(y), withOpacity(argb), true);
    }

    static void smallText(DrawContext g, TextRenderer font, String text, float x, float y, int argb) {
        g.getMatrices().pushMatrix();
        try {
            g.getMatrices().translate(x, y);
            g.getMatrices().scale(0.85F, 0.85F);
            text(g, font, text, 0, 0, argb);
        } finally {
            g.getMatrices().popMatrix();
        }
    }

    static int width(TextRenderer font, String text) { return font.getWidth(text); }
}
