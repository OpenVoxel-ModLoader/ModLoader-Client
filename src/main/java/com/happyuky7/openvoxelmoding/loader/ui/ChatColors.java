package com.happyuky7.openvoxelmoding.loader.ui;

import com.happyuky7.openvoxelmoding.loader.core.Hooks;

/** Minecraft-style formatting codes: {@code &0-f}, {@code §0-f}, {@code &r} and {@code &#RRGGBB}. */
public final class ChatColors {
    private ChatColors() {}

    /** While true (text field rendering) codes are drawn literally instead of being interpreted. */
    public static volatile boolean raw;

    private static final float[][] PALETTE = {
        {0f, 0f, 0f}, {0f, 0f, 0.667f}, {0f, 0.667f, 0f}, {0f, 0.667f, 0.667f},
        {0.667f, 0f, 0f}, {0.667f, 0f, 0.667f}, {1f, 0.667f, 0f}, {0.667f, 0.667f, 0.667f},
        {0.333f, 0.333f, 0.333f}, {0.333f, 0.333f, 1f}, {0.333f, 1f, 0.333f}, {0.333f, 1f, 1f},
        {1f, 0.333f, 0.333f}, {1f, 0.333f, 1f}, {1f, 1f, 0.333f}, {1f, 1f, 1f}
    };

    static boolean hasCodes(String text) {
        return text != null && (text.indexOf('&') >= 0 || text.indexOf('§') >= 0);
    }

    /** Length of the code starting at {@code i}, or 0 when it is plain text. */
    private static int codeLength(String s, int i) {
        char c = s.charAt(i);
        if (c != '&' && c != '§') return 0;
        if (i + 1 >= s.length()) return 0;
        char n = s.charAt(i + 1);
        if (n == '#' && i + 7 < s.length() + 0 && isHex(s, i + 2, 6)) return 8;
        char l = Character.toLowerCase(n);
        if ((l >= '0' && l <= '9') || (l >= 'a' && l <= 'f') || (l >= 'k' && l <= 'o') || l == 'r') return 2;
        return 0;
    }

    private static boolean isHex(String s, int from, int count) {
        if (from + count > s.length()) return false;
        for (int i = from; i < from + count; i++) if (Character.digit(s.charAt(i), 16) < 0) return false;
        return true;
    }

    public static String strip(String text) {
        if (!hasCodes(text)) return text;
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); ) {
            int len = codeLength(text, i);
            if (len > 0) { i += len; continue; }
            out.append(text.charAt(i++));
        }
        return out.toString();
    }

    public static void drawText(String text, float x, float y, float r, float g, float b, float a) {
        if (text == null || text.isEmpty()) return;
        if (raw || !hasCodes(text)) { Hooks.drawTextOrig(text, x, y, r, g, b, a); return; }
        float cr = r, cg = g, cb = b;
        StringBuilder segment = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int len = codeLength(text, i);
            if (len == 0) { segment.append(text.charAt(i++)); continue; }
            if (segment.length() > 0) {
                String s = segment.toString();
                Hooks.drawTextOrig(s, x, y, cr, cg, cb, a);
                x += Hooks.textWidthOrig(s);
                segment.setLength(0);
            }
            if (len == 8) {
                int rgb = Integer.parseInt(text.substring(i + 2, i + 8), 16);
                cr = ((rgb >> 16) & 255) / 255f; cg = ((rgb >> 8) & 255) / 255f; cb = (rgb & 255) / 255f;
            } else {
                char l = Character.toLowerCase(text.charAt(i + 1));
                int idx = Character.digit(l, 16);
                if (idx >= 0) { cr = PALETTE[idx][0]; cg = PALETTE[idx][1]; cb = PALETTE[idx][2]; }
                else if (l == 'r') { cr = r; cg = g; cb = b; }
            }
            i += len;
        }
        if (segment.length() > 0) Hooks.drawTextOrig(segment.toString(), x, y, cr, cg, cb, a);
    }
}
