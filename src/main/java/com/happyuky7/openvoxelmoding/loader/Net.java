package com.happyuky7.openvoxelmoding.loader;

import net.openvoxel.client.ClientMain;
import net.openvoxel.client.SessionInfo;
import net.openvoxel.engine.VoxelEngine;
import net.openvoxel.engine.ui.ChatHud;
import net.openvoxel.engine.ui.GLUIRenderer;
import net.openvoxel.engine.ui.UIManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Side channel to a ModLoader server: control lines are ordinary chat messages that start with
 * {@link #PREFIX} and are consumed before they reach the chat. The client announces itself with
 * {@code /mlhello}; a ModLoader server answers with {@code hello}, its command list, the player's
 * game mode and the tab list. Vanilla servers just print "Unknown command", which is hidden.
 */
final class Net {
    private Net() {}

    static final String PREFIX = "\u0001ML:";
    /** True once a ModLoader server answered: game mode changes then go through the server. */
    static volatile boolean managed;
    static volatile String serverVersion = "";
    static final List<String> SERVER_COMMANDS = new ArrayList<>();
    private static final List<String> TAB = new ArrayList<>();
    private static String tabHeader = "", tabFooter = "";
    private static boolean helloSent;
    private static long helloNs;

    private static java.lang.reflect.Field integrated;

    /** Connected to a real server (singleplayer runs an integrated server, which does not count). */
    static boolean multiplayer() {
        try {
            if (ClientMain.getNet() == null) return false;
            if (integrated == null) { integrated = ClientMain.class.getDeclaredField("integratedServer"); integrated.setAccessible(true); }
            return integrated.get(null) == null;
        } catch (Throwable t) { return false; }
    }

    static void onWorldEnter(VoxelEngine e) {
        managed = false; serverVersion = ""; helloSent = false;
        SERVER_COMMANDS.clear(); TAB.clear(); tabHeader = ""; tabFooter = "";
        helloNs = System.nanoTime();
    }

    static void onWorldExit() { managed = false; helloSent = false; TAB.clear(); SERVER_COMMANDS.clear(); }

    /** Called every frame: sends the hello once the connection is up. */
    static void tick(VoxelEngine e) {
        if (helloSent || !e.isWorldActive() || !multiplayer()) return;
        if (System.nanoTime() - helloNs < 1_500_000_000L) return;      // let the join finish first
        helloSent = true;
        sendRaw(e, "/mlhello " + Agent.VERSION);
    }

    /** Sends chat straight to the server, bypassing client-side command handling. */
    static void sendRaw(VoxelEngine e, String message) {
        try { Hooks.sendChatOrig(e, message); } catch (Throwable error) { System.err.println("[ModLoader] net send: " + error); }
    }

    /** Hook for every chat line; returns true when the line was a control message (not shown). */
    static boolean handleLine(String text) {
        if (text == null) return false;
        if (text.startsWith("Unknown command: /mlhello")) return true;
        if (!text.startsWith(PREFIX)) return false;
        String body = text.substring(PREFIX.length());
        int sp = body.indexOf(' ');
        String cmd = sp < 0 ? body : body.substring(0, sp), arg = sp < 0 ? "" : body.substring(sp + 1);
        try {
            switch (cmd) {
                case "hello": managed = true; serverVersion = arg.trim(); System.out.println("[ModLoader] server has ModLoader " + serverVersion); break;
                case "cmds": SERVER_COMMANDS.clear(); for (String c : arg.split(",")) if (!c.trim().isEmpty()) SERVER_COMMANDS.add(c.trim()); break;
                case "gm": applyGameMode(arg.trim()); break;
                case "tab": {
                    String[] parts = arg.split("\t", -1);
                    tabHeader = parts.length > 0 ? parts[0] : "";
                    tabFooter = parts.length > 1 ? parts[1] : "";
                    TAB.clear();
                    if (parts.length > 2) for (String n : parts[2].split(",")) if (!n.isEmpty()) TAB.add(n);
                    break;
                }
                default: break;
            }
        } catch (Throwable error) { System.err.println("[ModLoader] control message: " + error); }
        return true;
    }

    private static void applyGameMode(String mode) {
        String m = mode.equals("creative") ? "creative" : "survival";
        if (Settings.gameMode.equals(m)) return;
        GameMode.setLocal(m);
        ChatHud.addLine("&7" + Lang.t("gamemode.set", GameMode.label()));
    }

    /** Game mode request: a ModLoader server decides (op check); otherwise it is applied locally. */
    static void requestGameMode(VoxelEngine e, String mode) {
        if (managed && e != null) sendRaw(e, "/gamemode " + mode);
        else { GameMode.setLocal(mode); ChatHud.addLine("&7" + Lang.t("gamemode.set", GameMode.label())); }
    }

    // ---- Tab list ----------------------------------------------------------------------

    static List<String> players() {
        if (managed && !TAB.isEmpty()) return TAB;
        if (multiplayer()) return Chat.playerNames();
        List<String> me = new ArrayList<>();
        SessionInfo s = ClientMain.getSession();
        me.add(s != null && s.username != null ? s.username : "Player");
        return me;
    }

    /** Minecraft-style player list while the key is held. 2D rendering must be set up. */
    static void renderPlayerList(int w, int h) {
        List<String> names = players();
        if (names.isEmpty()) return;
        String header = managed ? tabHeader : (multiplayer() ? Lang.t("tablist.server") : Lang.t("tablist.singleplayer"));
        String footer = managed ? tabFooter : Lang.t("tablist.count", names.size());
        int cols = names.size() <= 10 ? 1 : names.size() <= 20 ? 2 : names.size() <= 40 ? 3 : 4;
        int rows = (names.size() + cols - 1) / cols;
        float colW = 0;
        for (String n : names) colW = Math.max(colW, GLUIRenderer.getTextWidth(n));
        colW += 28;
        float rowH = 22, pad = 10;
        float width = Math.max(cols * colW + (cols - 1) * 4 + pad * 2, Math.max(GLUIRenderer.getTextWidth(header), GLUIRenderer.getTextWidth(footer)) + pad * 2);
        width = Math.max(width, 220);
        float height = pad + (header.isEmpty() ? 0 : 26) + rows * rowH + (footer.isEmpty() ? 0 : 26) + pad;
        float x = (w - width) / 2f, y = 10;
        GLUIRenderer.drawPanel(x, y, width, height, 0, 0, 0, 0.55f);
        float cy = y + pad;
        if (!header.isEmpty()) { Mc.centered(header, w / 2f, cy + 16, Mc.TEXT); cy += 26; }
        float gx = x + (width - (cols * colW + (cols - 1) * 4)) / 2f;
        for (int i = 0; i < names.size(); i++) {
            int c = i / rows, r = i % rows;
            float nx = gx + c * (colW + 4), ny = cy + r * rowH;
            GLUIRenderer.drawPanel(nx, ny, colW, rowH - 2, 1, 1, 1, 0.12f);
            GLUIRenderer.drawPanel(nx + 4, ny + 4, 12, 12, 0.55f, 0.75f, 0.95f, 1f);   // head placeholder
            Mc.text(names.get(i), nx + 22, ny + 15, Mc.TEXT);
        }
        cy += rows * rowH;
        if (!footer.isEmpty()) Mc.centered(footer, w / 2f, cy + 16, Mc.TEXT_DIM);
    }

    static boolean listKeyDown() {
        return Keybinds.down(UIManager.get().getWindow(), "playerlist");
    }

    static List<String> serverCommandNames() {
        return SERVER_COMMANDS.isEmpty() ? Arrays.asList(Chat.SERVER_COMMANDS) : SERVER_COMMANDS;
    }
}
