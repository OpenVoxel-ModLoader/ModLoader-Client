package com.happyuky7.openvoxelmoding.loader.ui;

import com.happyuky7.openvoxelmoding.loader.Lang;
import com.happyuky7.openvoxelmoding.loader.NativeBlocks;

import com.happyuky7.openvoxelmoding.loader.command.ClientCommands;
import com.happyuky7.openvoxelmoding.loader.command.Give;
import com.happyuky7.openvoxelmoding.loader.item.Items;
import com.happyuky7.openvoxelmoding.loader.net.Net;

import net.openvoxel.client.ClientMain;
import net.openvoxel.client.SessionInfo;
import net.openvoxel.engine.VoxelEngine;
import net.openvoxel.engine.ui.ChatHud;
import net.openvoxel.engine.ui.GLUIRenderer;
import net.openvoxel.engine.ui.widgets.ChatScreen;
import net.openvoxel.engine.ui.widgets.TextField;
import net.openvoxel.engine.world.block.BlockType;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;

/**
 * Chat quality of life: sent-message history (up/down), Minecraft-style tab completion with a
 * suggestion popup (commands, their arguments, player names), scrolling (wheel / PageUp-PageDown).
 */
public final class Chat {
    private Chat() {}

    private static final List<String> SENT = new ArrayList<>();
    private static int historyIndex = -1;
    private static String draft;
    static int scroll;
    private static List<String> completions = new ArrayList<>();
    private static int completionIndex;
    private static String completionBase = "", completionText = "";
    private static Field chatField, historyField, playersField;
    private static TextField currentField;

    public static final String[] SERVER_COMMANDS = {"help", "version", "players", "list", "heal", "say", "platform", "modtest", "modreload"};

    /** Argument suggestions per command (client and ModLoader server commands). */
    private static final Map<String, Function<String[], List<String>>> ARGS = new HashMap<>();
    // Each function gets the arguments typed so far; the last element is the word being typed
    // (empty right after a space), so a.length is the 1-based position of the current argument.
    static {
        Function<String[], List<String>> modes = a -> a.length == 1 ? Arrays.asList("creative", "survival") : a.length == 2 ? playerNames() : Collections.emptyList();
        ARGS.put("gamemode", modes); ARGS.put("gm", modes);
        ARGS.put("give", a -> {
            boolean target = a.length >= 2 && Give.resolve(a[0]) == null;           // "/give <player> <item> [count]"
            int item = target ? 2 : 1;
            if (a.length == item) { List<String> ids = itemIds(); if (a.length == 1) ids.addAll(playerNames()); return ids; }
            if (a.length == item + 1) return Arrays.asList("1", "16", "32", "64");
            return Collections.emptyList();
        });
        ARGS.put("lang", first(a -> new ArrayList<>(Lang.available().keySet())));
        ARGS.put("music", first(a -> Arrays.asList("play", "next", "stop", "list", "reload")));
        ARGS.put("skin", first(a -> Arrays.asList("reload", "upload", "model")));
        ARGS.put("mobs", a -> a.length == 1 ? Arrays.asList("on", "off", "clear", "count", "spawn") : a.length == 2 && a[0].equalsIgnoreCase("spawn") ? Arrays.asList("pig", "zombie") : Collections.emptyList());
        Function<String[], List<String>> players = first(a -> playerNames());
        for (String c : new String[]{"op", "deop", "kick", "msg", "tell", "heal", "whois"}) ARGS.put(c, players);
        ARGS.put("tp", a -> a.length <= 2 ? playerNames() : Collections.emptyList());
        ARGS.put("whitelist", a -> a.length == 1 ? Arrays.asList("add", "remove", "list") : a.length == 2 && !a[0].equalsIgnoreCase("list") ? playerNames() : Collections.emptyList());
        ARGS.put("perms", a -> a.length == 1 ? Arrays.asList("check", "reload") : a.length == 2 && a[0].equalsIgnoreCase("check") ? playerNames() : Collections.emptyList());
        ARGS.put("warp", a -> Collections.emptyList());
        ARGS.put("world", first(a -> Arrays.asList("info", "spawn", "setspawn", "warps", "setwarp", "delwarp", "time", "save")));
    }

    /** Suggestions only for the first argument. */
    private static Function<String[], List<String>> first(Function<String[], List<String>> fn) { return a -> a.length == 1 ? fn.apply(a) : Collections.emptyList(); }

    private static List<String> itemIds() {
        List<String> ids = new ArrayList<>();
        for (Items.Item i : Items.ALL) ids.add(i.id);
        for (BlockType b : BlockType.values()) if (b != BlockType.AIR && !b.hiddenFromInventory() && NativeBlocks.of(b) == null) ids.add(b.name().toLowerCase());
        for (NativeBlocks.Def d : NativeBlocks.all()) ids.add(d.id());
        return ids;
    }

    public static void recordSent(String message) {
        if (message == null || message.isEmpty()) return;
        SENT.remove(message);
        SENT.add(message);
        while (SENT.size() > 100) SENT.remove(0);
        historyIndex = -1;
        draft = null;
        scroll = 0;
        completions = new ArrayList<>();
    }

    private static TextField field(Object screen) throws Exception {
        if (chatField == null) { chatField = ChatScreen.class.getDeclaredField("field"); chatField.setAccessible(true); }
        return (TextField) chatField.get(screen);
    }

    /** Returns true when the key was consumed. */
    public static boolean onKey(Object screen, int key) throws Exception {
        TextField f = field(screen);
        if (f == null) return false;
        currentField = f;
        switch (key) {
            case GLFW.GLFW_KEY_UP:
                if (!completions.isEmpty() && completions.size() > 1 && f.getText().equals(completionText)) { select(f, completionIndex - 1); return true; }
                if (SENT.isEmpty()) return true;
                if (historyIndex == -1) { draft = f.getText(); historyIndex = SENT.size() - 1; }
                else if (historyIndex > 0) historyIndex--;
                f.setText(SENT.get(historyIndex));
                return true;
            case GLFW.GLFW_KEY_DOWN:
                if (!completions.isEmpty() && completions.size() > 1 && f.getText().equals(completionText)) { select(f, completionIndex + 1); return true; }
                if (historyIndex == -1) return true;
                historyIndex++;
                if (historyIndex >= SENT.size()) { historyIndex = -1; f.setText(draft == null ? "" : draft); }
                else f.setText(SENT.get(historyIndex));
                return true;
            case GLFW.GLFW_KEY_TAB:
                complete(f);
                return true;
            case GLFW.GLFW_KEY_PAGE_UP:
                scroll += 5;
                return true;
            case GLFW.GLFW_KEY_PAGE_DOWN:
                scroll = Math.max(0, scroll - 5);
                return true;
            default:
                return false;
        }
    }

    private static void select(TextField f, int index) {
        if (completions.isEmpty()) return;
        completionIndex = ((index % completions.size()) + completions.size()) % completions.size();
        completionText = completionBase + completions.get(completionIndex);
        f.setText(completionText);
    }

    /** Candidates for the word being typed; also refreshes the popup state. */
    private static void refresh(String text) {
        if (text.equals(completionText) && !completions.isEmpty()) return;          // user accepted a suggestion
        int space = text.lastIndexOf(' ');
        String prefix = text.substring(space + 1), base = text.substring(0, space + 1);
        List<String> candidates = new ArrayList<>();
        if (space < 0 && text.startsWith("/")) {
            Set<String> names = new LinkedHashSet<>(ClientCommands.names());
            names.addAll(Net.serverCommandNames());
            for (String c : names) candidates.add("/" + c + " ");
        } else if (text.startsWith("/")) {
            String[] parts = text.substring(1).replaceAll("^\\s+", "").split("\\s+", -1);   // keeps the empty word after a trailing space
            Function<String[], List<String>> fn = ARGS.get(parts[0].toLowerCase());
            if (fn != null) { String[] args = Arrays.copyOfRange(parts, 1, parts.length); try { for (String c : fn.apply(args)) candidates.add(c + " "); } catch (Exception ignored) { } }
            else for (String p : playerNames()) candidates.add(p + " ");
        } else {
            for (String p : playerNames()) candidates.add(p + " ");
        }
        List<String> out = new ArrayList<>();
        if (!prefix.isEmpty() || text.startsWith("/")) {
            for (String c : candidates) if (c.toLowerCase().startsWith(prefix.toLowerCase()) && !c.trim().isEmpty() && !c.trim().equalsIgnoreCase(prefix.trim())) out.add(c);
        }
        Collections.sort(out);
        completions = out;
        completionBase = base;
        completionIndex = 0;
        completionText = "";
    }

    private static void complete(TextField f) {
        String text = f.getText();
        if (!completions.isEmpty() && text.equals(completionText)) { select(f, completionIndex + 1); return; }
        refresh(text);
        if (completions.isEmpty()) return;
        select(f, 0);
    }

    @SuppressWarnings("unchecked")
    public static List<String> playerNames() {
        List<String> names = new ArrayList<>();
        if (Net.managed) { names.addAll(Net.players()); return names; }
        SessionInfo s = ClientMain.getSession();
        if (s != null && s.username != null) names.add(s.username);
        try {
            if (ClientMain.getNet() != null) {
                Object remote = ClientMain.getNet().getRemotePlayers();
                if (playersField == null) { playersField = remote.getClass().getDeclaredField("players"); playersField.setAccessible(true); }
                Map<String, Object> players = (Map<String, Object>) playersField.get(remote);
                for (Object p : players.values()) {
                    Field u = p.getClass().getDeclaredField("username");
                    u.setAccessible(true);
                    Object name = u.get(p);
                    if (name != null) names.add(name.toString());
                }
            }
        } catch (Exception ignored) { }
        return names;
    }

    // ---- Scrollable rendering while the input is open ----------------------------------

    @SuppressWarnings("unchecked")
    public static void render(VoxelEngine engine) throws Exception {
        int[] aspect = engine.getCamera() != null ? engine.getCamera().getAspect() : new int[]{1280, 720};
        if (historyField == null) { historyField = ChatHud.class.getDeclaredField("history"); historyField.setAccessible(true); }
        Deque<ChatHud.Entry> history = (Deque<ChatHud.Entry>) historyField.get(null);
        List<String> lines = new ArrayList<>();
        float maxTextW = Math.min(aspect[0] * 0.45f, 520f);
        synchronized (history) { for (ChatHud.Entry e : history) lines.addAll(wrap(e.text, maxTextW)); }
        float lineH = GLUIRenderer.getFontHeight() + 3f;
        GLUIRenderer.setup2DRendering(aspect[0], aspect[1]);
        try {
            if (!lines.isEmpty()) {
                int maxLines = Math.max(3, (int) ((aspect[1] - 120) / lineH));
                scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - maxLines)));
                int end = lines.size() - scroll, start = Math.max(0, end - maxLines);
                List<String> shown = lines.subList(start, end);
                float x = 6f, y = aspect[1] - 72f - lineH * shown.size();
                GLUIRenderer.drawPanel(x - 4f, y - GLUIRenderer.getFontHeight() - 6f, maxTextW + 12f, lineH * shown.size() + 14f, 0.02f, 0.02f, 0.03f, 0.55f);
                for (String s : shown) { GLUIRenderer.drawText(s, x, y, 1f, 1f, 1f, 1f); y += lineH; }
                if (scroll > 0) GLUIRenderer.drawText("v " + scroll + " more", x + maxTextW - 80, aspect[1] - 72f, 0.7f, 0.7f, 0.7f, 1f);
            }
            renderSuggestions(aspect[0], aspect[1]);
        } finally { GLUIRenderer.restore3DRendering(); }
    }

    /** Minecraft-like suggestion popup right above the input line. */
    private static void renderSuggestions(int w, int h) {
        TextField f = null;
        try { Object top = BuiltinUI.topGui(); if (top instanceof ChatScreen) f = field(top); } catch (Exception ignored) { }
        if (f == null) f = currentField;
        if (f == null) return;
        String text;
        try { text = f.getText(); } catch (Exception e) { return; }
        if (text == null || text.isEmpty()) { completions = new ArrayList<>(); return; }
        refresh(text);
        if (completions.isEmpty()) return;
        int max = 8, from = Math.max(0, Math.min(completionIndex - max / 2, completions.size() - max));
        int to = Math.min(completions.size(), from + max);
        float rowH = 20f, pw = 0;
        for (int i = from; i < to; i++) pw = Math.max(pw, GLUIRenderer.getTextWidth(completions.get(i).trim()));
        pw += 16;
        float x = 10f + GLUIRenderer.getTextWidth(completionBase), bottom = h - 54f;
        float top = bottom - (to - from) * rowH - 6;
        GLUIRenderer.drawPanel(x - 4, top, pw, bottom - top, 0.05f, 0.05f, 0.07f, 0.9f);
        GLUIRenderer.drawBorder(x - 4, top, pw, bottom - top, 1, 0.3f, 0.3f, 0.35f, 1);
        boolean accepted = text.equals(completionText);
        for (int i = from; i < to; i++) {
            float y = top + 4 + (i - from) * rowH + 15;
            boolean sel = accepted && i == completionIndex;
            GLUIRenderer.drawText(completions.get(i).trim(), x + 2, y, sel ? 1f : 0.75f, sel ? 1f : 0.75f, sel ? 0.4f : 0.75f, 1f);
        }
        if (completions.size() > max) GLUIRenderer.drawText((completionIndex + 1) + "/" + completions.size(), x + pw - 46, top - 4, 0.6f, 0.6f, 0.6f, 1f);
    }

    private static List<String> wrap(String text, float maxWidth) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) { out.add(""); return out; }
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ", -1)) {
            while (GLUIRenderer.getTextWidth(word) > maxWidth && word.length() > 1) {
                int n = 1;
                while (n < word.length() && GLUIRenderer.getTextWidth(word.substring(0, n + 1)) <= maxWidth) n++;
                if (line.length() > 0) { out.add(line.toString()); line.setLength(0); }
                out.add(word.substring(0, n));
                word = word.substring(n);
            }
            if (line.length() == 0) line.append(word);
            else if (GLUIRenderer.getTextWidth(line + " " + word) <= maxWidth) line.append(' ').append(word);
            else { out.add(line.toString()); line.setLength(0); line.append(word); }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }
}
