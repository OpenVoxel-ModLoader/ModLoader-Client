package com.happyuky7.openvoxelmoding.loader.ui;

import com.happyuky7.openvoxelmoding.loader.ClientMod;
import com.happyuky7.openvoxelmoding.loader.Lang;
import com.happyuky7.openvoxelmoding.loader.ModContext;

import com.happyuky7.openvoxelmoding.loader.audio.Music;
import com.happyuky7.openvoxelmoding.loader.audio.SoundSystem;
import com.happyuky7.openvoxelmoding.loader.core.Agent;
import com.happyuky7.openvoxelmoding.loader.core.Settings;
import com.happyuky7.openvoxelmoding.loader.cosmetics.Skins;
import com.happyuky7.openvoxelmoding.loader.gameplay.GameMode;
import com.happyuky7.openvoxelmoding.loader.gameplay.Mobs;
import com.happyuky7.openvoxelmoding.loader.gameplay.Survival;
import com.happyuky7.openvoxelmoding.loader.item.Drops;
import com.happyuky7.openvoxelmoding.loader.item.Items;
import com.happyuky7.openvoxelmoding.loader.item.Mining;
import com.happyuky7.openvoxelmoding.loader.net.Net;
import com.happyuky7.openvoxelmoding.loader.world.Tiles;

import net.openvoxel.engine.VoxelEngine;
import net.openvoxel.engine.gui.GUI;
import net.openvoxel.engine.gui.PlayerInventory;
import net.openvoxel.engine.rendering.Camera;
import net.openvoxel.engine.ui.GLUIRenderer;
import net.openvoxel.engine.ui.UIManager;
import net.openvoxel.engine.ui.widgets.ChatScreen;
import net.openvoxel.engine.world.AbstractBlock;
import net.openvoxel.engine.world.block.BlockType;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWScrollCallback;

import java.lang.reflect.Field;
import java.util.List;

/** Per-frame client integration: hotbar HUD, inventory replacement, scroll wheel, shader overlay, mod frame hooks. */
public final class BuiltinUI {
    private BuiltinUI() {}

    public static final Items.Stack[] HOTBAR = new Items.Stack[9];
    public static final Items.Stack[] STORAGE = new Items.Stack[27];
    public static int selected;
    private static final boolean[] held = new boolean[9];
    private static Field activeGuis;
    private static boolean replacingInventory, initialized, dropWas, deathHandled;
    private static Object lastRenderer;
    private static long selectionChangedNs, lastNs;
    private static GLFWScrollCallback scroll;
    private static final SoundSystem SOUND = new SoundSystem();

    public static SoundSystem sound() { return SOUND; }
    public static Items.Stack held() { return HOTBAR[selected]; }

    public static void install(ModContext context, Object rawEngine) throws Exception {
        VoxelEngine engine = (VoxelEngine) rawEngine;
        activeGuis = UIManager.class.getDeclaredField("activeGUIs");
        activeGuis.setAccessible(true);
        Field frameHook = VoxelEngine.class.getDeclaredField("frameHook");
        frameHook.setAccessible(true);
        Runnable original = (Runnable) frameHook.get(engine);
        engine.setFrameHook(() -> {
            if (original != null) original.run();
            try { frame(engine); } catch (Throwable error) { context.log("UI hook error: " + error); error.printStackTrace(); }
            for (ClientMod mod : Agent.mods()) try { mod.onFrame(context); } catch (Throwable error) { context.log("mod frame error: " + error); }
        });
        SOUND.install(engine);
        BlockType[] defaults = {BlockType.STONE, BlockType.DIRT, BlockType.OAK_LOG, BlockType.TORCH};
        for (int i = 0; i < defaults.length; i++) HOTBAR[i] = Items.Stack.of(defaults[i], 64);
        HOTBAR[4] = Items.Stack.of(Items.byId("stone_pickaxe"));
        HOTBAR[5] = Items.Stack.of(Items.byId("stone_shovel"));
        HOTBAR[6] = Items.Stack.of(Items.byId("stone_axe"));
        HOTBAR[7] = Items.Stack.of(Items.byId("stone_sword"));
        HOTBAR[8] = Items.Stack.of(Items.byId("apple"), 8);
        STORAGE[0] = Items.Stack.of(Items.byId("crafting_table"));
        STORAGE[1] = Items.Stack.of(Items.byId("chest"));
        STORAGE[2] = Items.Stack.of(Items.byId("bed"));
        context.log("UI, hotbar, inventory, F3, hand and game modes enabled (" + GameMode.label() + ").");
    }

    /** GLFW calls must happen on the main thread, so the first frame finishes the setup. */
    private static void init(VoxelEngine engine) {
        initialized = true;
        GLFW.glfwSwapInterval(Settings.vsync ? 1 : 0);
        applyRenderDistance();
        scroll = GLFWScrollCallback.create((window, dx, dy) -> {
            if (dy == 0) return;
            GUI top = topGui();
            if (top instanceof ChatScreen) { Chat.scroll = Math.max(0, Chat.scroll + (dy > 0 ? 3 : -3)); return; }
            if (top instanceof Screens.McInventory) { ((Screens.McInventory) top).onScroll(dy); return; }
            if (!engine.isWorldActive() || engine.getCamera() == null || UIManager.get().hasActiveGUIs()) return;
            select(engine, ((selected - (int) Math.signum(dy)) % 9 + 9) % 9);
        });
        GLFW.glfwSetScrollCallback(UIManager.get().getWindow(), scroll);
    }

    @SuppressWarnings("unchecked")
    static GUI topGui() {
        try {
            List<GUI> open = (List<GUI>) activeGuis.get(UIManager.get());
            return open.isEmpty() ? null : open.get(open.size() - 1);
        } catch (Exception e) { return null; }
    }

    private static void frame(VoxelEngine engine) throws Exception {
        if (!initialized) init(engine);
        if (Tiles.prepareTextures() && engine.getRenderer() != null) engine.getRenderer().clearAllMeshes();
        syncAspect(engine);
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        SOUND.tick(engine);
        GameMode.tick(engine);
        Survival.tick(engine, dt);
        Mining.tick(engine, dt);
        Drops.tick(engine, dt);
        Tiles.tick(engine, dt);
        Net.tick(engine);
        Mobs.tick(engine, dt);
        HandRenderer.tick(engine);
        GUI top = topGui();
        if (top instanceof PlayerInventory) {
            if (!replacingInventory) {
                replacingInventory = true;
                UIManager.get().closeTopGUI();
                UIManager.get().openGUI(new Screens.McInventory(engine));
            }
            return;
        }
        if (top == null) replacingInventory = false;
        Object renderer = engine.getRenderer();
        if (renderer != lastRenderer) {
            lastRenderer = renderer;
            if (renderer != null) { Skins.onWorldEnter(); Survival.reset(); SOUND.rescan(); Tiles.load(engine); Net.onWorldEnter(engine); if (Settings.musicVolume > 0) Music.start(); }
            else { Drops.clear(); Mobs.clear(); Music.stop(); Tiles.unload(); Net.onWorldExit(); }
        }
        if (!engine.isWorldActive() || engine.getCamera() == null) return;
        Camera camera = engine.getCamera();
        // Death (local or reported by the server): show the death screen once.
        if (engine.getLocalPlayer() != null && engine.getLocalPlayer().dead) {
            if (!(top instanceof DeathScreen)) {
                if (!deathHandled) {
                    deathHandled = true;
                    // deaths reported by the server did not go through Survival.die(): drop the inventory here
                    if (!Survival.diedLocally) { DeathScreen.cause = "generic"; Survival.dropEverything(engine); }
                }
                UIManager.get().closeAllGUIs();
                UIManager.get().openGUI(new DeathScreen(engine));
            }
            return;
        }
        deathHandled = false;
        Survival.diedLocally = false;
        // Keep the engine's hand in sync with the hotbar (middle click is handled by PickBlock).
        AbstractBlock inHand = camera.getBlockInHand();
        BlockType expected = HOTBAR[selected] != null && HOTBAR[selected].isBlock() ? HOTBAR[selected].block : BlockType.AIR;
        if (Tiles.pendingPlace == null && (inHand == null || inHand.getType() != expected)) select(engine, selected);
        if (UIManager.get().hasActiveGUIs()) return;
        long window = UIManager.get().getWindow();
        for (int i = 0; i < 9; i++) {
            boolean down = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_1 + i) == GLFW.GLFW_PRESS;
            if (down && !held[i]) select(engine, i);
            held[i] = down;
        }
        // Q drops one item, Ctrl+Q the whole stack (Minecraft).
        boolean drop = Keybinds.down(window, "drop");
        if (drop && !dropWas && HOTBAR[selected] != null) {
            Items.Stack s = HOTBAR[selected];
            boolean all = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
            Items.Stack thrown;
            if (all || s.count <= 1) { thrown = s; HOTBAR[selected] = null; }
            else { thrown = s.isBlock() ? Items.Stack.of(s.block, 1) : Items.Stack.of(s.item, 1); s.count--; }
            Drops.throwFromPlayer(engine, thrown);
            select(engine, selected);
        }
        dropWas = drop;
        int[] size = camera.getAspect();
        GLUIRenderer.setup2DRendering(size[0], size[1]);
        try {
            renderShaderOverlay(size[0], size[1]);
            drawHotbar(size[0], size[1]);
            if (!Settings.isCreative()) Mining.renderHud(size[0], size[1]);
            Tiles.renderHud(size[0], size[1]);
            if (Net.listKeyDown() || Boolean.getBoolean("ml.tablist")) Net.renderPlayerList(size[0], size[1]);
        } finally { GLUIRenderer.restore3DRendering(); }
    }

    /**
     * The engine creates the camera at 1280x720 and only updates it from the resize callback, so a
     * window that was already maximized draws the HUD/menus stretched while clicks arrive in real
     * pixels (buttons "stop working" on big windows). Keep the camera at the framebuffer size.
     */
    private static void syncAspect(VoxelEngine engine) {
        Camera cam = engine.getCamera();
        if (cam == null) return;
        int[] w = new int[1], h = new int[1];
        GLFW.glfwGetFramebufferSize(UIManager.get().getWindow(), w, h);
        int[] a = cam.getAspect();
        if (w[0] > 0 && h[0] > 0 && (a[0] != w[0] || a[1] != h[0])) {
            cam.setAspect(w[0], h[0]);
            org.lwjgl.opengl.GL11.glViewport(0, 0, w[0], h[0]);
        }
    }

    public static void select(VoxelEngine engine, int slot) {
        if (slot != selected) selectionChangedNs = System.nanoTime();
        selected = slot;
        if (engine == null || engine.getCamera() == null) return;
        Items.Stack s = HOTBAR[slot];
        BlockType type = s != null && s.isBlock() ? s.block : BlockType.AIR;
        engine.getCamera().setBlockInHand(new AbstractBlock(type));
    }

    private static void drawHotbar(int w, int h) {
        float s = 40, gap = 4, total = 9 * s + 8 * gap, x0 = (w - total) / 2f, y = h - s - 8;
        GLUIRenderer.drawPanel(x0 - 4, y - 4, total + 8, s + 8, 0f, 0f, 0f, 0.35f);
        for (int i = 0; i < 9; i++) {
            float sx = x0 + i * (s + gap);
            Mc.hudSlot(sx, y, s, i == selected);
            if (HOTBAR[i] != null) HOTBAR[i].drawIcon(sx + s / 2, y + s / 2, s * 0.62f);
        }
        float age = (System.nanoTime() - selectionChangedNs) / 1e9f;
        if (age < 2.2f && HOTBAR[selected] != null) {
            float alpha = age < 1.6f ? 1f : Math.max(0f, 1f - (age - 1.6f) / 0.6f);
            String name = HOTBAR[selected].name();
            Mc.text(name, (w - GLUIRenderer.getTextWidth(name)) / 2f, y - 34, 1f, 1f, 1f, alpha);
        }
        if (GameMode.flying) Mc.text(Lang.t("hud.flying"), w - GLUIRenderer.getTextWidth(Lang.t("hud.flying")) - 10, h - 12, Mc.TEXT_DIM[0], Mc.TEXT_DIM[1], Mc.TEXT_DIM[2], 0.8f);
    }

    private static void renderShaderOverlay(int w, int h) {
        if (Settings.shaderPreset.equals("Vibrant")) GLUIRenderer.drawRect(0, 0, w, h, 0.12f, 0.05f, 0f, 0.035f);
        else if (Settings.shaderPreset.equals("Fog")) GLUIRenderer.drawGradientV(0, 0, w, h, 0.38f, 0.46f, 0.55f, 0.075f, 0.08f, 0.12f, 0.17f, 0.02f);
    }

    static void applyRenderDistance() {
        try {
            VoxelEngine engine = VoxelEngine.getEngine();
            if (engine == null) return;
            Field f = VoxelEngine.class.getDeclaredField("renderDistance");
            f.setAccessible(true);
            f.setInt(engine, Settings.renderDistance);
            if (engine.getCamera() != null) {
                Field c = Camera.class.getDeclaredField("renderDistance");
                c.setAccessible(true);
                c.setInt(engine.getCamera(), Settings.renderDistance);
            }
        } catch (Exception error) { System.err.println("[HappySettings] render distance: " + error); }
    }

    /** Re-decodes every block texture so a resource pack switch shows without restarting. */
    static void reloadTextures() {
        try {
            java.lang.reflect.Method each = BlockType.class.getDeclaredMethod("forEachTexture", java.util.function.Consumer.class);
            each.setAccessible(true);
            for (BlockType b : BlockType.values()) {
                each.invoke(b, (java.util.function.Consumer<net.openvoxel.engine.rendering.Texture>) t -> { t.cleanup(); t.reload(); });
            }
            VoxelEngine e = VoxelEngine.getEngine();
            if (e != null && e.getRenderer() != null) e.getRenderer().clearAllMeshes();
        } catch (Exception error) { System.err.println("[ModLoader] texture reload: " + error); }
    }
}
