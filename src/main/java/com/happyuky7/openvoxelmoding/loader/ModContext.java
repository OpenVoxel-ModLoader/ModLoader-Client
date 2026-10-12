package com.happyuky7.openvoxelmoding.loader;

import java.nio.file.Path;

/** Services offered to client mods by the ModLoader loader. */
public final class ModContext {
    /** Handler for a client chat command; {@code args} excludes the command name. */
    public interface CommandHandler { void run(String[] args); }

    private final Path gameDirectory;
    private volatile Object engine;

    ModContext(Path gameDirectory) { this.gameDirectory = gameDirectory; }
    void setEngine(Object engine) { this.engine = engine; }

    /** Directory the game runs in ({@code Game/}). */
    public Path getGameDirectory() { return gameDirectory; }
    /** {@code client/instance}: mods, config, logs, lang, resourcepacks. */
    public Path getInstanceDirectory() { return Agent.instanceDir(); }
    public Path getConfigDirectory() { return Agent.instanceDir().resolve("config"); }
    /** The {@code net.openvoxel.engine.VoxelEngine} once the game is ready, else null. */
    public Object getEngine() { return engine; }
    public <T> T getEngine(Class<T> type) { return type.cast(engine); }

    public void log(String message) { System.out.println("[ModLoader] " + message); }

    /** Prints a line in the in-game chat; supports {@code &} color codes. */
    public void chat(String message) { net.openvoxel.engine.ui.ChatHud.addLine(message); }

    /** Registers {@code /name}; shown by {@code /chelp} and completed with Tab. */
    public void registerCommand(String name, String description, CommandHandler handler) { ClientCommands.register(name, description, handler); }

    /** Translation from the loader, the mods' {@code assets/<id>/lang/<code>.json} and {@code instance/lang}. */
    public String translate(String key, Object... args) { return Lang.t(key, args); }
    public String language() { return Lang.current(); }

    public boolean isCreative() { return Settings.isCreative(); }
}
