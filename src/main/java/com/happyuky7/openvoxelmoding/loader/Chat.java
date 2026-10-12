package com.happyuky7.openvoxelmoding.loader;

import net.openvoxel.client.ClientMain;
import net.openvoxel.client.SessionInfo;
import net.openvoxel.engine.VoxelEngine;
import net.openvoxel.engine.ui.ChatHud;
import net.openvoxel.engine.ui.GLUIRenderer;
import net.openvoxel.engine.ui.widgets.ChatScreen;
import net.openvoxel.engine.ui.widgets.TextField;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.*;

/** Chat quality of life: sent-message history (up/down), tab completion, scrolling (wheel / PageUp-PageDown). */
final class Chat {
    private Chat() {}

    private static final List<String> SENT = new ArrayList<>();
    private static int historyIndex = -1;
    private static String draft;
    static int scroll;
    private static List<String> completions = new ArrayList<>();
    private static int completionIndex = -1;
    private static String completionBase;
    private static Field chatField, historyField, playersField;

    static final String[] SERVER_COMMANDS = {"help", "version", "players", "heal", "platform", "modtest", "modreload"};

    static void recordSent(String message) {
        if (message == null || message.isEmpty()) return;
        SENT.remove(message);
        SENT.add(message);
        while (SENT.size() > 100) SENT.remove(0);
        historyIndex = -1;
        draft = null;
        scroll = 0;
    }

    private static TextField field(Object screen) throws Exception {
        if (chatField == null) { chatField = ChatScreen.class.getDeclaredField("field"); chatField.setAccessible(true); }
        return (TextField) chatField.get(screen);
    }

    /** Returns true when the key was consumed. */
    static boolean onKey(Object screen, int key) throws Exception {
        TextField f = field(screen);
        if (f == null) return false;
        switch (key) {
            case GLFW.GLFW_KEY_UP:
                if (SENT.isEmpty()) return true;
                if (historyIndex == -1) { draft = f.getText(); historyIndex = SENT.size() - 1; }
                else if (historyIndex > 0) historyIndex--;
                f.setText(SENT.get(historyIndex));
                return true;
            case GLFW.GLFW_KEY_DOWN:
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
                if (key != GLFW.GLFW_KEY_LEFT_SHIFT && key != GLFW.GLFW_KEY_RIGHT_SHIFT) completionIndex = -1;
                return false;
        }
    }

    private static void complete(TextField f) {
        String text = f.getText();
        if (completionIndex >= 0 && !completions.isEmpty() && text.equals(completionBase + completions.get(completionIndex))) {
            completionIndex = (completionIndex + 1) % completions.size();
            f.setText(completionBase + completions.get(completionIndex));
            return;
        }
        int space = text.lastIndexOf(' ');
        String prefix = text.substring(space + 1), base = text.substring(0, space + 1);
        List<String> candidates = new ArrayList<>();
        if (space < 0 && text.startsWith("/")) {
            for (String c : ClientCommands.names()) candidates.add("/" + c + " ");
            for (String c : SERVER_COMMANDS) if (!ClientCommands.names().contains(c)) candidates.add("/" + c + " ");
        } else {
            candidates.addAll(playerNames());
        }
        completions = new ArrayList<>();
        for (String c : candidates) if (c.toLowerCase().startsWith(prefix.toLowerCase()) && !c.trim().isEmpty()) completions.add(c);
        Collections.sort(completions);
        if (completions.isEmpty()) { completionIndex = -1; return; }
        completionBase = base;
        completionIndex = 0;
        f.setText(base + completions.get(0));
    }

    @SuppressWarnings("unchecked")
    static List<String> playerNames() {
        List<String> names = new ArrayList<>();
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
    static void render(VoxelEngine engine) throws Exception {
        int[] aspect = engine.getCamera() != null ? engine.getCamera().getAspect() : new int[]{1280, 720};
        if (historyField == null) { historyField = ChatHud.class.getDeclaredField("history"); historyField.setAccessible(true); }
        Deque<ChatHud.Entry> history = (Deque<ChatHud.Entry>) historyField.get(null);
        List<String> lines = new ArrayList<>();
        float maxTextW = Math.min(aspect[0] * 0.45f, 520f);
        synchronized (history) { for (ChatHud.Entry e : history) lines.addAll(wrap(e.text, maxTextW)); }
        if (lines.isEmpty()) return;
        float lineH = GLUIRenderer.getFontHeight() + 3f;
        int maxLines = Math.max(3, (int) ((aspect[1] - 120) / lineH));
        scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - maxLines)));
        int end = lines.size() - scroll, start = Math.max(0, end - maxLines);
        List<String> shown = lines.subList(start, end);
        GLUIRenderer.setup2DRendering(aspect[0], aspect[1]);
        try {
            float x = 6f, y = aspect[1] - 72f - lineH * shown.size();
            GLUIRenderer.drawPanel(x - 4f, y - GLUIRenderer.getFontHeight() - 6f, maxTextW + 12f, lineH * shown.size() + 14f, 0.02f, 0.02f, 0.03f, 0.55f);
            for (String s : shown) { GLUIRenderer.drawText(s, x, y, 1f, 1f, 1f, 1f); y += lineH; }
            if (scroll > 0) GLUIRenderer.drawText("v " + scroll + " more", x + maxTextW - 80, aspect[1] - 72f, 0.7f, 0.7f, 0.7f, 1f);
        } finally { GLUIRenderer.restore3DRendering(); }
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
