package com.happyuky7.openvoxelmoding.loader;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Persistent key/value configuration for one mod, stored at {@code instance/config/<modId>.properties}. */
public final class ModConfig {
    private final Path file;
    private final Properties properties = new Properties();

    ModConfig(String modId) {
        this.file = Agent.instanceDir().resolve("config").resolve(modId.replaceAll("[^a-zA-Z0-9._-]", "_") + ".properties");
        if (Files.isRegularFile(file)) try (InputStream in = Files.newInputStream(file)) { properties.load(in); } catch (Exception ignored) { }
    }

    public Path getFile() { return file; }
    public boolean has(String key) { return properties.containsKey(key); }
    public String get(String key, String def) { return properties.getProperty(key, def); }
    public int getInt(String key, int def) { try { return Integer.parseInt(get(key, Integer.toString(def)).trim()); } catch (NumberFormatException e) { return def; } }
    public double getDouble(String key, double def) { try { return Double.parseDouble(get(key, Double.toString(def)).trim()); } catch (NumberFormatException e) { return def; } }
    public boolean getBoolean(String key, boolean def) { return Boolean.parseBoolean(get(key, Boolean.toString(def)).trim()); }
    public ModConfig set(String key, Object value) { properties.setProperty(key, String.valueOf(value)); return this; }
    public ModConfig remove(String key) { properties.remove(key); return this; }

    /** Writes the file; call after changing values (also called automatically on game shutdown). */
    public void save() {
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) { properties.store(out, "OpenVoxel ModLoader mod config"); }
        } catch (Exception error) { System.err.println("[ModLoader] config " + file + ": " + error); }
    }
}
