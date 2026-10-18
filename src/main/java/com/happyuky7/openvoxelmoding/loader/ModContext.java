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

    /**
     * True when this mod was loaded by the ModLoader agent inside a dedicated server. There only
     * {@link #nativeBlock} and configuration matter; client features (chat, sounds, commands,
     * recipes, items) are ignored.
     */
    public boolean isServer() { return Agent.serverMode; }
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
    public void chat(String message) { if (Agent.serverMode) { log(message); return; } ChatHud.addLine(message); }
    /** Plays a named sound event (step, break, place, pickup, eat, pop, hurt, click) or a WAV from {@code instance/sounds/<name>.wav}. */
    public void playSound(String name) { if (!Agent.serverMode) BuiltinUI.sound().play(name); }

    // ---- Commands, translations, settings ----------------------------------------------

    /** Registers {@code /name}; shown by {@code /chelp} and completed with Tab. */
    public void registerCommand(String name, String description, CommandHandler handler) { if (!Agent.serverMode) ClientCommands.register(name, description, handler); }
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
        if (Agent.serverMode) return false;
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
        Items.Stack held = BuiltinUI.held();
        if (held != null && held.isBlock()) { NativeBlocks.Def nd = NativeBlocks.of(held.block); if (nd != null) return nd.id(); }
        Items.Stack s = BuiltinUI.held();
        return s == null ? null : s.isBlock() ? s.block.name().toLowerCase() : s.item.id;
    }

    /**
     * Adds a shaped crafting recipe. {@code pattern} rows use one character per cell (space =
     * empty), e.g. {@code {"MM", "MS", " S"}}; {@code keys} maps characters to item ids or block
     * names: {@code "M", "iron_ingot", "S", "stick"}.
     */
    public boolean registerRecipe(String resultId, int resultCount, String[] pattern, String... keys) {
        if (Agent.serverMode) return false;
        if (deferIfLoading(() -> registerRecipe(resultId, resultCount, pattern, keys))) return true;
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

    /** Called when a player right-clicks a mod block; return true to consume the click. */
    public interface BlockUseHandler { boolean use(int x, int y, int z); }

    /**
     * Registers a textured cube block with a namespaced id ({@code "mymod:ruby_block"}), like Fabric.
     * Textures are 16x16 PNG paths inside the calling mod's jar ({@code top} is required, {@code side}
     * and {@code bottom} fall back to it). {@code baseBlock} is the engine block it is stored as in the
     * world (collision, mining time, light), e.g. {@code "STONE"}. The block gets an item with the same
     * id, usable in {@link #giveItem}, recipes and the creative inventory; placed blocks are saved with
     * the world. Call from {@code onLoad} or {@code onGameReady}. Returns false if registration failed.
     */
    public boolean registerBlock(String id, String displayName, String baseBlock, String topTexture, String sideTexture, String bottomTexture, BlockUseHandler onUse) {
        if (Agent.serverMode) return false;
        return registerBlockFrom(callerLoader(), id, displayName, baseBlock, topTexture, sideTexture, bottomTexture, onUse);
    }

    /** Short form: one texture for every face, no right-click behaviour. */
    public boolean registerBlock(String id, String displayName, String baseBlock, String texture) {
        if (Agent.serverMode) return false;
        return registerBlockFrom(callerLoader(), id, displayName, baseBlock, texture, null, null, null);
    }

    private final java.util.List<Runnable> deferred = new java.util.ArrayList<>();

    /**
     * Calls that need the game's blocks (recipes, ModLoader blocks) made during {@code onLoad} are run
     * once the game is ready, so they never load BlockType before all native blocks are registered.
     */
    private boolean deferIfLoading(Runnable action) {
        if (NativeBlocks.isFrozen()) return false;
        synchronized (deferred) { deferred.add(action); }
        return true;
    }

    /** Runs the calls deferred during loading (called by the loader when the engine exists). */
    void runDeferred() {
        java.util.List<Runnable> copy;
        synchronized (deferred) { copy = new java.util.ArrayList<>(deferred); deferred.clear(); }
        for (Runnable r : copy) try { r.run(); } catch (Throwable error) { log("deferred mod call failed: " + error); }
    }

    private boolean registerBlockFrom(ClassLoader loader, String id, String displayName, String baseBlock, String topTexture, String sideTexture, String bottomTexture, BlockUseHandler onUse) {
        if (deferIfLoading(() -> registerBlockFrom(loader, id, displayName, baseBlock, topTexture, sideTexture, bottomTexture, onUse))) return NativeBlocks.validId(id);
        if (!BlockRegistry.validId(id)) { log("registerBlock: invalid id '" + id + "' (use namespace:path, lowercase, e.g. mymod:ruby_block)"); return false; }
        byte[] top = resource(loader, topTexture);
        if (top == null) { log("registerBlock " + id + ": texture not found in the mod jar: " + topTexture); return false; }
        return BlockRegistry.registerCube(id, displayName, baseBlock, top, resource(loader, sideTexture), resource(loader, bottomTexture), onUse) != null;
    }

    /** Class loader of the mod that called into ModContext (its jar holds the textures). */
    private static ClassLoader callerLoader() {
        Class<?> caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames.map(StackWalker.StackFrame::getDeclaringClass).filter(c -> c != ModContext.class).findFirst().orElse(ModContext.class));
        return caller.getClassLoader() != null ? caller.getClassLoader() : ClassLoader.getSystemClassLoader();
    }

    /**
     * Starts the definition of a native block: a real engine block type, like Fabric/Forge registration.
     * Call from {@code onLoad} (before the game loads its blocks) and finish with {@code register()}.
     * Textures are 16x16 PNG paths inside your mod jar.
     *
     * <pre>{@code
     * ctx.nativeBlock("mymod:ruby_block").name("Ruby Block").texture("assets/mymod/ruby_block.png")
     *    .hardness(3f).tool("pickaxe", true).register();
     * }</pre>
     *
     * The same mod jar must be installed on a dedicated server (with the ModLoader agent) for players
     * there to place it. The block id becomes usable in {@link #giveItem}, recipes and the creative tab.
     */
    public NativeBlocks.Builder nativeBlock(String id) { return NativeBlocks.builder(id, callerLoader()); }

    /** Ids of every ModLoader block ({@code modloader:chest}, mod blocks...). */
    public java.util.List<String> blockIds() { java.util.List<String> out = new java.util.ArrayList<>(); for (BlockRegistry.BlockDef d : BlockRegistry.all()) out.add(d.id); return out; }

    /** Id of the ModLoader block at a position ({@code "modloader:chest"}) or null if it is a plain engine block. */
    public String getBlockId(int x, int y, int z) { Tiles.Tile t = Tiles.at(x, y, z); return t == null ? null : t.def.id; }

    private static byte[] resource(ClassLoader loader, String path) {
        if (path == null || path.isEmpty()) return null;
        String p = path.startsWith("/") ? path.substring(1) : path;
        try (java.io.InputStream in = loader.getResourceAsStream(p)) { return in == null ? null : in.readAllBytes(); }
        catch (java.io.IOException e) { return null; }
    }

    /** All known item ids (not blocks). */
    public java.util.List<String> itemIds() { java.util.List<String> out = new java.util.ArrayList<>(); for (Items.Item i : Items.ALL) out.add(i.id); return out; }

    private static Items.Stack stack(String id, int count) {
        if (id == null) return null;
        Items.Item item = Items.byId(id.toLowerCase());
        if (item != null) return Items.Stack.of(item, Math.max(1, count));
        BlockType nb = NativeBlocks.type(id.toLowerCase());
        if (nb != null) return Items.Stack.of(nb, Math.max(1, count));
        try { return Items.Stack.of(BlockType.valueOf(id.toUpperCase()), Math.max(1, count)); } catch (IllegalArgumentException e) { return null; }
    }
}
