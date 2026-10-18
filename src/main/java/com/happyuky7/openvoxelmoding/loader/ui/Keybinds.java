package com.happyuky7.openvoxelmoding.loader.ui;

import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rebindable controls with Minecraft defaults. The engine reads keys through
 * {@code GLFW.glfwGetKey}; the transformer redirects those calls in InputHandler to
 * {@link Hooks#glfwGetKey}, which maps the engine's hard-coded key to the bound one.
 */
public final class Keybinds {
    private Keybinds() {}

    static final String[] ACTIONS = {"forward", "back", "left", "right", "jump", "sneak", "sprint", "inventory", "chat", "drop", "debug", "thirdperson", "playerlist"};
    private static final Map<String, Integer> DEFAULTS = new LinkedHashMap<>();
    private static final Map<String, Integer> BOUND = new LinkedHashMap<>();
    /** Engine key code → action, so remapping works inside the game's own input code. */
    private static final Map<Integer, String> ENGINE = new LinkedHashMap<>();

    static {
        DEFAULTS.put("forward", GLFW.GLFW_KEY_W); DEFAULTS.put("back", GLFW.GLFW_KEY_S);
        DEFAULTS.put("left", GLFW.GLFW_KEY_A); DEFAULTS.put("right", GLFW.GLFW_KEY_D);
        DEFAULTS.put("jump", GLFW.GLFW_KEY_SPACE); DEFAULTS.put("sneak", GLFW.GLFW_KEY_LEFT_SHIFT);
        DEFAULTS.put("sprint", GLFW.GLFW_KEY_LEFT_CONTROL); DEFAULTS.put("inventory", GLFW.GLFW_KEY_E);
        DEFAULTS.put("chat", GLFW.GLFW_KEY_T); DEFAULTS.put("drop", GLFW.GLFW_KEY_Q);
        DEFAULTS.put("debug", GLFW.GLFW_KEY_F3); DEFAULTS.put("thirdperson", GLFW.GLFW_KEY_F5); DEFAULTS.put("playerlist", GLFW.GLFW_KEY_TAB);
        BOUND.putAll(DEFAULTS);
        // What the engine's InputHandler polls for each action.
        ENGINE.put(GLFW.GLFW_KEY_W, "forward"); ENGINE.put(GLFW.GLFW_KEY_S, "back");
        ENGINE.put(GLFW.GLFW_KEY_A, "left"); ENGINE.put(GLFW.GLFW_KEY_D, "right");
        ENGINE.put(GLFW.GLFW_KEY_SPACE, "jump"); ENGINE.put(GLFW.GLFW_KEY_LEFT_CONTROL, "sneak");
        ENGINE.put(GLFW.GLFW_KEY_LEFT_SHIFT, "sprint"); ENGINE.put(GLFW.GLFW_KEY_E, "inventory");
        ENGINE.put(GLFW.GLFW_KEY_T, "chat"); ENGINE.put(GLFW.GLFW_KEY_F3, "debug"); ENGINE.put(GLFW.GLFW_KEY_F5, "thirdperson");
    }

    public static int key(String action) { return BOUND.getOrDefault(action, DEFAULTS.getOrDefault(action, GLFW.GLFW_KEY_UNKNOWN)); }
    static int defaultKey(String action) { return DEFAULTS.getOrDefault(action, GLFW.GLFW_KEY_UNKNOWN); }
    static void bind(String action, int key) { BOUND.put(action, key); }
    static void reset() { BOUND.clear(); BOUND.putAll(DEFAULTS); }

    /** Translates a key the engine polls into the key the player bound for that action. */
    public static int remap(int engineKey) {
        String action = ENGINE.get(engineKey);
        return action == null ? engineKey : key(action);
    }

    public static boolean down(long window, String action) { return GLFW.glfwGetKey(window, key(action)) == GLFW.GLFW_PRESS; }

    static String name(int key) {
        if (key == GLFW.GLFW_KEY_UNKNOWN) return "-";
        switch (key) {
            case GLFW.GLFW_KEY_SPACE: return "Space"; case GLFW.GLFW_KEY_LEFT_SHIFT: return "L.Shift"; case GLFW.GLFW_KEY_RIGHT_SHIFT: return "R.Shift";
            case GLFW.GLFW_KEY_LEFT_CONTROL: return "L.Ctrl"; case GLFW.GLFW_KEY_RIGHT_CONTROL: return "R.Ctrl"; case GLFW.GLFW_KEY_LEFT_ALT: return "L.Alt";
            case GLFW.GLFW_KEY_RIGHT_ALT: return "R.Alt"; case GLFW.GLFW_KEY_TAB: return "Tab"; case GLFW.GLFW_KEY_ENTER: return "Enter";
            case GLFW.GLFW_KEY_ESCAPE: return "Esc"; case GLFW.GLFW_KEY_UP: return "Up"; case GLFW.GLFW_KEY_DOWN: return "Down";
            case GLFW.GLFW_KEY_LEFT: return "Left"; case GLFW.GLFW_KEY_RIGHT: return "Right"; case GLFW.GLFW_KEY_CAPS_LOCK: return "Caps";
            case GLFW.GLFW_KEY_BACKSPACE: return "Backspace"; case GLFW.GLFW_KEY_DELETE: return "Del"; case GLFW.GLFW_KEY_INSERT: return "Ins";
            case GLFW.GLFW_KEY_HOME: return "Home"; case GLFW.GLFW_KEY_END: return "End"; case GLFW.GLFW_KEY_PAGE_UP: return "PgUp"; case GLFW.GLFW_KEY_PAGE_DOWN: return "PgDn";
            default:
                if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
                if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) return "Num" + (key - GLFW.GLFW_KEY_KP_0);
                try { String n = GLFW.glfwGetKeyName(key, 0); if (n != null && !n.isEmpty()) return n.toUpperCase(); } catch (Throwable ignored) { }
                return "Key " + key;
        }
    }

    public static void load(java.util.Properties p) {
        reset();
        for (String a : ACTIONS) {
            String v = p.getProperty("keys." + a);
            if (v != null) try { BOUND.put(a, Integer.parseInt(v.trim())); } catch (NumberFormatException ignored) { }
        }
    }

    public static void save(java.util.Properties p) { for (String a : ACTIONS) p.setProperty("keys." + a, Integer.toString(key(a))); }
}
