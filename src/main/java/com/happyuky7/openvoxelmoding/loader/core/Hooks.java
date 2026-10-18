package com.happyuky7.openvoxelmoding.loader.core;

import com.happyuky7.openvoxelmoding.loader.BlockRegistry;
import com.happyuky7.openvoxelmoding.loader.Lang;
import com.happyuky7.openvoxelmoding.loader.NativeBlocks;

import com.happyuky7.openvoxelmoding.loader.command.ClientCommands;
import com.happyuky7.openvoxelmoding.loader.cosmetics.Skins;
import com.happyuky7.openvoxelmoding.loader.gameplay.GameMode;
import com.happyuky7.openvoxelmoding.loader.gameplay.Mobs;
import com.happyuky7.openvoxelmoding.loader.gameplay.Survival;
import com.happyuky7.openvoxelmoding.loader.item.Drops;
import com.happyuky7.openvoxelmoding.loader.item.Items;
import com.happyuky7.openvoxelmoding.loader.item.Mining;
import com.happyuky7.openvoxelmoding.loader.item.PickBlock;
import com.happyuky7.openvoxelmoding.loader.net.Net;
import com.happyuky7.openvoxelmoding.loader.net.ServerStatus;
import com.happyuky7.openvoxelmoding.loader.ui.BuiltinUI;
import com.happyuky7.openvoxelmoding.loader.ui.Chat;
import com.happyuky7.openvoxelmoding.loader.ui.ChatColors;
import com.happyuky7.openvoxelmoding.loader.ui.DebugScreen;
import com.happyuky7.openvoxelmoding.loader.ui.HandRenderer;
import com.happyuky7.openvoxelmoding.loader.ui.Keybinds;
import com.happyuky7.openvoxelmoding.loader.ui.Screens;
import com.happyuky7.openvoxelmoding.loader.world.Lighting;
import com.happyuky7.openvoxelmoding.loader.world.Tiles;
import com.happyuky7.openvoxelmoding.loader.world.Torches;

import net.openvoxel.client.ClientMain;
import net.openvoxel.client.SessionInfo;
import net.openvoxel.engine.VoxelEngine;
import net.openvoxel.engine.input.InputHandler;
import net.openvoxel.engine.rendering.Renderer;
import net.openvoxel.engine.rendering.Texture;
import net.openvoxel.engine.ui.ChatHud;
import net.openvoxel.engine.ui.GLUIRenderer;
import net.openvoxel.engine.world.block.BlockState;
import net.openvoxel.engine.world.block.BlockType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Targets of the bytecode wrappers installed by {@link Transformer}. Every method here
 * receives the original arguments and can call the renamed original through {@link #orig}.
 * Must stay public: it is invoked from game classes.
 */
public final class Hooks {
    private static final Map<String, MethodHandle> CACHE = new ConcurrentHashMap<>();
    private Hooks() {}

    private static MethodHandle orig(String cls, String name, boolean isStatic, Class<?> ret, Class<?>... args) {
        return handle(cls, name + "$orig", isStatic, ret, args);
    }

    private static MethodHandle handle(String cls, String name, boolean isStatic, Class<?> ret, Class<?>... args) {
        String key = cls + "#" + name;
        MethodHandle handle = CACHE.get(key);
        if (handle != null) return handle;
        try {
            Class<?> owner = Class.forName(cls, false, ClassLoader.getSystemClassLoader());
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(owner, MethodHandles.lookup());
            MethodType type = MethodType.methodType(ret, args);
            handle = isStatic ? lookup.findStatic(owner, name, type) : lookup.findVirtual(owner, name, type);
            CACHE.put(key, handle);
            return handle;
        } catch (Throwable error) {
            throw new IllegalStateException("method not found: " + key, error);
        }
    }

    private static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException) return (RuntimeException) t;
        if (t instanceof Error) throw (Error) t;
        return new RuntimeException(t);
    }

    /** Replaces GLFW.glfwGetKey inside the engine's InputHandler so controls can be rebound. */
    public static int glfwGetKey(long window, int key) {
        return org.lwjgl.glfw.GLFW.glfwGetKey(window, Keybinds.remap(key));
    }

    // ---- GLUIRenderer / widgets --------------------------------------------------------

    public static void drawTextOrig(String text, float x, float y, float r, float g, float b, float a) {
        try { orig("net.openvoxel.engine.ui.GLUIRenderer", "drawText", true, void.class, String.class, float.class, float.class, float.class, float.class, float.class, float.class).invoke(text, x, y, r, g, b, a); }
        catch (Throwable t) { throw rethrow(t); }
    }

    public static float textWidthOrig(String text) {
        try { return (float) orig("net.openvoxel.engine.ui.GLUIRenderer", "getTextWidth", true, float.class, String.class).invoke(text); }
        catch (Throwable t) { throw rethrow(t); }
    }

    public static void drawText(String text, float x, float y, float r, float g, float b, float a) {
        ChatColors.drawText(text, x, y, r, g, b, a);
    }

    public static float getTextWidth(String text) {
        return textWidthOrig(ChatColors.raw ? text : ChatColors.strip(text));
    }

    /** Text fields show formatting codes literally; they are only coloured once printed in chat. */
    public static void textFieldRender(Object field) {
        ChatColors.raw = true;
        try { orig("net.openvoxel.engine.ui.widgets.TextField", "renderWidget", false, void.class).invoke(field); }
        catch (Throwable t) { throw rethrow(t); }
        finally { ChatColors.raw = false; }
    }

    public static void chatKey(Object screen, int key) {
        try { if (Chat.onKey(screen, key)) return; } catch (Throwable error) { System.err.println("[ModLoader] chat key: " + error); }
        try { orig("net.openvoxel.engine.ui.widgets.ChatScreen", "onKeyPress", false, void.class, int.class).invoke(screen, key); }
        catch (Throwable t) { throw rethrow(t); }
    }

    public static void chatRender(VoxelEngine engine) {
        if (ChatHud.isInputOpen() && engine.isWorldActive()) {
            try { Chat.render(engine); return; } catch (Throwable error) { System.err.println("[ModLoader] chat render: " + error); }
        }
        try { orig("net.openvoxel.engine.ui.ChatHud", "render", true, void.class, VoxelEngine.class).invoke(engine); }
        catch (Throwable t) { throw rethrow(t); }
    }

    public static ByteBuffer fontData() {
        try {
            Path custom = Agent.instanceDir().resolve("config").resolve("font.ttf");
            if (Files.isRegularFile(custom)) {
                byte[] data = Files.readAllBytes(custom);
                ByteBuffer buffer = org.lwjgl.system.MemoryUtil.memAlloc(data.length);
                buffer.put(data).flip();
                System.out.println("[ModLoader] Using custom font " + custom);
                return buffer;
            }
        } catch (Throwable error) { System.err.println("[ModLoader] Custom font failed: " + error); }
        try { return (ByteBuffer) orig("net.openvoxel.engine.ui.GLUIRenderer", "createDefaultFontData", true, ByteBuffer.class).invoke(); }
        catch (Throwable t) { throw rethrow(t); }
    }

    // ---- Physics / rendering -----------------------------------------------------------

    public static void physicsTick(Object physics, float delta, boolean jump, boolean crouch) {
        if (GameMode.skipPhysics()) return;
        try { orig("net.openvoxel.engine.physics.PhysicsEngine", "tick", false, void.class, float.class, boolean.class, boolean.class).invoke(physics, delta, jump, crouch); }
        catch (Throwable t) { throw rethrow(t); }
    }

    public static void moveHorizontal(Object physics, double dx, double dz, double height) {
        double[] clamped;
        try { clamped = GameMode.sneakClamp(dx, dz); } catch (Throwable error) { clamped = new double[]{dx, dz}; }
        try { orig("net.openvoxel.engine.physics.PhysicsEngine", "moveHorizontal", false, void.class, double.class, double.class, double.class).invoke(physics, clamped[0], clamped[1], height); }
        catch (Throwable t) { throw rethrow(t); }
    }

    public static void renderWorld(Object renderer, InputHandler input) {
        try { Lighting.beforeRender((Renderer) renderer); } catch (Throwable error) { Lighting.reportOnce(error); }
        try { orig("net.openvoxel.engine.rendering.Renderer", "render", false, void.class, InputHandler.class).invoke(renderer, input); }
        catch (Throwable t) { throw rethrow(t); }
        try { HandRenderer.render((Renderer) renderer); }
        catch (Throwable error) { HandRenderer.reportOnce(error); }
    }

    /** After the engine compiles its chunk shader, swap in the ModLoader one (fog, tone curve). */
    public static void setupShaders(Object renderer) {
        try { orig("net.openvoxel.engine.rendering.Renderer", "setupShaders", false, void.class).invoke(renderer); }
        catch (Throwable t) { throw rethrow(t); }
        try { Lighting.install((Renderer) renderer); } catch (Throwable error) { Lighting.reportOnce(error); }
    }

    private static Field textureFilepath;

    /** Resource-pack override: {@code client/instance/resourcepacks/<pack>/<filepath>}. */
    public static void textureDecode(Object texture) {
        byte[] nativePng = NativeBlocks.pngOf(texture);
        if (nativePng != null) {
            try { handle("net.openvoxel.engine.rendering.Texture", "decodeFromBytes", false, void.class, byte[].class).invoke(texture, nativePng); return; }
            catch (Throwable t) { throw rethrow(t); }
        }
        try {
            if (!Settings.resourcePack.isEmpty()) {
                if (textureFilepath == null) { textureFilepath = Texture.class.getDeclaredField("filepath"); textureFilepath.setAccessible(true); }
                String path = (String) textureFilepath.get(texture);
                if (path != null && !path.startsWith("<")) {
                    Path override = Agent.instanceDir().resolve("resourcepacks").resolve(Settings.resourcePack).resolve(path.replaceFirst("^\\./", ""));
                    if (Files.isRegularFile(override)) {
                        byte[] png = Files.readAllBytes(override);
                        handle("net.openvoxel.engine.rendering.Texture", "decodeFromBytes", false, void.class, byte[].class).invoke(texture, png);
                        return;
                    }
                }
            }
        } catch (Throwable error) { System.err.println("[ModLoader] texture override failed: " + error); }
        try { orig("net.openvoxel.engine.rendering.Texture", "decode", false, void.class).invoke(texture); }
        catch (Throwable t) { throw rethrow(t); }
    }

    public static void debugRender(VoxelEngine engine) {
        try { DebugScreen.render(engine); }
        catch (Throwable error) { DebugScreen.reportOnce(error); }
    }

    public static void statusRender(VoxelEngine e) {
        boolean alive = e.isWorldActive() && e.getLocalPlayer() != null && !e.getLocalPlayer().dead;
        if (!alive && e.isWorldActive() && e.getLocalPlayer() != null) return;      // DeathScreen replaces "Press R to respawn"
        if (alive && Settings.isCreative()) return;
        if (alive) {
            int[] aspect = e.getCamera() != null ? e.getCamera().getAspect() : new int[]{1280, 720};
            GLUIRenderer.setup2DRendering(aspect[0], aspect[1]);
            try { Survival.renderHud(e, aspect[0], aspect[1]); } finally { GLUIRenderer.restore3DRendering(); }
            return;
        }
        try { orig("net.openvoxel.engine.ui.StatusHud", "render", true, void.class, VoxelEngine.class).invoke(e); }
        catch (Throwable t) { throw rethrow(t); }
    }

    // ---- Engine ------------------------------------------------------------------------

    public static void sendChat(Object engine, String message) {
        Chat.recordSent(message);
        if (message != null && message.startsWith("/") && ClientCommands.handle(message)) return;
        sendChatOrig(engine, message);
    }

    public static void sendChatOrig(Object engine, String message) {
        try { orig("net.openvoxel.engine.VoxelEngine", "sendChat", false, void.class, String.class).invoke(engine, message); }
        catch (Throwable t) { throw rethrow(t); }
    }

    /** Incoming chat lines: ModLoader server control messages are consumed here. */
    public static void chatLine(String text) {
        try { if (Net.handleLine(text)) return; } catch (Throwable error) { System.err.println("[ModLoader] chat line: " + error); }
        try { orig("net.openvoxel.engine.ui.ChatHud", "addLine", true, void.class, String.class).invoke(text); }
        catch (Throwable t) { throw rethrow(t); }
    }

    // ---- Blocks (torches) --------------------------------------------------------------

    public static net.openvoxel.engine.world.block.model.BlockModel blockModel(Object type, long state) {
        net.openvoxel.engine.world.block.model.BlockModel base;
        try { base = (net.openvoxel.engine.world.block.model.BlockModel) orig("net.openvoxel.engine.world.block.BlockType", "getModel", false, net.openvoxel.engine.world.block.model.BlockModel.class, long.class).invoke(type, state); }
        catch (Throwable t) { throw rethrow(t); }
        try { if (type == BlockType.TORCH && Torches.isWall(state)) return Torches.wallModel(base, Torches.facing(state)); }
        catch (Throwable error) { System.err.println("[ModLoader] torch model: " + error); }
        return base;
    }

    /** Chunk mesher texture lookup: ModLoader blocks (code in the state) get their own textures. */
    public static net.openvoxel.engine.rendering.Texture blockTexture(Object type, int face, long state) {
        if ((state >>> BlockRegistry.CODE_SHIFT) != 0) {
            BlockRegistry.BlockDef def = BlockRegistry.ofState(state);
            if (def != null && def.meshTop != null) {
                net.openvoxel.engine.rendering.Texture t = def.meshFace(face, BlockRegistry.facingIn(state));
                if (t != null) return t;
            }
        }
        try { return (net.openvoxel.engine.rendering.Texture) orig("net.openvoxel.engine.world.block.BlockType", "getTextureForFace", false, net.openvoxel.engine.rendering.Texture.class, int.class, long.class).invoke(type, face, state); }
        catch (Throwable t) { throw rethrow(t); }
    }

    public static long placementState(Object type, net.openvoxel.engine.world.block.BlockPlacement.PlacementContext ctx, long base) {
        if (type == BlockType.TORCH) try { return Torches.placementState(ctx, base); } catch (Throwable ignored) { }
        try { return (long) orig("net.openvoxel.engine.world.block.BlockType", "computePlacementState", false, long.class, net.openvoxel.engine.world.block.BlockPlacement.PlacementContext.class, long.class).invoke(type, ctx, base); }
        catch (Throwable t) { throw rethrow(t); }
    }

    /** Torches are never replaced by a placed block (the block goes next to them, like Minecraft). */
    public static boolean canReplaceInto(BlockType held, BlockType existing, long existingState) {
        if (existing == BlockType.TORCH) return false;
        try { return (boolean) orig("net.openvoxel.engine.world.block.BlockPlacement", "canReplaceInto", true, boolean.class, BlockType.class, BlockType.class, long.class).invoke(held, existing, existingState); }
        catch (Throwable t) { throw rethrow(t); }
    }


    public static void applyEditOrig(VoxelEngine e, int x, int y, int z, net.openvoxel.engine.world.AbstractBlock block) {
        try { orig("net.openvoxel.engine.VoxelEngine", "applyEdit", false, void.class, int.class, int.class, int.class, net.openvoxel.engine.world.AbstractBlock.class).invoke(e, x, y, z, block); }
        catch (Throwable t) { throw rethrow(t); }
    }

    /** Right clicks on tiles (crafting table, chest, bed) and tile placement go through {@link Tiles}. */
    public static void fireEvent(Object manager, net.openvoxel.engine.events.GameEvent event) {
        VoxelEngine e = VoxelEngine.getEngine();
        boolean tilePlacement = false, pick = false;
        try {
            if (event instanceof net.openvoxel.engine.events.player.ClickEvent && e != null) {
                net.openvoxel.engine.events.player.ClickEvent click = (net.openvoxel.engine.events.player.ClickEvent) event;
                if (click.type == net.openvoxel.engine.events.player.ClickEvent.ClickType.LEFT && Mobs.suppressBlockClick()) return;
                pick = click.type == net.openvoxel.engine.events.player.ClickEvent.ClickType.MIDDLE;
                if (click.type == net.openvoxel.engine.events.player.ClickEvent.ClickType.RIGHT) {
                    if (Tiles.onRightClick(e, click)) return;
                    tilePlacement = Tiles.pendingPlace != null;
                }
            }
        } catch (Throwable error) { System.err.println("[ModLoader] tile click: " + error); }
        try { orig("net.openvoxel.engine.events.GameEventManager", "fire", false, void.class, net.openvoxel.engine.events.GameEvent.class).invoke(manager, event); }
        catch (Throwable t) { throw rethrow(t); }
        finally {
            if (tilePlacement && e != null) Tiles.finishPlace(e);
            // Every listener (mods, sounds) saw the middle click; the engine's own pick only set the
            // hand block, which PickBlock now replaces with the Minecraft behaviour.
            if (pick && e != null) try { PickBlock.pick(e); BuiltinUI.select(e, BuiltinUI.selected); } catch (Throwable error) { System.err.println("[ModLoader] pick block: " + error); }
        }
    }

    /** Survival: breaks only after the mining timer (see {@link Mining}), drops go to the inventory, placing consumes stacks. */
    public static void applyEdit(Object engine, int x, int y, int z, net.openvoxel.engine.world.AbstractBlock block) {
        VoxelEngine e = (VoxelEngine) engine;
        BlockType before = null;
        long beforeState = 0;
        boolean active = e.isWorldActive() && e.getWorld() != null && block != null;
        if (active) {
            try { beforeState = e.getWorld().getState(x, y, z); before = BlockType.fromId(BlockState.typeId(beforeState)); } catch (Exception ignored) { active = false; }
        }
        // Placing a ModLoader block: the engine placed its base block; add the block code to the state so the
        // world (and the server, and other players) store which block it is.
        if (active && Tiles.pendingPlace != null && block.getType() == Tiles.pendingPlace.base && BlockRegistry.codeIn(block.getState()) == 0)
            block.setState(BlockRegistry.encode(block.getState(), Tiles.pendingPlace, Tiles.placementFacing(e)));
        boolean survival = active && !Settings.isCreative();
        if (survival && block.getType() == BlockType.AIR && !Mining.approved) return; // instant click: wait for the timer
        // Torches need support: a solid block below, or behind a wall torch.
        if (active && block.getType() == BlockType.TORCH && before != BlockType.TORCH) {
            try { if (!Torches.supported(e.getWorld(), x, y, z, block.getState())) return; } catch (Throwable ignored) { }
        }
        try { orig("net.openvoxel.engine.VoxelEngine", "applyEdit", false, void.class, int.class, int.class, int.class, net.openvoxel.engine.world.AbstractBlock.class).invoke(engine, x, y, z, block); }
        catch (Throwable t) { throw rethrow(t); }
        if (!active || before == null) return;
        try {
            if (block.getType() == BlockType.AIR) {
                if (survival && before != BlockType.AIR) Mining.onBroken(before, beforeState, x, y, z);
                else if (before != BlockType.AIR) { Items.Stack tile = Tiles.onBlockBroken(x, y, z, beforeState); if (tile != null) Drops.spawn(tile, x, y, z); }
                if (before != BlockType.AIR) Torches.onBlockRemoved(e, x, y, z);
            } else if (block.getType() != before) {
                if (Tiles.pendingPlace != null && block.getType() == Tiles.pendingPlace.base) Tiles.register(e, x, y, z);
                else if (survival) { Items.Stack held = BuiltinUI.held(); if (held != null && held.block == block.getType()) Mining.onPlaced(); }
            }
        } catch (Throwable error) { System.err.println("[ModLoader] inventory edit: " + error); }
    }

    public static void applyServerHealth(Object engine, float health, boolean dead) {
        if (Settings.isCreative()) { health = 20f; dead = false; }
        try { orig("net.openvoxel.engine.VoxelEngine", "applyServerHealth", false, void.class, float.class, boolean.class).invoke(engine, health, dead); }
        catch (Throwable t) { throw rethrow(t); }
    }

    // ---- Client ------------------------------------------------------------------------

    /**
     * The 0.0.8-Debug client aborts a multiplayer join when the auth server answers
     * "invalid session" to /session/join, even for LAN servers in offline mode. Skip the
     * intent for local addresses and never block the join on auth failures: an online-mode
     * server still rejects bad tokens itself with a clear kick reason.
     */
    public static String registerJoinIntent(String serverId) {
        String host = serverId;
        int colon = host.lastIndexOf(':');
        if (colon > 0) host = host.substring(0, colon);
        if (isLocalHost(host)) {
            System.out.println("[ModLoader] Local server " + serverId + ": skipping /session/join");
            return null;
        }
        SessionInfo session = ClientMain.getSession();
        if (session == null || !session.isLoggedIn()) return null;
        String error;
        try { error = (String) orig("net.openvoxel.client.ClientMain", "registerJoinIntent", true, String.class, String.class).invoke(serverId); }
        catch (Throwable t) { error = String.valueOf(t.getMessage()); }
        if (error != null) {
            System.out.println("[ModLoader] /session/join failed (" + error + "); joining anyway");
            ChatHud.addLine(Lang.t("chat.session", error));
        }
        return null;
    }

    static boolean isLocalHost(String host) {
        if (host == null || host.isEmpty()) return true;
        String h = host.trim().toLowerCase();
        if (h.equals("localhost") || h.endsWith(".local") || h.endsWith(".lan")) return true;
        try {
            InetAddress address = InetAddress.getByName(h);
            return address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress() || address.isAnyLocalAddress();
        } catch (Exception ignored) { return false; }
    }

    /** Remembers the singleplayer world folder so ModLoader blocks are saved inside that world. */
    public static void startSingleplayer(net.openvoxel.client.WorldSaves.Entry entry) {
        Tiles.worldDir = entry != null ? entry.dir : null;
        try { orig("net.openvoxel.client.ClientMain", "startSingleplayer", true, void.class, net.openvoxel.client.WorldSaves.Entry.class).invoke(entry); }
        catch (Throwable t) { throw rethrow(t); }
    }

    /** Every connection (singleplayer's integrated server too) goes through join(host, port, serverId). */
    public static void join(String host, int port, String serverId) {
        if (!"singleplayer".equals(serverId)) Tiles.worldDir = null;
        Tiles.serverId = host + "_" + port;
        if (!"singleplayer".equals(serverId)) Tiles.serverId = serverId != null && !serverId.isEmpty() ? serverId : host + "_" + port;
        try { orig("net.openvoxel.client.ClientMain", "join", true, void.class, String.class, int.class, String.class).invoke(host, port, serverId); }
        catch (Throwable t) { throw rethrow(t); }
    }

    /** Server list row: shows the server's MOTD in place of the address (see {@link ServerStatus}). */
    public static void serverRowRender(Object row) {
        String motd = null;
        net.openvoxel.engine.ui.widgets.Label address = null;
        try {
            motd = ServerStatus.motdOf(ServerStatus.pingOf(row));
            address = ServerStatus.hideAddress(((net.openvoxel.engine.ui.widgets.Container) row).getChildren(), motd);
        } catch (Throwable ignored) { }
        try { orig("net.openvoxel.client.ServerSelectScreen$ServerRowPanel", "renderWidget", false, void.class).invoke(row); }
        catch (Throwable t) { throw rethrow(t); }
        ServerStatus.drawMotd(address, motd);
    }

    private static java.lang.reflect.Field errTitle, errMessage;

    /** Connection/kick errors: explain the common ones (wrong game build, session, refused...) before drawing. */
    public static void errorScreenBuild(Object screen, float w, float h) {
        try {
            if (errTitle == null) {
                Class<?> c = Class.forName("net.openvoxel.client.ErrorScreen");
                errTitle = c.getDeclaredField("title"); errTitle.setAccessible(true);
                errMessage = c.getDeclaredField("message"); errMessage.setAccessible(true);
            }
            String title = (String) errTitle.get(screen), message = (String) errMessage.get(screen);
            if (message != null && !message.contains("\n\n(")) {
                String[] better = GameBuild.explain(title, message);
                if (better != null) { errTitle.set(screen, better[0]); errMessage.set(screen, better[1]); }
            }
        } catch (Throwable error) { System.err.println("[ModLoader] error screen: " + error); }
        try { orig("net.openvoxel.client.ErrorScreen", "build", false, void.class, float.class, float.class).invoke(screen, w, h); }
        catch (Throwable t) { throw rethrow(t); }
    }

    public static void appearanceApply(String base, byte[] skin, String modelJson, String uuid) {
        try {
            if (Skins.applyFromServer(skin, modelJson, uuid)) return;
        } catch (Throwable error) { System.err.println("[HappySkins] " + error); }
        try { orig("net.openvoxel.client.net.AppearanceLoader", "apply", true, void.class, String.class, byte[].class, String.class, String.class).invoke(base, skin, modelJson, uuid); }
        catch (Throwable t) { throw rethrow(t); }
    }

    public static void mainMenuBuild(Object screen, float w, float h) {
        try { Screens.buildMainMenu((net.openvoxel.engine.ui.widgets.Screen) screen, w, h); return; }
        catch (Throwable error) { System.err.println("[HappyUI] main menu: " + error); error.printStackTrace(); }
        try { orig("net.openvoxel.client.MainMenuScreen", "build", false, void.class, float.class, float.class).invoke(screen, w, h); }
        catch (Throwable t) { throw rethrow(t); }
    }

    public static void pauseMenuBuild(Object screen, float w, float h) {
        try { Screens.buildPauseMenu((net.openvoxel.engine.ui.widgets.Screen) screen, w, h); return; }
        catch (Throwable error) { System.err.println("[HappyUI] pause menu: " + error); error.printStackTrace(); }
        try { orig("net.openvoxel.engine.ui.PauseMenu", "build", false, void.class, float.class, float.class).invoke(screen, w, h); }
        catch (Throwable t) { throw rethrow(t); }
    }
}
