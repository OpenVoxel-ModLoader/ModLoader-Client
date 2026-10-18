package com.happyuky7.openvoxelmoding.loader.net;

import net.openvoxel.engine.net.Protocol;
import net.openvoxel.engine.ui.GLUIRenderer;
import net.openvoxel.engine.ui.widgets.Label;
import net.openvoxel.engine.ui.widgets.Widget;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server list MOTD. The game's status reply (packet 9) only carries online/max players; a server started
 * with the ModLoader agent appends {@link #MAGIC} and a UTF string after them (the vanilla client ignores
 * extra bytes). ModLoader clients read it while pinging and draw it in the server list row, in place of the
 * address, like Minecraft.
 */
public final class ServerStatus {
    private ServerStatus() {}

    /** "OVML" */
    public static final long MAGIC = 0x4F564D4CL;
    private static final Map<Object, String> MOTDS = Collections.synchronizedMap(new WeakHashMap<>());

    // ---- server side (agent in server mode) ---------------------------------------------

    /** Replaces the hard-coded max players (20) with {@code gameplay.max-players} when the ModLoader plugin set it. */
    public static long maxPlayers(long engineDefault) {
        try { int max = Integer.parseInt(System.getProperty("openvoxel.modloader.maxPlayers", "")); return max > 0 ? max : engineDefault; }
        catch (NumberFormatException e) { return engineDefault; }
    }

    /** Appends the MOTD to the status reply. */
    public static Protocol.PayloadWriter extend(Protocol.PayloadWriter w) {
        String motd = System.getProperty("openvoxel.modloader.motd", "");
        if (motd.isEmpty()) return w;
        if (motd.length() > 512) motd = motd.substring(0, 512);
        return w.u32(MAGIC).utf(motd);
    }

    // ---- client side ----------------------------------------------------------------------

    /** Replaces {@code Protocol.readFrame} in {@code ServerPinger.probe}: reads the frame and remembers the MOTD of {@code result}. */
    public static byte[] pingFrame(DataInputStream in, Object result) throws IOException {
        byte[] frame = Protocol.readFrame(in);
        try {
            if (frame != null && frame.length > 1 + 8 + 4 && (frame[0] & 255) == 9) {
                Protocol.PayloadReader r = new Protocol.PayloadReader(frame, 1 + 8);   // after id, online, max
                if (r.u32() == MAGIC) MOTDS.put(result, r.utf());
            }
        } catch (Exception ignored) { }
        return frame;
    }

    public static String motdOf(Object pingResult) { return pingResult == null ? null : MOTDS.get(pingResult); }

    private static Field labelScale, rowIndex, rowScreen, screenPings;

    /** The ping result shown by a {@code ServerSelectScreen$ServerRowPanel}. */
    public static Object pingOf(Object row) {
        try {
            if (rowIndex == null) {
                Class<?> rc = row.getClass();
                rowIndex = rc.getDeclaredField("index"); rowIndex.setAccessible(true);
                rowScreen = rc.getDeclaredField("this$0"); rowScreen.setAccessible(true);
                screenPings = rowScreen.getType().getDeclaredField("pings"); screenPings.setAccessible(true);
            }
            List<?> pings = (List<?>) screenPings.get(rowScreen.get(row));
            int i = rowIndex.getInt(row);
            return i >= 0 && i < pings.size() ? pings.get(i) : null;
        } catch (Exception e) { return null; }
    }

    /** Before the row renders: hides the address label when there is a MOTD. Returns that label (or null). */
    public static Label hideAddress(List<Widget> children, String motd) {
        Label address = null;
        int labels = 0;
        for (Widget w : children) if (w instanceof Label && ++labels == 2) { address = (Label) w; break; }
        if (address != null) address.setVisible(motd == null || motd.isEmpty());
        return address;
    }

    /** After the row renders: draws up to two MOTD lines with &-colors where the address was. */
    public static void drawMotd(Label address, String motd) {
        if (address == null || motd == null || motd.isEmpty()) return;
        float scale = 1f;
        try {
            if (labelScale == null) { labelScale = Label.class.getDeclaredField("textScale"); labelScale.setAccessible(true); }
            scale = labelScale.getFloat(address);
        } catch (Exception ignored) { }
        String[] lines = motd.split("\n", -1);
        if (lines.length == 1) { colored(lines[0], address.getX(), address.getY(), scale); return; }
        float small = scale * 0.8f;
        colored(lines[0], address.getX(), address.getY() - 7, small);
        colored(lines[1], address.getX(), address.getY() + 7, small);
    }

    /** Draws text with Minecraft color codes ({@code &0-&f}, {@code &r}); formatting codes are skipped. */
    static void colored(String s, float x, float y, float scale) {
        float[] c = {0.67f, 0.67f, 0.67f};
        StringBuilder run = new StringBuilder();
        List<Object[]> runs = new ArrayList<>();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if ((ch == '&' || ch == '§') && i + 1 < s.length()) {
                float[] next = color(Character.toLowerCase(s.charAt(i + 1)));
                if (next != null || "klmnor".indexOf(Character.toLowerCase(s.charAt(i + 1))) >= 0) {
                    if (run.length() > 0) { runs.add(new Object[]{run.toString(), c}); run.setLength(0); }
                    if (next != null) c = next; else if (Character.toLowerCase(s.charAt(i + 1)) == 'r') c = new float[]{0.67f, 0.67f, 0.67f};
                    i++;
                    continue;
                }
            }
            run.append(ch);
        }
        if (run.length() > 0) runs.add(new Object[]{run.toString(), c});
        for (Object[] r : runs) {
            String text = (String) r[0]; float[] col = (float[]) r[1];
            GLUIRenderer.drawText(text, x, y, scale, col[0], col[1], col[2], 1f);
            x += GLUIRenderer.getTextWidth(text) * scale;
        }
    }

    private static float[] color(char code) {
        String hex = "0123456789abcdef";
        int i = hex.indexOf(code);
        if (i < 0) return null;
        int[] rgb = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
        int v = rgb[i];
        return new float[]{(v >> 16 & 255) / 255f, (v >> 8 & 255) / 255f, (v & 255) / 255f};
    }
}
