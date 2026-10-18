package com.happyuky7.openvoxelmoding.loader;

import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** OpenVoxel ModLoader: java agent that adds the modding layer without touching the game JAR. */
public final class Agent {
    public static final String NAME = "OpenVoxel ModLoader", VERSION = "1.1.0-pre2", GAME_VERSION = "0.0.8";
    private static final List<ClientMod> MODS = new ArrayList<>();
    private static final List<URLClassLoader> LOADERS = new ArrayList<>();
    private static final List<String> NAMES = new ArrayList<>();
    private static ModContext context;
    private static Path instance;

    public static List<String> getLoadedModNames() { return Collections.unmodifiableList(NAMES); }
    static List<ClientMod> mods() { return MODS; }
    static ModContext context() { return context; }

    /** Root of the client instance (mods/, config/, logs/, lang/, resourcepacks/). */
    static Path instanceDir() {
        if (instance == null) {
            String configured = System.getenv("OPENVOXEL_MODS_DIR");
            Path game = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
            instance = configured == null ? game.resolve("ModsSystem").resolve("client").resolve("instance") : Paths.get(configured);
        }
        return instance;
    }

    /** True when the agent runs inside the dedicated server (native blocks only, no client hooks). */
    static boolean serverMode;

    public static void premain(String arguments, Instrumentation instrumentation) {
        String command = System.getProperty("sun.java.command", "");
        serverMode = "server".equalsIgnoreCase(arguments) || command.contains("openvoxel-server") || command.contains("net.openvoxel.server");
        if (serverMode) { premainServer(instrumentation); return; }
        // Must be registered before any game class loads: the wrappers rename methods in memory only.
        instrumentation.addTransformer(new Transformer(), false);
        Path game = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path mods = instanceDir().resolve("mods");
        context = new ModContext(game);
        NAMES.add("ModLoader UI (menus, options, F3)");
        NAMES.add("ModLoader Inventory (hotbar, items, armor, creative/survival)");
        NAMES.add("ModLoader Skins (Steve/Alex layers, capes, elytra)");
        NAMES.add("ModLoader Chat (colors, history, tab, commands)");
        NAMES.add("ModLoader Sound");
        System.out.println("[ModLoader] " + NAME + " " + VERSION + " starting from " + mods);
        try {
            Files.createDirectories(mods);
            Files.createDirectories(instanceDir().resolve("config"));
            Files.createDirectories(instanceDir().resolve("resourcepacks"));
            Files.createDirectories(instanceDir().resolve("lang"));
            Settings.load();
            loadMods(mods, context);
            Lang.load(Settings.language);
        } catch (Throwable error) {
            System.err.println("[ModLoader] Could not scan mods: " + error);
        }
        Thread ready = new Thread(() -> waitForEngine(context), "ModLoader-Loader");
        ready.setDaemon(true);
        ready.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Settings.save();
            context.saveConfigs();
            for (ClientMod mod : MODS) try { mod.onShutdown(context); }
            catch (Throwable error) { error.printStackTrace(); }
        }, "ModLoader-Shutdown"));
    }

    /**
     * Dedicated server: registers only the BlockType extension and loads mod jars from {@code mods/}
     * (or OPENVOXEL_MODS_DIR) so they can declare their native blocks in onLoad. Jars without
     * {@code openvoxel.mod.properties} (server plugins) are skipped silently.
     */
    private static void premainServer(Instrumentation instrumentation) {
        Transformer.serverMode = true;
        instrumentation.addTransformer(new Transformer(), false);
        String configured = System.getenv("OPENVOXEL_MODS_DIR");
        instance = configured != null ? Paths.get(configured) : Paths.get("").toAbsolutePath();
        Path mods = configured != null ? instance.resolve("mods") : Paths.get("mods").toAbsolutePath();
        context = new ModContext(Paths.get("").toAbsolutePath());
        System.out.println("[ModLoader] " + NAME + " " + VERSION + " (server agent) - native blocks from " + mods);
        try { if (Files.isDirectory(mods)) loadMods(mods, context); }
        catch (Throwable error) { System.err.println("[ModLoader] Could not scan mods: " + error); }
        System.out.println("[ModLoader] " + NativeBlocks.all().size() + " native block(s) registered");
    }

    private static void loadMods(Path directory, ModContext context) throws Exception {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.jar")) {
            for (Path file : files) try {
                String main, name, version;
                try (JarFile jar = new JarFile(file.toFile())) {
                    Properties properties = new Properties();
                    java.util.jar.JarEntry entry = jar.getJarEntry("openvoxel.mod.properties");
                    if (entry == null && serverMode) continue;                 // a server plugin, not a mod
                    if (entry == null) throw new IllegalArgumentException("missing openvoxel.mod.properties");
                    try (InputStream in = jar.getInputStream(entry)) { properties.load(in); }
                    main = properties.getProperty("main", "").trim();
                    name = properties.getProperty("name", file.getFileName().toString()).trim();
                    version = properties.getProperty("version", "").trim();
                    if (main.isEmpty()) throw new IllegalArgumentException("missing main property");
                }
                Lang.registerModJar(file);
                URLClassLoader loader = new URLClassLoader(new URL[]{file.toUri().toURL()}, ClassLoader.getSystemClassLoader());
                ClientMod mod = (ClientMod) Class.forName(main, true, loader).getDeclaredConstructor().newInstance();
                LOADERS.add(loader); MODS.add(mod); NAMES.add(name + (version.isEmpty() ? "" : " " + version)); mod.onLoad(context);
                context.log("Loaded " + file.getFileName() + " (" + main + ")");
            } catch (Throwable error) {
                System.err.println("[ModLoader] Failed " + file.getFileName() + ": " + error);
                error.printStackTrace();
            }
        }
    }

    private static void waitForEngine(ModContext context) {
        try {
            Class<?> type;
            while (true) {
                try { type = Class.forName("net.openvoxel.engine.VoxelEngine", false, ClassLoader.getSystemClassLoader()); break; }
                catch (ClassNotFoundException ignored) { Thread.sleep(100); }
            }
            Object engine = null;
            while (engine == null) { engine = type.getMethod("getEngine").invoke(null); if (engine == null) Thread.sleep(100); }
            while (type.getMethod("getEventManager").invoke(engine) == null) Thread.sleep(50);
            context.setEngine(engine);
            context.runDeferred();
            BuiltinUI.install(context, engine);
            for (ClientMod mod : MODS) try { mod.onGameReady(context); }
            catch (Throwable error) { error.printStackTrace(); }
            context.log(MODS.size() + " client mod(s) ready; base JAR was not modified.");
        } catch (Throwable error) {
            System.err.println("[ModLoader] Engine hook failed: " + error);
            error.printStackTrace();
        }
    }
}
