package com.happyuky7.openvoxelmoding.loader.gameplay;

import com.happyuky7.openvoxelmoding.loader.net.Net;
import com.happyuky7.openvoxelmoding.loader.world.Tiles;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * "Allow cheats" per singleplayer world, like Minecraft: without it {@code /give} and game mode changes are
 * blocked. Stored in {@code saves/<world>/modloader/world.properties}; new and old worlds start with cheats off
 * and the pause menu toggles it. On servers the server decides (ModLoader servers check permissions).
 */
public final class Cheats {
    private Cheats() {}

    private static File loadedFor;
    private static boolean enabled;

    /** true in a singleplayer world (integrated server). */
    public static boolean singleplayer() { return Tiles.worldDir != null && !Net.managed && !Net.multiplayer(); }

    /** Whether cheat commands may run here: the world flag in singleplayer, otherwise true (the server enforces its rules). */
    public static boolean allowed() {
        if (!singleplayer()) return true;
        load();
        return enabled;
    }

    public static void set(boolean on) {
        if (!singleplayer()) return;
        load();
        enabled = on;
        File f = file();
        Properties p = new Properties();
        try {
            if (f.isFile()) try (FileInputStream in = new FileInputStream(f)) { p.load(in); }
            p.setProperty("cheats", String.valueOf(on));
            f.getParentFile().mkdirs();
            try (FileOutputStream out = new FileOutputStream(f)) { p.store(out, "OpenVoxel ModLoader world settings"); }
        } catch (Exception error) { System.err.println("[ModLoader] world.properties: " + error); }
    }

    private static void load() {
        File dir = Tiles.worldDir;
        if (dir == null || dir.equals(loadedFor)) return;
        loadedFor = dir;
        enabled = false;
        File f = file();
        if (!f.isFile()) return;
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(f)) { p.load(in); enabled = Boolean.parseBoolean(p.getProperty("cheats", "false")); }
        catch (Exception error) { System.err.println("[ModLoader] world.properties: " + error); }
    }

    private static File file() { return new File(new File(Tiles.worldDir, "modloader"), "world.properties"); }
}
