package com.happyuky7.openvoxelmoding.loader;

import com.happyuky7.openvoxelmoding.loader.core.Agent;

import net.openvoxel.engine.util.Json;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Translations. Sources, in override order: the loader's bundled {@code lang/<code>.json},
 * every mod jar's {@code assets/<modid>/lang/<code>.json}, and {@code client/instance/lang/<code>.json}.
 * Unknown keys fall back to English and then to the key itself.
 */
public final class Lang {
    private Lang() {}

    private static final Map<String, String> EN = new HashMap<>(), CURRENT = new HashMap<>();
    private static final List<Path> MOD_JARS = new ArrayList<>();
    private static final String[] BUNDLED = {"en_us", "es_es"};
    static String code = "en_us";

    public static void registerModJar(Path jar) { MOD_JARS.add(jar); }

    /** @return the active language code, e.g. {@code en_us} */
    public static String current() { return code; }

    /**
     * Switches the active language (English stays loaded as the fallback).
     *
     * @param languageCode code such as {@code es_es}; null or empty means {@code en_us}
     */
    public static void load(String languageCode) {
        code = languageCode == null || languageCode.isEmpty() ? "en_us" : languageCode;
        EN.clear(); CURRENT.clear();
        loadInto(EN, "en_us");
        if (!code.equals("en_us")) loadInto(CURRENT, code);
    }

    /**
     * Translates a key: current language, then English, then the key itself.
     *
     * @param key translation key
     * @param args {@link String#format} arguments for the placeholders
     * @return translated text
     */
    public static String t(String key, Object... args) {
        String s = CURRENT.get(key);
        if (s == null) s = EN.get(key);
        if (s == null) {
            // "block.oak_log" style keys may pass a fallback as the first argument.
            if (args.length == 1 && args[0] instanceof String && key.startsWith("block.")) return (String) args[0];
            return key;
        }
        if (args.length == 0) return s;
        try { return String.format(s, args); } catch (Exception e) { return s; }
    }

    /** @return language codes that have at least one source (loader, mods, instance/lang), with display names */
    public static LinkedHashMap<String, String> available() {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (String c : BUNDLED) out.put(c, c);
        Path dir = Agent.instanceDir().resolve("lang");
        if (Files.isDirectory(dir)) try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.json")) {
            for (Path f : files) { String n = f.getFileName().toString(); out.put(n.substring(0, n.length() - 5), n); }
        } catch (Exception ignored) { }
        for (Path jar : MOD_JARS) try (JarFile jf = new JarFile(jar.toFile())) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                String n = en.nextElement().getName();
                if (n.startsWith("assets/") && n.contains("/lang/") && n.endsWith(".json")) { String c = n.substring(n.lastIndexOf('/') + 1, n.length() - 5); out.putIfAbsent(c, c); }
            }
        } catch (Exception ignored) { }
        for (Map.Entry<String, String> e : out.entrySet()) {
            Map<String, String> probe = new HashMap<>();
            loadInto(probe, e.getKey());
            e.setValue(probe.getOrDefault("language.name", e.getKey()));
        }
        return out;
    }

    private static void loadInto(Map<String, String> target, String c) {
        try (InputStream in = Lang.class.getClassLoader().getResourceAsStream("lang/" + c + ".json")) {
            if (in != null) parse(target, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
        for (Path jar : MOD_JARS) try (JarFile jf = new JarFile(jar.toFile())) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry entry = en.nextElement();
                String n = entry.getName();
                if (n.startsWith("assets/") && n.endsWith("/lang/" + c + ".json"))
                    try (InputStream in = jf.getInputStream(entry)) { parse(target, new String(in.readAllBytes(), StandardCharsets.UTF_8)); }
            }
        } catch (Exception ignored) { }
        Path file = Agent.instanceDir().resolve("lang").resolve(c + ".json");
        if (Files.isRegularFile(file)) try { parse(target, Files.readString(file, StandardCharsets.UTF_8)); } catch (Exception ignored) { }
    }

    private static void parse(Map<String, String> target, String json) {
        try {
            Map<String, Object> obj = Json.obj(Json.parse(json));
            if (obj == null) return;
            for (Map.Entry<String, Object> e : obj.entrySet()) if (e.getValue() instanceof String) target.put(e.getKey(), (String) e.getValue());
        } catch (Exception error) { System.err.println("[Lang] bad json: " + error.getMessage()); }
    }
}
