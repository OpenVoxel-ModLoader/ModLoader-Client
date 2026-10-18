package com.happyuky7.openvoxelmoding.loader;

import net.openvoxel.engine.VoxelEngine;
import net.openvoxel.engine.ui.ChatHud;
import net.openvoxel.engine.world.block.BlockType;
import org.joml.Vector3d;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Services the OpenVoxel ModLoader offers to mods. One instance is shared by every mod and passed to
 * each {@link ClientMod} hook.
 *
 * <p><b>Threads.</b> Call these methods from the hooks (the game's main thread). From other threads,
 * hand work back with {@code ctx.engine().runOnMainThread(...)}.
 *
 * <p><b>Ids.</b> Items use plain ids ({@code iron_sword}, {@code stick}), engine blocks use their
 * names ({@code stone}, {@code oak_log}), and mod blocks use namespaced ids ({@code mymod:ruby_block}).
 * Every method that takes an id accepts any of the three.
 *
 * <p><b>Dedicated servers.</b> The same mod jar can run on a server through the ModLoader agent; there
 * only {@link ClientMod#onLoad} runs, {@link #isServer()} is true and client features are ignored.
 */
public final class ModContext {

    /** Handler of a client chat command. */
    public interface CommandHandler {
        /**
         * @param args the words typed after the command name (never null, may be empty)
         */
        void run(String[] args);
    }

    /** Right-click behaviour of a block registered with {@link #registerBlock}. */
    public interface BlockUseHandler {
        /**
         * @param x block x
         * @param y block y
         * @param z block z
         * @return true to consume the click (the player does not place the held block)
         */
        boolean use(int x, int y, int z);
    }

    private final Path gameDirectory;
    private volatile Object engine;
    private final Map<String, ModConfig> configs = new HashMap<>();
    private final List<Runnable> deferred = new ArrayList<>();

    ModContext(Path gameDirectory) { this.gameDirectory = gameDirectory; }
    void setEngine(Object engine) { this.engine = engine; }

    // ---- Environment ---------------------------------------------------------------------

    /** @return the directory the game runs in ({@code Game/}) */
    public Path getGameDirectory() { return gameDirectory; }

    /**
     * @return true when this mod was loaded by the ModLoader agent inside a dedicated server. There only
     *         {@link #nativeBlock} and configuration matter; chat, sounds, commands, recipes and items are ignored.
     */
    public boolean isServer() { return Agent.serverMode; }

    /** @return {@code instance/}: mods, config, logs, lang, resourcepacks, sounds, music */
    public Path getInstanceDirectory() { return Agent.instanceDir(); }

    /** @return {@code instance/config} */
    public Path getConfigDirectory() { return Agent.instanceDir().resolve("config"); }

    /** @return the {@code net.openvoxel.engine.VoxelEngine} once the game is ready, else null */
    public Object getEngine() { return engine; }

    /**
     * @param type the class to cast to (usually {@code VoxelEngine.class})
     * @param <T> engine type
     * @return the engine cast to {@code type}, or null before {@link ClientMod#onGameReady}
     */
    public <T> T getEngine(Class<T> type) { return type.cast(engine); }

    /** @return the game engine, or null before {@link ClientMod#onGameReady} */
    public VoxelEngine engine() { return (VoxelEngine) engine; }

    /** @return true while a world (singleplayer or multiplayer) is loaded */
    public boolean isInWorld() { VoxelEngine e = engine(); return e != null && e.isWorldActive() && e.getCamera() != null; }

    /** @return a copy of the player's feet position, or null when not in a world */
    public Vector3d getPlayerPosition() { return isInWorld() ? new Vector3d(engine().getCamera().getPosition()) : null; }

    /** @return camera yaw in degrees (0 = north, 90 = east), 0 when not in a world */
    public float getPlayerYaw() { return isInWorld() ? engine().getCamera().getYaw() : 0f; }

    /** @return camera pitch in degrees (positive looks down), 0 when not in a world */
    public float getPlayerPitch() { return isInWorld() ? engine().getCamera().getPitch() : 0f; }

    // ---- Chat, logging, sound --------------------------------------------------------------

    /**
     * Writes {@code [ModLoader] message} to the game log / console.
     *
     * @param message text to log
     */
    public void log(String message) { System.out.println("[ModLoader] " + message); }

    /**
     * Prints a line in the in-game chat. Supports color codes: {@code &0}..{@code &f}, {@code &r}
     * and hex colors {@code &#rrggbb}.
     *
     * @param message text, with optional color codes
     */
    public void chat(String message) { if (Agent.serverMode) { log(message); return; } ChatHud.addLine(message); }

    /**
     * Plays a sound event: {@code step}, {@code break}, {@code place}, {@code pickup}, {@code eat},
     * {@code pop}, {@code hurt}, {@code click}, or any {@code instance/sounds/<name>.wav}.
     *
     * @param name sound event name
     */
    public void playSound(String name) { if (!Agent.serverMode) BuiltinUI.sound().play(name); }

    // ---- Commands, translations, configuration ---------------------------------------------

    /**
     * Registers a client chat command {@code /name}. It is listed by {@code /chelp} and completed with
     * Tab. Client commands run before anything is sent to the server; unknown commands go to the server.
     *
     * @param name command name without the slash (case-insensitive)
     * @param description one line shown by {@code /chelp}, e.g. {@code "<player> - greets someone"}
     * @param handler called with the arguments
     */
    public void registerCommand(String name, String description, CommandHandler handler) { if (!Agent.serverMode) ClientCommands.register(name, description, handler); }

    /**
     * Looks a key up in the current language, then English, then returns the key itself. Sources, in
     * override order: the loader, every mod's {@code assets/<id>/lang/<code>.json}, {@code instance/lang}.
     *
     * @param key translation key
     * @param args {@link String#format} arguments for {@code %s}/{@code %d} placeholders
     * @return the translated text
     */
    public String translate(String key, Object... args) { return Lang.t(key, args); }

    /** @return the current language code ({@code en_us}, {@code es_es}...) */
    public String language() { return Lang.current(); }

    /**
     * Per-mod settings stored at {@code instance/config/<modId>.properties}.
     *
     * @param modId your mod id
     * @return the (cached) config; changes are saved with {@link ModConfig#save()} and on shutdown
     */
    public ModConfig config(String modId) { return configs.computeIfAbsent(modId, ModConfig::new); }
    void saveConfigs() { for (ModConfig c : configs.values()) c.save(); }

    // ---- Player state and items ----------------------------------------------------------

    /** @return true in creative mode */
    public boolean isCreative() { return Settings.isCreative(); }

    /** @return true in survival mode */
    public boolean isSurvival() { return !Settings.isCreative(); }

    /** @return true while flying (creative only) */
    public boolean isFlying() { return GameMode.flying; }

    /**
     * Puts items in the player's inventory: stacks with existing ones (max 64), then the first free
     * hotbar slot, then storage.
     *
     * @param id item id, engine block name or mod block id
     * @param count how many (at least 1)
     * @return false when the inventory is full, the id is unknown, or on a server
     */
    public boolean giveItem(String id, int count) {
        if (Agent.serverMode) return false;
        Items.Stack stack = stack(id, count);
        if (stack == null) return false;
        boolean ok = Mining.give(stack);
        if (ok) Mining.pickup(stack);
        return ok;
    }

    /**
     * Selects a hotbar slot, like pressing 1..9.
     *
     * @param slot 0..8 (clamped)
     */
    public void setHotbarSlot(int slot) { BuiltinUI.select(engine(), Math.max(0, Math.min(8, slot))); }

    /** @return id of what the player holds (item id, block name or mod block id), or null for an empty hand */
    public String getHeldItem() {
        Items.Stack held = BuiltinUI.held();
        if (held == null) return null;
        if (held.isBlock()) { NativeBlocks.Def nd = NativeBlocks.of(held.block); if (nd != null) return nd.id(); return held.block.name().toLowerCase(); }
        return held.item.id;
    }

    /** @return every item id known to the loader (not blocks) */
    public List<String> itemIds() { List<String> out = new ArrayList<>(); for (Items.Item i : Items.ALL) out.add(i.id); return out; }

    /**
     * Adds a shaped crafting recipe. It matches at any position in the grid and mirrored; recipes up to
     * 2x2 also work in the inventory grid, bigger ones need the crafting table. Safe to call in onLoad
     * (it is applied once the game is ready).
     *
     * <pre>{@code
     * ctx.registerRecipe("torch", 4, new String[]{"C", "S"}, "C", "coal", "S", "stick");
     * }</pre>
     *
     * @param resultId item id, block name or mod block id of the result
     * @param resultCount how many items one craft gives
     * @param pattern rows of the shape, one character per cell (space = empty)
     * @param keys pairs of character and ingredient id: {@code "M", "iron_ingot", "S", "stick"}
     * @return false if an id is unknown or keys are not pairs (true when deferred during onLoad)
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

    // ---- Blocks ----------------------------------------------------------------------------

    /**
     * Starts the definition of a <b>native block</b>: a real engine block type, like block registration
     * in Fabric or Forge. The game treats it like its own blocks (rendering, lighting, collision, mining,
     * saving, networking, inventory). Call it from {@link ClientMod#onLoad} and finish with
     * {@link NativeBlocks.Builder#register()}.
     *
     * <pre>{@code
     * ctx.nativeBlock("mymod:ruby_block")
     *    .name("Ruby Block")
     *    .texture("assets/mymod/textures/ruby_block.png")
     *    .hardness(3f).tool("pickaxe", true)
     *    .register();
     * }</pre>
     *
     * For multiplayer, install the same mod jar on the server ({@code ModLoader-Server/mods}) and run the
     * server with the ModLoader agent ({@code ModLoader-Server/agent}); both sides then agree on the ids.
     *
     * @param id namespaced id {@code namespace:path}, lowercase
     * @return a builder; textures are read from the calling mod's jar
     * @see NativeBlocks
     */
    public NativeBlocks.Builder nativeBlock(String id) { return NativeBlocks.builder(id, callerLoader()); }

    /**
     * Registers a <b>ModLoader block</b>: a textured cube stored in the world as an engine block
     * ({@code baseBlock}) plus a block code in its state. Prefer {@link #nativeBlock} for new blocks; this
     * kind exists for blocks that need right-click behaviour or must work on servers without the agent.
     *
     * @param id namespaced id ({@code mymod:ruby_block})
     * @param displayName name shown in the inventory when no translation exists
     * @param baseBlock engine block it is stored as (collision, mining, light), e.g. {@code "STONE"}
     * @param topTexture 16x16 PNG path inside your jar (required)
     * @param sideTexture side texture, or null for {@code topTexture}
     * @param bottomTexture bottom texture, or null for {@code topTexture}
     * @param onUse right-click handler, or null
     * @return false if the id is invalid or taken, or a texture is missing
     */
    public boolean registerBlock(String id, String displayName, String baseBlock, String topTexture, String sideTexture, String bottomTexture, BlockUseHandler onUse) {
        if (Agent.serverMode) return false;
        return registerBlockFrom(callerLoader(), id, displayName, baseBlock, topTexture, sideTexture, bottomTexture, onUse);
    }

    /**
     * Short form of {@link #registerBlock(String, String, String, String, String, String, BlockUseHandler)}:
     * one texture for every face and no right-click behaviour.
     *
     * @param id namespaced id
     * @param displayName fallback name
     * @param baseBlock engine block it is stored as
     * @param texture 16x16 PNG path inside your jar
     * @return false if registration failed
     */
    public boolean registerBlock(String id, String displayName, String baseBlock, String texture) {
        if (Agent.serverMode) return false;
        return registerBlockFrom(callerLoader(), id, displayName, baseBlock, texture, null, null, null);
    }

    /** @return ids of every ModLoader block ({@code modloader:chest}, mod blocks registered with registerBlock) */
    public List<String> blockIds() { List<String> out = new ArrayList<>(); for (BlockRegistry.BlockDef d : BlockRegistry.all()) out.add(d.id); return out; }

    /**
     * @param x block x
     * @param y block y
     * @param z block z
     * @return id of the ModLoader block at that position, or null for any other block
     */
    public String getBlockId(int x, int y, int z) { Tiles.Tile t = Tiles.at(x, y, z); return t == null ? null : t.def.id; }

    // ---- internals -------------------------------------------------------------------------

    /** Calls that need the game's blocks made during onLoad run once the game is ready (keeps BlockType unloaded). */
    private boolean deferIfLoading(Runnable action) {
        if (NativeBlocks.isFrozen()) return false;
        synchronized (deferred) { deferred.add(action); }
        return true;
    }

    void runDeferred() {
        List<Runnable> copy;
        synchronized (deferred) { copy = new ArrayList<>(deferred); deferred.clear(); }
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
                .walk(frames -> frames.<Class<?>>map(StackWalker.StackFrame::getDeclaringClass).filter(c -> c != ModContext.class).findFirst().orElse(ModContext.class));
        return caller.getClassLoader() != null ? caller.getClassLoader() : ClassLoader.getSystemClassLoader();
    }

    private static byte[] resource(ClassLoader loader, String path) {
        if (path == null || path.isEmpty()) return null;
        String p = path.startsWith("/") ? path.substring(1) : path;
        try (java.io.InputStream in = loader.getResourceAsStream(p)) { return in == null ? null : in.readAllBytes(); }
        catch (java.io.IOException e) { return null; }
    }

    private static Items.Stack stack(String id, int count) {
        if (id == null) return null;
        Items.Item item = Items.byId(id.toLowerCase());
        if (item != null) return Items.Stack.of(item, Math.max(1, count));
        BlockType nb = NativeBlocks.type(id.toLowerCase());
        if (nb != null) return Items.Stack.of(nb, Math.max(1, count));
        try { return Items.Stack.of(BlockType.valueOf(id.toUpperCase()), Math.max(1, count)); } catch (IllegalArgumentException e) { return null; }
    }
}
