package com.happyuky7.openvoxelmoding.loader;

import net.openvoxel.engine.VoxelEngine;
import net.openvoxel.engine.ui.ChatHud;
import net.openvoxel.engine.world.block.BlockType;
import org.joml.Vector3d;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Services offered to client mods by the OpenVoxel ModLoader. One instance is shared by all
 * mods; everything here is safe to call from the main thread (the game loop). Methods that
 * touch the world return defaults when no world is loaded.
 */
public final class ModContext {
    /** Handler for a client chat command; {@code args} excludes the command name. */
    public interface CommandHandler { void run(String[] args); }

    private final Path gameDirectory;
    private volatile Object engine;
    private final Map<String, ModConfig> configs = new HashMap<>();

    ModContext(Path gameDirectory) { this.gameDirectory = gameDirectory; }
    void setEngine(Object engine) { this.engine = engine; }

    // ---- Paths & engine ----------------------------------------------------------------

    /** Directory the game runs in ({@code Game/}). */
    public Path getGameDirectory() { return gameDirectory; }
    /** {@code instance/}: mods, config, logs, lang, resourcepacks, sounds, music. */
    public Path getInstanceDirectory() { return Agent.instanceDir(); }
    public Path getConfigDirectory() { return Agent.instanceDir().resolve("config"); }
    /** The {@code net.openvoxel.engine.VoxelEngine} once the game is ready, else null. */
    public Object getEngine() { return engine; }
    public <T> T getEngine(Class<T> type) { return type.cast(engine); }
    /** Typed shortcut for {@link #getEngine()}. */
    public VoxelEngine engine() { return (VoxelEngine) engine; }
    /** True while a world (singleplayer or server) is loaded. */
    public boolean isInWorld() { VoxelEngine e = engine(); return e != null && e.isWorldActive() && e.getCamera() != null; }
    /** Player feet position, or null when not in a world. */
    public Vector3d getPlayerPosition() { return isInWorld() ? new Vector3d(engine().getCamera().getPosition()) : null; }
    public float getPlayerYaw() { return isInWorld() ? engine().getCamera().getYaw() : 0f; }
    public float getPlayerPitch() { return isInWorld() ? engine().getCamera().getPitch() : 0f; }

    // ---- Chat, logging, sound ----------------------------------------------------------

    public void log(String message) { System.out.println("[ModLoader] " + message); }
    /** Prints a line in the in-game chat; supports {@code &a} / {@code &#rrggbb} color codes. */
    public void chat(String message) { ChatHud.addLine(message); }
    /** Plays a named sound event (step, break, place, pickup, eat, pop, hurt, click) or a WAV from {@code instance/sounds/<name>.wav}. */
    public void playSound(String name) { BuiltinUI.sound().play(name); }

    // ---- Commands, translations, settings ----------------------------------------------

    /** Registers {@code /name}; shown by {@code /chelp} and completed with Tab. */
    public void registerCommand(String name, String description, CommandHandler handler) { ClientCommands.register(name, description, handler); }
    /** Translation from the loader, the mods' {@code assets/<id>/lang/<code>.json} and {@code instance/lang}. */
    public String translate(String key, Object... args) { return Lang.t(key, args); }
    public String language() { return Lang.current(); }
    /** Persistent per-mod settings file {@code instance/config/<modId>.properties}. */
    public ModConfig config(String modId) { return configs.computeIfAbsent(modId, ModConfig::new); }
    void saveConfigs() { for (ModConfig c : configs.values()) c.save(); }

    // ---- Game mode, items, recipes -----------------------------------------------------

    public boolean isCreative() { return Settings.isCreative(); }
    public boolean isSurvival() { return !Settings.isCreative(); }
    public boolean isFlying() { return GameMode.flying; }

    /**
     * Puts items in the player's inventory (hotbar, then storage). {@code id} is an item id
     * ({@code iron_sword}, {@code apple}, {@code crafting_table}...) or a block name ({@code stone}).
     * @return false when the inventory is full or the id is unknown
     */
    public boolean giveItem(String id, int count) {
        Items.Stack stack = stack(id, count);
        if (stack == null) return false;
        boolean ok = Mining.give(stack);
        if (ok) Mining.pickup(stack);
        return ok;
    }

    /** Item id or block name of the selected hotbar slot, or null when empty. */
    /** Selects a hotbar slot (0-8). */
    public void setHotbarSlot(int slot) { BuiltinUI.select(engine(), Math.max(0, Math.min(8, slot))); }

    public String getHeldItem() {
        Items.Stack s = BuiltinUI.held();
        return s == null ? null : s.isBlock() ? s.block.name().toLowerCase() : s.item.id;
    }

    /**
     * Adds a shaped crafting recipe. {@code pattern} rows use one character per cell (space =
     * empty), e.g. {@code {"MM", "MS", " S"}}; {@code keys} maps characters to item ids or block
     * names: {@code "M", "iron_ingot", "S", "stick"}.
     */
    public boolean registerRecipe(String resultId, int resultCount, String[] pattern, String... keys) {
        Items.Stack result = stack(resultId, resultCount);
        if (result == null || keys.length % 2 != 0) return false;
        char[] chars = new char[keys.length / 2];
        Items.Stack[] ing = new Items.Stack[chars.length];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = keys[i * 2].charAt(0);
            ing[i] = stack(keys[i * 2 + 1], 1);
            if (ing[i] == null) return false;
        }
        Recipes.ALL.add(new Recipes.Recipe(pattern, chars, ing, result));
        return true;
    }

    /** All known item ids (not blocks). */
    public java.util.List<String> itemIds() { java.util.List<String> out = new java.util.ArrayList<>(); for (Items.Item i : Items.ALL) out.add(i.id); return out; }

    private static Items.Stack stack(String id, int count) {
        if (id == null) return null;
        Items.Item item = Items.byId(id.toLowerCase());
        if (item != null) return Items.Stack.of(item, Math.max(1, count));
        try { return Items.Stack.of(BlockType.valueOf(id.toUpperCase()), Math.max(1, count)); } catch (IllegalArgumentException e) { return null; }
    }
}
