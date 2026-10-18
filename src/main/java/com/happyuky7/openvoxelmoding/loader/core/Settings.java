package com.happyuky7.openvoxelmoding.loader.core;

import com.happyuky7.openvoxelmoding.loader.ui.Keybinds;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/** Persistent client settings stored in {@code client/instance/config/user-config.properties}. */
public final class Settings {
    private Settings() {}

    public static boolean sounds = true, vsync = true, debugEnabled = false, cape = true, elytra = false;
    public static float volume = 0.18f, musicVolume = 0.35f, flySpeed = 1.0f;
    public static boolean fog = true;
    public static int renderDistance = 8;
    public static String gameMode = "creative";     // creative | survival
    public static String skinModel = "auto";        // auto | steve | alex
    public static String shaderPreset = "Off";      // Off | Vibrant | Fog
    public static String language = "en_us";
    public static String resourcePack = "";         // folder name under client/instance/resourcepacks

    public static boolean isCreative() { return "creative".equals(gameMode); }

    static Path file() { return Agent.instanceDir().resolve("config").resolve("user-config.properties"); }

    static void load() {
        Path path = file();
        if (!Files.isRegularFile(path)) {
            Path legacy = Agent.instanceDir().resolve("config").resolve("user-config.properties");   // pre-1.0 name
            if (Files.isRegularFile(legacy)) path = legacy; else return;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(path)) { p.load(in); }
        catch (IOException error) { System.err.println("[HappySettings] load: " + error); return; }
        sounds = Boolean.parseBoolean(p.getProperty("sounds", "true"));
        vsync = Boolean.parseBoolean(p.getProperty("vsync", "true"));
        debugEnabled = Boolean.parseBoolean(p.getProperty("debug", "false"));
        cape = Boolean.parseBoolean(p.getProperty("cape", "true"));
        elytra = Boolean.parseBoolean(p.getProperty("elytra", "false"));
        volume = clamp(parse(p.getProperty("volume"), 0.18f), 0f, 1f);
        musicVolume = clamp(parse(p.getProperty("musicVolume"), 0.35f), 0f, 1f);
        fog = Boolean.parseBoolean(p.getProperty("fog", "true"));
        flySpeed = clamp(parse(p.getProperty("flySpeed"), 1f), 0.25f, 4f);
        renderDistance = (int) clamp(parse(p.getProperty("renderDistance"), 8f), 4f, 32f);
        gameMode = p.getProperty("gameMode", "creative").equals("survival") ? "survival" : "creative";
        skinModel = p.getProperty("skinModel", "auto");
        if (!skinModel.equals("steve") && !skinModel.equals("alex")) skinModel = "auto";
        shaderPreset = p.getProperty("shaderPreset", "Off");
        language = p.getProperty("language", "en_us");
        resourcePack = p.getProperty("resourcePack", "");
        Keybinds.load(p);
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("sounds", Boolean.toString(sounds));
        p.setProperty("vsync", Boolean.toString(vsync));
        p.setProperty("debug", Boolean.toString(debugEnabled));
        p.setProperty("cape", Boolean.toString(cape));
        p.setProperty("elytra", Boolean.toString(elytra));
        p.setProperty("volume", Float.toString(volume));
        p.setProperty("musicVolume", Float.toString(musicVolume));
        p.setProperty("fog", Boolean.toString(fog));
        p.setProperty("flySpeed", Float.toString(flySpeed));
        p.setProperty("renderDistance", Integer.toString(renderDistance));
        p.setProperty("gameMode", gameMode);
        p.setProperty("skinModel", skinModel);
        p.setProperty("shaderPreset", shaderPreset);
        p.setProperty("language", language);
        p.setProperty("resourcePack", resourcePack);
        Keybinds.save(p);
        try {
            Files.createDirectories(file().getParent());
            try (OutputStream out = Files.newOutputStream(file())) { p.store(out, "ModLoader client settings"); }
        } catch (IOException error) { System.err.println("[HappySettings] save: " + error); }
    }

    private static float parse(String s, float def) {
        try { return s == null ? def : Float.parseFloat(s.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}
