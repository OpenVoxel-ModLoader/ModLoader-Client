package com.happyuky7.openvoxelmoding.loader;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Persistent key/value settings for one mod, stored as a properties file at
 * {@code instance/config/<modId>.properties}. Obtain it with {@link ModContext#config(String)}.
 *
 * <pre>{@code
 * ModConfig cfg = ctx.config("mymod");
 * boolean hud = cfg.getBoolean("hud.enabled", true);
 * cfg.set("hud.enabled", !hud).save();
 * }</pre>
 *
 * Values are strings on disk; the typed getters fall back to the default when a value is missing or
 * cannot be parsed. Every config is also saved automatically when the game closes.
 */
public final class ModConfig {
    private final Path file;
    private final Properties properties = new Properties();

    ModConfig(String modId) {
        this.file = Agent.instanceDir().resolve("config").resolve(modId.replaceAll("[^a-zA-Z0-9._-]", "_") + ".properties");
        if (Files.isRegularFile(file)) try (InputStream in = Files.newInputStream(file)) { properties.load(in); } catch (Exception ignored) { }
    }

    /** @return the file backing this config */
    public Path getFile() { return file; }

    /**
     * @param key setting name
     * @return true if the key has a value
     */
    public boolean has(String key) { return properties.containsKey(key); }

    /**
     * @param key setting name
     * @param def value returned when the key is missing
     * @return the stored string, or {@code def}
     */
    public String get(String key, String def) { return properties.getProperty(key, def); }

    /**
     * @param key setting name
     * @param def value returned when missing or not a number
     * @return the stored integer, or {@code def}
     */
    public int getInt(String key, int def) { try { return Integer.parseInt(get(key, Integer.toString(def)).trim()); } catch (NumberFormatException e) { return def; } }

    /**
     * @param key setting name
     * @param def value returned when missing or not a number
     * @return the stored number, or {@code def}
     */
    public double getDouble(String key, double def) { try { return Double.parseDouble(get(key, Double.toString(def)).trim()); } catch (NumberFormatException e) { return def; } }

    /**
     * @param key setting name
     * @param def value returned when missing
     * @return true only for the text {@code true} (any case)
     */
    public boolean getBoolean(String key, boolean def) { return Boolean.parseBoolean(get(key, Boolean.toString(def)).trim()); }

    /**
     * Sets a value (stored with {@link String#valueOf(Object)}). Call {@link #save()} to write it.
     *
     * @param key setting name
     * @param value new value
     * @return this config, for chaining
     */
    public ModConfig set(String key, Object value) { properties.setProperty(key, String.valueOf(value)); return this; }

    /**
     * Removes a value.
     *
     * @param key setting name
     * @return this config, for chaining
     */
    public ModConfig remove(String key) { properties.remove(key); return this; }

    /** Writes the file now (it is also written automatically when the game closes). */
    public void save() {
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) { properties.store(out, "OpenVoxel ModLoader mod config"); }
        } catch (Exception error) { System.err.println("[ModLoader] config " + file + ": " + error); }
    }
}
