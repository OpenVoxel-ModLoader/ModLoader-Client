package com.happyuky7.openvoxelmoding.loader;

import net.openvoxel.client.ClientMain;
import net.openvoxel.client.ServerSelectScreen;
import net.openvoxel.client.SessionInfo;
import net.openvoxel.client.SingleplayerScreen;
import net.openvoxel.engine.VoxelEngine;
import net.openvoxel.engine.gui.GUI;
import net.openvoxel.engine.rendering.Camera;
import net.openvoxel.engine.rendering.Texture;
import net.openvoxel.engine.ui.GLUIRenderer;
import net.openvoxel.engine.ui.UIManager;
import net.openvoxel.engine.ui.widgets.*;
import net.openvoxel.engine.world.block.BlockType;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.happyuky7.openvoxelmoding.loader.Mc.McButton;
import com.happyuky7.openvoxelmoding.loader.Mc.McLabel;
import com.happyuky7.openvoxelmoding.loader.Mc.McSlider;
import com.happyuky7.openvoxelmoding.loader.Mc.PasswordField;

/** All Minecraft-styled screens: main/pause menu builders and the loader's own screens. */
final class Screens {
    private Screens() {}

    static String t(String key, Object... args) { return Lang.t(key, args); }
    static String onOff(boolean b) { return t(b ? "gui.on" : "gui.off"); }

    // ---- Main menu (replaces MainMenuScreen.build) --------------------------------------

    /**
     * Main menu in OpenVoxel's own look (animated backdrop, logo, themed buttons) — this is not
     * Minecraft — with the ModLoader entries added under the game's buttons.
     */
    static void buildMainMenu(Screen screen, float w, float h) throws Exception {
        List<Widget> list = Mc.widgetsOf(screen);
        list.clear();
        int iw = (int) w, ih = (int) h;
        float bw = 300, bh = 46, gap = 12, half = (bw - gap) / 2, cx = w / 2f, y = h * 0.40f;
        list.add(new net.openvoxel.engine.ui.widgets.MenuBackdrop(0, 0, w, h));
        Widget logo = gameLogo(w, h * 0.13f);
        if (logo != null) list.add(logo);
        list.add(new Button(cx - bw / 2, y, bw, bh, t("menu.singleplayer"), b -> { UIManager.get().closeAllGUIs(); UIManager.get().openGUI(new SingleplayerScreen(iw, ih)); }));
        list.add(new Button(cx - bw / 2, y + (bh + gap), bw, bh, t("menu.multiplayer"), b -> { UIManager.get().closeAllGUIs(); UIManager.get().openGUI(new ServerSelectScreen(iw, ih)); }));
        float y2 = y + 2 * (bh + gap);
        list.add(new Button(cx - bw / 2, y2, half, bh, t("menu.mods", Agent.getLoadedModNames().size()), b -> UIManager.get().openGUI(new ModsScreen(null, iw, ih))));
        list.add(new Button(cx + gap / 2, y2, half, bh, t("menu.options"), b -> UIManager.get().openGUI(new SettingsScreen(null, iw, ih))));
        float y3 = y2 + bh + gap;
        list.add(new Button(cx - bw / 2, y3, half, bh, t("menu.skin"), b -> UIManager.get().openGUI(new SkinScreen(null, iw, ih))));
        list.add(new Button(cx + gap / 2, y3, half, bh, t("menu.account"), b -> UIManager.get().openGUI(new AccountScreen(null, iw, ih))));
        list.add(new Button(cx - bw / 2, y3 + bh + gap, bw, bh, t("menu.quit"), b -> VoxelEngine.getEngine().stop()));
        float[] dim = net.openvoxel.engine.ui.Theme.TEXT_DIM;
        list.add(new Label(14, h - 24, "OpenVoxel " + GameBuild.id() + " (protocol " + GameBuild.protocol() + ")  ·  " + Agent.NAME + " " + Agent.VERSION).color(dim[0], dim[1], dim[2], dim[3]));
        String site = "open-voxel.net";
        list.add(new Label(w - GLUIRenderer.getTextWidth(site) - 14, h - 24, site).color(dim[0], dim[1], dim[2], dim[3]));
    }

    /** The game's own logo widget (private inner class of MainMenuScreen). */
    private static Widget gameLogo(float w, float y) {
        try {
            Class<?> c = Class.forName("net.openvoxel.client.MainMenuScreen$LogoTitle");
            java.lang.reflect.Constructor<?> k = c.getDeclaredConstructor(float.class, float.class);
            k.setAccessible(true);
            return (Widget) k.newInstance(w, y);
        } catch (Throwable error) { System.err.println("[ModLoader] main menu logo: " + error); return null; }
    }

    static final class MenuBackdrop extends Widget {
        private static final String[] SPLASHES = {"Modded!", "Now with Alex arms!", "Double-tap space to fly!", "&colors!", "Try /chelp", "Steve approved!", "128x128 skins!", "ModLoader!"};
        private final String splash = SPLASHES[(int) (System.nanoTime() / 1_000_000L % SPLASHES.length)];
        MenuBackdrop(float w, float h) { super(0, 0, w, h); }
        @Override protected void renderWidget() {
            Mc.dirt(0, 0, width, height, 0.30f);
            float logoY = height * 0.10f, logoW = Math.min(520f, width * 0.46f), logoH = 90;
            Texture logo = Mc.logo();
            if (logo != null && logo.getWidth() > 0) {
                logoH = logoW * logo.getHeight() / logo.getWidth();
                GLUIRenderer.drawTexture(logo, (width - logoW) / 2f, logoY, logoW, logoH);
            } else {
                GL11.glPushMatrix();
                GL11.glTranslatef(width / 2f, logoY + 60, 0);
                GL11.glScalef(3.2f, 3.2f, 1f);
                Mc.centered("OPEN VOXEL", 0, 0, Mc.TEXT);
                GL11.glPopMatrix();
            }
            float pulse = 1f + 0.08f * (float) Math.abs(Math.sin(System.nanoTime() / 1.5e8));
            GL11.glPushMatrix();
            GL11.glTranslatef(width / 2f + logoW / 2f - 20, logoY + logoH - 10, 0);
            GL11.glRotatef(-20f, 0, 0, 1);
            GL11.glScalef(pulse, pulse, 1f);
            Mc.centered(splash, 0, 0, Mc.YELLOW);
            GL11.glPopMatrix();
        }
    }

    // ---- Pause menu (replaces PauseMenu.build) ------------------------------------------

    static void buildPauseMenu(Screen screen, float w, float h) throws Exception {
        List<Widget> list = Mc.widgetsOf(screen);
        list.clear();
        Camera cam = Mc.cameraIfInWorld();
        int iw = (int) w, ih = (int) h;
        float bw = 300, bh = 40, gap = 8, half = (bw - gap) / 2, cx = w / 2f, y = h * 0.30f;
        list.add(new Panel(0, 0, w, h, 0, 0, 0, 0.55f));
        list.add(new McLabel(0, y - 24, t("pause.title")).centerIn(w));
        list.add(new McButton(cx - bw / 2, y, bw, bh, t("pause.back"), b -> screen.close()));
        list.add(new McButton(cx - bw / 2, y + (bh + gap), half, bh, t("pause.mods"), b -> UIManager.get().openGUI(new ModsScreen(cam, iw, ih))));
        list.add(new McButton(cx + gap / 2, y + (bh + gap), half, bh, t("menu.options"), b -> UIManager.get().openGUI(new SettingsScreen(cam, iw, ih))));
        list.add(new McButton(cx - bw / 2, y + 2 * (bh + gap), bw, bh, t("pause.gamemode", GameMode.label()), b -> {
            GameMode.set(Settings.isCreative() ? "survival" : "creative");
            b.setText(t("pause.gamemode", GameMode.label()));
        }));
        list.add(new McButton(cx - bw / 2, y + 3 * (bh + gap), half, bh, t("menu.skin"), b -> UIManager.get().openGUI(new SkinScreen(cam, iw, ih))));
        list.add(new McButton(cx + gap / 2, y + 3 * (bh + gap), half, bh, t("menu.account"), b -> UIManager.get().openGUI(new AccountScreen(cam, iw, ih))));
        list.add(new McButton(cx - bw / 2, y + 4 * (bh + gap) + 12, bw, bh, t("pause.quit"), b -> {
            Runnable handler = VoxelEngine.getEngine().getExitToMenuHandler();
            if (handler != null) handler.run(); else UIManager.get().closeAllGUIs();
        }));
    }

    // ---- Base screen -------------------------------------------------------------------

    abstract static class Base extends Screen {
        final Camera cam;
        final String title;
        Base(Camera cam, int w, int h, String title) { super(w, h); this.cam = cam; this.title = title; }
        @Override public void onOpen() { if (cam != null && cam.getInputHandler() != null) cam.getInputHandler().paused = true; }
        /** Only resume mouse look when no other menu (e.g. the pause menu underneath) is still open. */
        @Override public void onClose() { if (cam != null && cam.getInputHandler() != null && !UIManager.get().hasActiveGUIs()) cam.getInputHandler().paused = false; }
        /** Escape always closes just this screen (even with a focused text field), returning to the one below. */
        @Override public void onKeyPress(int key) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { close(); return; }
            super.onKeyPress(key);
        }
        void frame(float w, float h) {
            add(new Backdrop(w, h, cam != null));
            add(new McLabel(0, 44, title).centerIn(w));
            add(new McButton(w / 2 - 100, h - 60, 200, 40, t("gui.done"), b -> close()));
        }
        Label dim(float x, float y, String text) { return add(new McLabel(x, y, text).color(Mc.TEXT_DIM)); }
        void status(String s) {
            GLUIRenderer.setup2DRendering(windowWidth, windowHeight);
            Mc.centered(s, windowWidth / 2f, windowHeight - 76, Mc.YELLOW);
            GLUIRenderer.restore3DRendering();
        }
    }

    static final class Backdrop extends Widget {
        private final boolean overlay;
        Backdrop(float w, float h, boolean overlay) { super(0, 0, w, h); this.overlay = overlay; }
        @Override protected void renderWidget() {
            if (overlay) GLUIRenderer.drawPanel(0, 0, width, height, 0, 0, 0, 0.62f);
            else Mc.dirt(0, 0, width, height, 0.28f);
        }
    }

    // ---- Mods --------------------------------------------------------------------------

    static final class ModsScreen extends Base {
        ModsScreen(Camera cam, int w, int h) { super(cam, w, h, t("mods.title")); }
        @Override protected void build(float w, float h) {
            frame(w, h);
            float px = w / 2 - 260, py = 80, pw = 520, ph = h - 160;
            add(new Panel(px, py, pw, ph, 0, 0, 0, 0.45f).withBorder(1, 0.35f, 0.35f, 0.35f, 1));
            add(new McLabel(px + 16, py + 28, t("mods.loader")).color(Mc.YELLOW));
            int row = 0;
            for (String name : Agent.getLoadedModNames()) add(new McLabel(px + 16, py + 60 + row++ * 24, "- " + name));
            if (row == 0) dim(px + 16, py + 60, t("mods.none"));
            dim(px + 16, py + ph - 20, t("mods.hint"));
        }
    }

    // ---- Controls ----------------------------------------------------------------------

    /** Minecraft-style key binding list: click a key, press the new one (Esc cancels). */
    static final class ControlsScreen extends Base {
        private String waiting;
        private final java.util.Map<String, McButton> buttons = new java.util.HashMap<>();
        ControlsScreen(Camera cam, int w, int h) { super(cam, w, h, t("controls.title")); }
        private String label(String action) { return t("key." + action) + ": " + (action.equals(waiting) ? "> ? <" : Keybinds.name(Keybinds.key(action))); }
        @Override protected void build(float w, float h) {
            frame(w, h);
            float lx = w / 2 - 205, rx = w / 2 + 5, bw = 200, bh = 34, y = 84, step = 40;
            buttons.clear();
            for (int i = 0; i < Keybinds.ACTIONS.length; i++) {
                String action = Keybinds.ACTIONS[i];
                float x = i % 2 == 0 ? lx : rx, yy = y + (i / 2) * step;
                buttons.put(action, add(new McButton(x, yy, bw, bh, label(action), b -> { waiting = action; refresh(); })));
            }
            float ry = y + ((Keybinds.ACTIONS.length + 1) / 2) * step + 6;
            add(new McButton(w / 2 - 100, ry, 200, bh, t("controls.reset"), b -> { Keybinds.reset(); Settings.save(); waiting = null; refresh(); }));
            dim(w / 2 - 205, ry + 56, t("controls.hint1"));
            dim(w / 2 - 205, ry + 76, t("controls.hint2"));
        }
        private void refresh() { for (java.util.Map.Entry<String, McButton> e : buttons.entrySet()) e.getValue().setText(label(e.getKey())); }
        @Override public void onKeyPress(int key) {
            if (waiting != null) {
                if (key != GLFW.GLFW_KEY_ESCAPE) { Keybinds.bind(waiting, key); Settings.save(); }
                waiting = null;
                refresh();
                return;
            }
            super.onKeyPress(key);
        }
    }

    // ---- Options -----------------------------------------------------------------------

    static final class SettingsScreen extends Base {
        SettingsScreen(Camera cam, int w, int h) { super(cam, w, h, t("options.title")); }
        @Override protected void build(float w, float h) {
            frame(w, h);
            float lx = w / 2 - 205, rx = w / 2 + 5, bw = 200, bh = 36, y = 84, step = 44;
            add(new McButton(lx, y, bw, bh, t("options.sound", onOff(Settings.sounds)), b -> { Settings.sounds = !Settings.sounds; b.setText(t("options.sound", onOff(Settings.sounds))); }));
            add(new McSlider(rx, y, bw, bh, Settings.volume, v -> t("options.volume", Math.round(v * 100)), v -> Settings.volume = v));
            y += step;
            add(new McButton(lx, y, bw, bh, t("options.vsync", onOff(Settings.vsync)), b -> { Settings.vsync = !Settings.vsync; GLFW.glfwSwapInterval(Settings.vsync ? 1 : 0); b.setText(t("options.vsync", onOff(Settings.vsync))); }));
            add(new McSlider(rx, y, bw, bh, (Settings.renderDistance - 4) / 28f, v -> t("options.render", 4 + Math.round(v * 14) * 2), v -> { Settings.renderDistance = 4 + Math.round(v * 14) * 2; BuiltinUI.applyRenderDistance(); }));
            y += step;
            add(new McButton(lx, y, bw, bh, t("pause.gamemode", GameMode.label()), b -> { GameMode.set(Settings.isCreative() ? "survival" : "creative"); b.setText(t("pause.gamemode", GameMode.label())); }));
            add(new McSlider(rx, y, bw, bh, (Settings.flySpeed - 0.25f) / 3.75f, v -> t("options.fly", 0.25f + v * 3.75f), v -> Settings.flySpeed = 0.25f + v * 3.75f));
            y += step;
            add(new McButton(lx, y, bw, bh, t("options.debug", t(Settings.debugEnabled ? "options.debug.always" : "options.debug.f3")), b -> { Settings.debugEnabled = !Settings.debugEnabled; b.setText(t("options.debug", t(Settings.debugEnabled ? "options.debug.always" : "options.debug.f3"))); }));
            add(new McButton(rx, y, bw, bh, t("options.shaders", Settings.shaderPreset), b -> {
                Settings.shaderPreset = Settings.shaderPreset.equals("Off") ? "Vibrant" : Settings.shaderPreset.equals("Vibrant") ? "Fog" : "Off";
                b.setText(t("options.shaders", Settings.shaderPreset));
            }));
            y += step;
            add(new McButton(lx, y, bw, bh, t("options.skinmodel", modelLabel()), b -> {
                Settings.skinModel = Settings.skinModel.equals("auto") ? "steve" : Settings.skinModel.equals("steve") ? "alex" : "auto";
                b.setText(t("options.skinmodel", modelLabel()));
                Skins.reloadLocal();
            }));
            Map<String, String> langs = Lang.available();
            add(new McButton(rx, y, bw, bh, t("options.language", langs.getOrDefault(Settings.language, Settings.language)), b -> {
                List<String> codes = new ArrayList<>(langs.keySet());
                int i = codes.indexOf(Settings.language);
                Settings.language = codes.get((i + 1) % codes.size());
                Lang.load(Settings.language);
                Settings.save();
                UIManager.get().closeTopGUI();
                UIManager.get().openGUI(new SettingsScreen(cam, windowWidth, windowHeight));
            }));
            y += step;
            add(new McButton(lx, y, bw, bh, t("options.cape", onOff(Settings.cape)), b -> { Settings.cape = !Settings.cape; b.setText(t("options.cape", onOff(Settings.cape))); }));
            add(new McButton(rx, y, bw, bh, t("options.elytra", onOff(Settings.elytra)), b -> { Settings.elytra = !Settings.elytra; b.setText(t("options.elytra", onOff(Settings.elytra))); }));
            y += step;
            add(new McSlider(lx, y, bw, bh, Settings.musicVolume, v -> t("options.music", Math.round(v * 100)), v -> { Settings.musicVolume = v; if (v > 0) Music.start(); }));
            add(new McButton(rx, y, bw, bh, t("options.fog", onOff(Settings.fog)), b -> { Settings.fog = !Settings.fog; b.setText(t("options.fog", onOff(Settings.fog))); }));
            y += step;
            add(new McButton(lx, y, bw, bh, t("options.textures"), b -> UIManager.get().openGUI(new TexturesScreen(cam, windowWidth, windowHeight))));
            add(new McButton(rx, y, bw, bh, t("options.controls"), b -> UIManager.get().openGUI(new ControlsScreen(cam, windowWidth, windowHeight))));
            y += step;
            dim(lx, y + 16, t("options.saved"));
        }
        static String modelLabel() { return t("skinmodel." + Settings.skinModel); }
        @Override public void onClose() { Settings.save(); super.onClose(); }
    }

    // ---- Resource packs ----------------------------------------------------------------

    static final class TexturesScreen extends Base {
        TexturesScreen(Camera cam, int w, int h) { super(cam, w, h, t("textures.title")); }
        @Override protected void build(float w, float h) {
            frame(w, h);
            float bw = 320, bh = 36, y = 90, cx = w / 2;
            dim(cx - 300, y - 10, t("textures.folder"));
            add(new McLabel(cx - 300, y + 12, t("textures.active", Settings.resourcePack.isEmpty() ? t("textures.default") : Settings.resourcePack)).color(Mc.YELLOW));
            y += 36;
            add(new McButton(cx - bw / 2, y, bw, bh, t("textures.default"), b -> apply("")));
            y += bh + 6;
            List<String> packs = new ArrayList<>();
            Path dir = Agent.instanceDir().resolve("resourcepacks");
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) { for (Path p : files) if (Files.isDirectory(p)) packs.add(p.getFileName().toString()); }
            catch (Exception ignored) { }
            if (packs.isEmpty()) dim(cx - 300, y + 20, t("textures.none"));
            for (String pack : packs) {
                add(new McButton(cx - bw / 2, y, bw, bh, (pack.equals(Settings.resourcePack) ? "> " : "") + pack, b -> apply(pack)));
                y += bh + 6;
            }
        }
        private void apply(String pack) {
            Settings.resourcePack = pack;
            Settings.save();
            if (VoxelEngine.getEngine() != null && VoxelEngine.getEngine().isWorldActive()) ClientMain.runOnMainThread(BuiltinUI::reloadTextures);
            UIManager.get().closeTopGUI();
            UIManager.get().openGUI(new TexturesScreen(cam, windowWidth, windowHeight));
        }
    }

    // ---- Skin --------------------------------------------------------------------------

    /** Draws the front of a 64x64-layout skin (any multiple) at {@code scale} px per skin pixel. */
    static void drawSkinFront(Texture tex, float x, float y, float scale, boolean slim) {
        if (tex == null) return;
        float tw = tex.getWidth(), th = tex.getHeight(), s = tw / 64f;
        float aw = slim ? 3 : 4;
        float[][] parts = {
            {4, 0, 8, 8, 8, 8}, {4, 8, 20, 20, 8, 12},
            {4 - aw, 8, 44, 20, aw, 12}, {12, 8, 36, 52, aw, 12},
            {4, 20, 4, 20, 4, 12}, {8, 20, 20, 52, 4, 12},
            {4, 0, 40, 8, 8, 8}, {4, 8, 20, 36, 8, 12},
            {4 - aw, 8, 44, 36, aw, 12}, {12, 8, 52, 52, aw, 12},
            {4, 20, 4, 36, 4, 12}, {8, 20, 4, 52, 4, 12}
        };
        for (float[] p : parts) {
            float x1 = x + p[0] * scale, y1 = y + p[1] * scale, x2 = x1 + p[4] * scale, y2 = y1 + p[5] * scale;
            float u0 = p[2] * s / tw, u1 = (p[2] + p[4]) * s / tw, v0 = 1f - p[3] * s / th, v1 = 1f - (p[3] + p[5]) * s / th;
            GLUIRenderer.drawTexturedQuad(tex, x1, y1, x2, y1, x2, y2, x1, y2, u0, v0, u1, v0, u1, v1, u0, v1, 1f);
        }
    }

    static final class SkinPreview extends Widget {
        private Texture tex; private boolean slim, tried;
        SkinPreview(float x, float y, float w, float h) { super(x, y, w, h); }
        void refresh() { if (tex != null) tex.cleanup(); tex = null; tried = false; }
        @Override protected void renderWidget() {
            if (!tried) {
                tried = true;
                try { BufferedImage img = Skins.currentImage(); if (img != null) { tex = new Texture(Skins.encode(img)); slim = Skins.slim(img); } }
                catch (Exception error) { tex = null; }
            }
            GLUIRenderer.drawPanel(x, y, width, height, 0, 0, 0, 0.5f);
            GLUIRenderer.drawBorder(x, y, width, height, 1, 0.35f, 0.35f, 0.35f, 1);
            float scale = Math.min(width / 18f, height / 34f);
            drawSkinFront(tex, x + (width - 16 * scale) / 2f, y + (height - 32 * scale) / 2f, scale, slim);
        }
        void dispose() { if (tex != null) tex.cleanup(); tex = null; }
    }

    static final class SkinScreen extends Base {
        private SkinPreview preview;
        SkinScreen(Camera cam, int w, int h) { super(cam, w, h, t("skin.title")); }
        @Override protected void build(float w, float h) {
            frame(w, h);
            float px = w / 2 - 250, py = 80;
            preview = add(new SkinPreview(px, py, 160, 300));
            float bx = px + 190, bw = 300, bh = 36;
            add(new McLabel(bx, py + 16, t("skin.formats")).color(Mc.YELLOW));
            dim(bx, py + 40, t("skin.file"));
            dim(bx, py + 60, t("skin.layers"));
            add(new McButton(bx, py + 80, bw, bh, t("skin.model", SettingsScreen.modelLabel()), b -> {
                Settings.skinModel = Settings.skinModel.equals("auto") ? "steve" : Settings.skinModel.equals("steve") ? "alex" : "auto";
                Settings.save();
                b.setText(t("skin.model", SettingsScreen.modelLabel()));
                Skins.reloadLocal();
                preview.refresh();
            }));
            add(new McButton(bx, py + 124, bw, bh, t("skin.reload"), b -> { Skins.reloadLocal(); Capes.reload(); preview.refresh(); }));
            add(new McButton(bx, py + 168, bw, bh, t("skin.upload"), b -> async(Skins::uploadSkin)));
            add(new McButton(bx, py + 212, bw, bh, t("skin.uploadmodel"), b -> async(Skins::uploadModel)));
            dim(bx, py + 268, t("skin.hint1"));
            dim(bx, py + 288, t("skin.hint2"));
            dim(bx, py + 308, t("skin.cape"));
        }
        @Override public void render() { super.render(); status(Skins.status); }
        @Override public void onClose() { if (preview != null) preview.dispose(); super.onClose(); }
    }

    // ---- Account -----------------------------------------------------------------------

    static final class AccountScreen extends Base {
        private static volatile String status = "";
        AccountScreen(Camera cam, int w, int h) { super(cam, w, h, t("account.title")); }
        @Override protected void build(float w, float h) {
            frame(w, h);
            float cx = w / 2, y = 90;
            SessionInfo s = ClientMain.getSession();
            add(new McLabel(cx - 200, y, s != null && s.isLoggedIn() ? t("account.session", s.username, s.uuid) : t("account.none")).color(Mc.YELLOW));
            dim(cx - 200, y + 22, t("account.server", s != null ? s.authServer : "https://open-voxel.net"));
            TextField user = add(new TextField(cx - 200, y + 50, 400, 34, t("account.username")));
            if (s != null && s.username != null) user.setText(s.username);
            PasswordField pass = add(new PasswordField(cx - 200, y + 94, 400, 34, t("account.password")));
            add(new McButton(cx - 200, y + 140, 196, 36, t("account.login"), b -> async(() -> login(user.getText(), pass.getText()))));
            add(new McButton(cx + 4, y + 140, 196, 36, t("account.check"), b -> async(AccountScreen::validate)));
            dim(cx - 200, y + 200, t("account.hint1"));
            dim(cx - 200, y + 220, t("account.hint2"));
        }
        @Override public void render() { super.render(); status(status); }

        static void validate() {
            SessionInfo s = ClientMain.getSession();
            if (s == null || !s.isLoggedIn()) { status = "No session token. Log in below."; return; }
            try {
                String body = Skins.http(base(s) + "/auth/validate", "GET", null, null, s.token);
                status = "Session valid: " + Skins.extract(body, "username", s.username);
            } catch (Exception error) { status = "Session invalid: " + error.getMessage(); }
        }

        static void login(String username, String password) {
            if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) { status = "Enter username and password"; return; }
            SessionInfo s = ClientMain.getSession();
            try {
                String json = "{\"username\":\"" + esc(username.trim()) + "\",\"password\":\"" + esc(password) + "\"}";
                String body = Skins.http(base(s) + "/auth/login", "POST", "application/json", json.getBytes(StandardCharsets.UTF_8), null);
                String token = Skins.extract(body, "token", null), uuid = Skins.extract(body, "uuid", null), name = Skins.extract(body, "username", username.trim());
                if (token == null) { status = "Login failed: no token in response"; return; }
                if (s == null) { status = "Login ok but no session object to update"; return; }
                set(s, "token", token); set(s, "uuid", uuid); set(s, "username", name);
                status = "Logged in as " + name;
                ClientMain.runOnMainThread(() -> { if (VoxelEngine.getEngine().isWorldActive()) net.openvoxel.client.net.AppearanceLoader.loadAsync(); });
            } catch (Exception error) { status = "Login failed: " + error.getMessage(); }
        }

        private static String base(SessionInfo s) {
            String b = s != null && s.authServer != null ? s.authServer : "https://open-voxel.net";
            return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
        }

        private static void set(SessionInfo s, String field, String value) throws Exception {
            Field f = SessionInfo.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(s, value);
        }

        private static String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
    }

    // ---- Inventory ---------------------------------------------------------------------

    static final class McInventory extends GUI implements RecipeBook.Host {
        private final VoxelEngine engine;
        private final List<List<Items.Stack>> palettes = new ArrayList<>();
        private final List<Items.Stack> tabIcons = new ArrayList<>();
        private static final int TAB_INVENTORY = 5, TABS = 6;
        private int tab, page;
        private Items.Stack carried;
        /** Survival 2x2 crafting grid + result, like the vanilla inventory. */
        private final Items.Stack[] craft = new Items.Stack[4];
        private Items.Stack craftResult;
        private final RecipeBook book = new RecipeBook(this);
        private int width, height;
        private Texture skinTex; private boolean skinSlim, skinTried;
        private static final float SLOT = 40, GAP = 4, TAB_W = 44, TAB_H = 30, TAB_STEP = 48;
        private static final int COLS = 9;

        McInventory(VoxelEngine engine) {
            this.engine = engine;
            List<Items.Stack> blocks = new ArrayList<>(), tools = new ArrayList<>(), combat = new ArrayList<>(), food = new ArrayList<>(), materials = new ArrayList<>();
            for (BlockType b : BlockType.values()) if (b != BlockType.AIR && !b.hiddenFromInventory()) blocks.add(Items.Stack.of(b));
            for (Items.Item i : Items.ofKind(Items.Kind.PICKAXE, Items.Kind.SHOVEL, Items.Kind.AXE)) tools.add(Items.Stack.of(i));
            for (Items.Item i : Items.ofKind(Items.Kind.SWORD, Items.Kind.HELMET, Items.Kind.CHESTPLATE, Items.Kind.LEGGINGS, Items.Kind.BOOTS)) combat.add(Items.Stack.of(i));
            for (Items.Item i : Items.ofKind(Items.Kind.FOOD)) food.add(Items.Stack.of(i));
            for (Items.Item i : Items.ofKind(Items.Kind.MATERIAL, Items.Kind.TILE)) materials.add(Items.Stack.of(i));
            palettes.add(blocks); palettes.add(tools); palettes.add(combat); palettes.add(food); palettes.add(materials);
            tabIcons.add(Items.Stack.of(BlockType.GRASS)); tabIcons.add(Items.Stack.of(Items.byId("iron_pickaxe"))); tabIcons.add(Items.Stack.of(Items.byId("iron_sword")));
            tabIcons.add(Items.Stack.of(Items.byId("apple"))); tabIcons.add(Items.Stack.of(Items.byId("iron_ingot"))); tabIcons.add(Items.Stack.of(Items.byId("chest")));
        }

        private boolean creative() { return Settings.isCreative(); }
        private boolean paletteTab() { return creative() && tab < TAB_INVENTORY; }
        private List<Items.Stack> palette() { return palettes.get(Math.min(tab, palettes.size() - 1)); }
        private String tabName(int i) { return t("inventory.tab." + new String[]{"blocks", "tools", "combat", "food", "materials", "inventory"}[i]); }
        private boolean showCraft() { return !creative(); }
        private int gridRows() { return paletteTab() ? 4 : 3; }
        private float bookShift() { return book.open ? (RecipeBook.W + 8) / 2f : 0; }
        private float panelW() { return 56 + COLS * (SLOT + GAP) - GAP + 32; }
        private float panelH() { return 150 + (creative() ? 36 : 0) + gridRows() * (SLOT + GAP) + 16 + SLOT + 24; }
        private float px() { return (width - panelW()) / 2f + bookShift(); }
        private float py() { return (height - panelH()) / 2f; }
        private float gridX() { return px() + 16 + 56; }
        private float gridY() { return py() + 150 + (creative() ? 36 : 0); }
        private float hotbarY() { return gridY() + gridRows() * (SLOT + GAP) + 12; }
        private float armorX() { return px() + 16; }
        private float armorY(int i) { return py() + 40 + i * (SLOT + GAP); }
        private float tabY() { return py() + 150; }
        private int perPage() { return gridRows() * COLS; }

        void onScroll(double dy) {
            if (!paletteTab()) return;
            int pages = (palette().size() + perPage() - 1) / perPage();
            page = Math.max(0, Math.min(pages - 1, page - (int) Math.signum(dy)));
        }

        @Override public void onOpen() {
            int[] s = Mc.windowSize(); width = s[0]; height = s[1];
            if (engine.getCamera() != null && engine.getCamera().getInputHandler() != null) engine.getCamera().getInputHandler().paused = true;
        }
        @Override public void onTick(long ticks) { int[] s = Mc.windowSize(); width = s[0]; height = s[1]; }

        private Items.Stack gridStack(int i) {
            if (paletteTab()) { int idx = page * perPage() + i; return idx < palette().size() ? palette().get(idx) : null; }
            return i < BuiltinUI.STORAGE.length ? BuiltinUI.STORAGE[i] : null;
        }

        // RecipeBook.Host (survival 2x2 grid)
        @Override public int gridSize() { return 2; }
        @Override public Items.Stack[] grid() { return craft; }
        @Override public void gridChanged() { recomputeCraft(); }

        @Override public void render() {
            GLUIRenderer.setup2DRendering(width, height);
            try {
                float px = px(), py = py(), pw = panelW(), ph = panelH();
                GLUIRenderer.drawPanel(0, 0, width, height, 0, 0, 0, 0.6f);
                float[] mouse = Mc.mouse();
                if (showCraft()) { book.layout(px, py, ph); book.render(mouse); }
                Mc.window(px, py, pw, ph);
                float[] d = Mc.TEXT_DARK;
                GLUIRenderer.drawText(t(creative() ? "inventory.creative" : "inventory.title"), px + 16, py + 26, d[0], d[1], d[2], 1f);
                // armor slots
                for (int i = 0; i < 4; i++) {
                    float sx = armorX(), sy = armorY(i);
                    Mc.slot(sx, sy, SLOT, GLUIRenderer.isPointInRect(mouse[0], mouse[1], sx, sy, SLOT, SLOT));
                    Items.Stack a = Survival.ARMOR[i];
                    if (a != null) a.drawIcon(sx + SLOT / 2, sy + SLOT / 2, SLOT * 0.62f);
                    else GLUIRenderer.drawPanel(sx + 12, sy + 12, SLOT - 24, SLOT - 24, 0.45f, 0.45f, 0.45f, 1f);
                }
                // character
                float cx = armorX() + SLOT + 12, cy = py + 40, cw = 110, ch = 172;
                GLUIRenderer.drawPanel(cx, cy, cw, ch, 0, 0, 0, 1f);
                if (!skinTried) { skinTried = true; try { BufferedImage img = Skins.currentImage(); if (img != null) { skinTex = new Texture(Skins.encode(img)); skinSlim = Skins.slim(img); } } catch (Exception ignored) { } }
                drawSkinFront(skinTex, cx + (cw - 16 * 4.5f) / 2f, cy + (ch - 32 * 4.5f) / 2f, 4.5f, skinSlim);
                float ix = cx + cw + 16;
                GLUIRenderer.drawText(t("inventory.mode", GameMode.label()), ix, cy + 18, d[0], d[1], d[2], 1f);
                GLUIRenderer.drawText(t(creative() ? "inventory.hint.creative" : "inventory.hint.survival"), ix, cy + 42, d[0], d[1], d[2], 1f);
                GLUIRenderer.drawText(t("inventory.hint.clear"), ix, cy + 66, d[0], d[1], d[2], 1f);
                if (!creative()) GLUIRenderer.drawText(Lang.t("tip.armor", Survival.armorPoints()) + "   " + Lang.t("tip.food", Math.round(Survival.hunger)), ix, cy + 90, d[0], d[1], d[2], 1f);
                if (showCraft()) {
                    GLUIRenderer.drawText(t("container.crafting"), craftX(), craftY() - 8, d[0], d[1], d[2], 1f);
                    for (int i = 0; i < 4; i++) {
                        float sx = craftX() + (i % 2) * (SLOT + GAP), sy = craftY() + (i / 2) * (SLOT + GAP);
                        Mc.slot(sx, sy, SLOT, GLUIRenderer.isPointInRect(mouse[0], mouse[1], sx, sy, SLOT, SLOT));
                        if (craft[i] != null) craft[i].drawIcon(sx + SLOT / 2, sy + SLOT / 2, SLOT * 0.62f);
                    }
                    float rx = resultX(), ry = resultY();
                    GLUIRenderer.drawRect(rx - 34, ry + SLOT / 2 - 3, 22, 6, 0.3f, 0.3f, 0.3f, 1);
                    Mc.slot(rx, ry, SLOT, GLUIRenderer.isPointInRect(mouse[0], mouse[1], rx, ry, SLOT, SLOT));
                    GLUIRenderer.drawBorder(rx, ry, SLOT, SLOT, 2, 0.95f, 0.8f, 0.3f, 1);
                    if (craftResult != null) craftResult.drawIcon(rx + SLOT / 2, ry + SLOT / 2, SLOT * 0.62f);
                    RecipeBook.drawToggle(bookX(), bookY(), mouse);
                }
                // creative tabs (icons like Minecraft)
                int hoverTab = -1;
                if (creative()) {
                    for (int i = 0; i < TABS; i++) {
                        float tx = gridX() + i * TAB_STEP, ty = tabY();
                        boolean hover = GLUIRenderer.isPointInRect(mouse[0], mouse[1], tx, ty, TAB_W, TAB_H), sel = tab == i;
                        if (hover) hoverTab = i;
                        GLUIRenderer.drawPanel(tx, ty, TAB_W, TAB_H + (sel ? 4 : 0), 0, 0, 0, 1);
                        float g = sel ? 0.78f : hover ? 0.6f : 0.47f;
                        GLUIRenderer.drawPanel(tx + 1, ty + 1, TAB_W - 2, TAB_H - 2 + (sel ? 6 : 0), g, g, g, 1);
                        tabIcons.get(i).drawIcon(tx + TAB_W / 2, ty + TAB_H / 2 + 1, 20);
                    }
                    if (paletteTab()) {
                        int pages = (palette().size() + perPage() - 1) / perPage();
                        if (pages > 1) GLUIRenderer.drawText((page + 1) + "/" + pages, gridX() + TABS * TAB_STEP + 6, tabY() + 21, d[0], d[1], d[2], 1f);
                    }
                }
                // grid
                for (int i = 0; i < perPage(); i++) {
                    float sx = gridX() + (i % COLS) * (SLOT + GAP), sy = gridY() + (i / COLS) * (SLOT + GAP);
                    Mc.slot(sx, sy, SLOT, GLUIRenderer.isPointInRect(mouse[0], mouse[1], sx, sy, SLOT, SLOT));
                    Items.Stack s = gridStack(i);
                    if (s != null) s.drawIcon(sx + SLOT / 2, sy + SLOT / 2, SLOT * 0.62f);
                }
                for (int i = 0; i < 9; i++) {
                    float sx = gridX() + i * (SLOT + GAP), sy = hotbarY();
                    Mc.slot(sx, sy, SLOT, GLUIRenderer.isPointInRect(mouse[0], mouse[1], sx, sy, SLOT, SLOT));
                    if (i == BuiltinUI.selected) GLUIRenderer.drawBorder(sx, sy, SLOT, SLOT, 2, 1, 1, 1, 1);
                    if (BuiltinUI.HOTBAR[i] != null) BuiltinUI.HOTBAR[i].drawIcon(sx + SLOT / 2, sy + SLOT / 2, SLOT * 0.62f);
                }
                if (carried != null) carried.drawIcon(mouse[0], mouse[1], SLOT * 0.62f);
                else if (hoverTab >= 0) label(tabName(hoverTab), mouse[0], mouse[1]);
                else {
                    Items.Stack hover = hovered(mouse[0], mouse[1]);
                    if (hover != null) tooltip(hover, mouse[0], mouse[1]);
                    else if (showCraft() && RecipeBook.toggleHit(bookX(), bookY(), mouse[0], mouse[1])) label(t("recipes.open"), mouse[0], mouse[1]);
                }
            } finally { GLUIRenderer.restore3DRendering(); }
        }

        private void label(String text, float mx, float my) {
            float tw = GLUIRenderer.getTextWidth(text) + 10, th = 24;
            float x = Math.min(mx + 12, width - tw - 4), y = my - th - 4;
            GLUIRenderer.drawPanel(x, y, tw, th, 0.06f, 0.0f, 0.06f, 0.92f);
            GLUIRenderer.drawBorder(x, y, tw, th, 1, 0.3f, 0.1f, 0.5f, 1);
            Mc.text(text, x + 5, y + 17, Mc.TEXT);
        }

        private void tooltip(Items.Stack s, float mx, float my) {
            String name = s.name(), tip = s.tooltip();
            float tw = Math.max(GLUIRenderer.getTextWidth(name), tip == null ? 0 : GLUIRenderer.getTextWidth(tip)) + 10, th = tip == null ? 24 : 44;
            float x = Math.min(mx + 12, width - tw - 4), y = my - th - 4;
            GLUIRenderer.drawPanel(x, y, tw, th, 0.06f, 0.0f, 0.06f, 0.92f);
            GLUIRenderer.drawBorder(x, y, tw, th, 1, 0.3f, 0.1f, 0.5f, 1);
            Mc.text(name, x + 5, y + 17, Mc.TEXT);
            if (tip != null) Mc.text(tip, x + 5, y + 37, Mc.TEXT_DIM);
        }

        private float craftX() { return px() + panelW() - 16 - 2 * (SLOT + GAP) - 50 - SLOT; }
        private float craftY() { return py() + 52; }
        private float resultX() { return craftX() + 2 * (SLOT + GAP) + 46; }
        private float resultY() { return craftY() + (SLOT + GAP) / 2; }
        private float bookX() { return craftX() - 40; }
        private float bookY() { return craftY() + (SLOT + GAP) / 2 + 8; }
        private int craftHit(float mx, float my) {
            if (!showCraft()) return -1;
            for (int i = 0; i < 4; i++) if (GLUIRenderer.isPointInRect(mx, my, craftX() + (i % 2) * (SLOT + GAP), craftY() + (i / 2) * (SLOT + GAP), SLOT, SLOT)) return i;
            if (GLUIRenderer.isPointInRect(mx, my, resultX(), resultY(), SLOT, SLOT)) return 4;
            return -1;
        }
        private void recomputeCraft() { craftResult = Recipes.resultOf(craft, 2); }

        private int gridHit(float mx, float my) {
            for (int i = 0; i < perPage(); i++)
                if (GLUIRenderer.isPointInRect(mx, my, gridX() + (i % COLS) * (SLOT + GAP), gridY() + (i / COLS) * (SLOT + GAP), SLOT, SLOT)) return i;
            return -1;
        }
        private int hotbarHit(float mx, float my) {
            for (int i = 0; i < 9; i++) if (GLUIRenderer.isPointInRect(mx, my, gridX() + i * (SLOT + GAP), hotbarY(), SLOT, SLOT)) return i;
            return -1;
        }
        private int armorHit(float mx, float my) {
            for (int i = 0; i < 4; i++) if (GLUIRenderer.isPointInRect(mx, my, armorX(), armorY(i), SLOT, SLOT)) return i;
            return -1;
        }
        private int tabHit(float mx, float my) {
            if (!creative()) return -1;
            for (int i = 0; i < TABS; i++) if (GLUIRenderer.isPointInRect(mx, my, gridX() + i * TAB_STEP, tabY(), TAB_W, TAB_H)) return i;
            return -1;
        }
        private Items.Stack hovered(float mx, float my) {
            int g = gridHit(mx, my);
            if (g >= 0) return gridStack(g);
            int h = hotbarHit(mx, my);
            if (h >= 0) return BuiltinUI.HOTBAR[h];
            int c = craftHit(mx, my);
            if (c >= 0) return c == 4 ? craftResult : craft[c];
            int a = armorHit(mx, my);
            return a >= 0 ? Survival.ARMOR[a] : null;
        }

        private boolean shift() {
            long w = UIManager.get().getWindow();
            return GLFW.glfwGetKey(w, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(w, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        }

        /** Shift-click: armor goes to its slot, otherwise to the first free hotbar slot (or storage). */
        private boolean quickMove(Items.Stack s) {
            if (s == null) return false;
            if (s.item != null && s.item.isArmor() && Survival.ARMOR[s.item.armorSlot()] == null) { Survival.ARMOR[s.item.armorSlot()] = s; return true; }
            for (int i = 0; i < 9; i++) if (BuiltinUI.HOTBAR[i] == null) { BuiltinUI.HOTBAR[i] = s; return true; }
            for (int i = 0; i < BuiltinUI.STORAGE.length; i++) if (BuiltinUI.STORAGE[i] == null) { BuiltinUI.STORAGE[i] = s; return true; }
            return false;
        }

        /** Right click on a normal slot: pick up half / drop one, like Minecraft. */
        private void rightClick(Items.Stack[] arr, int i) {
            Items.Stack s = arr[i];
            if (carried == null && s != null) { int half = (s.count + 1) / 2; carried = Containers.ContainerScreen.copy(s); carried.count = half; s.count -= half; if (s.count <= 0) arr[i] = null; }
            else if (carried != null && (s == null || (s.same(carried) && s.count < 64))) { if (s == null) { arr[i] = Containers.ContainerScreen.copy(carried); arr[i].count = 1; } else s.count++; if (--carried.count <= 0) carried = null; }
        }

        /** Left click on a normal slot: swap, merge or pick up. */
        private void leftClick(Items.Stack[] arr, int i) {
            Items.Stack s = arr[i];
            if (carried != null && s != null && s.same(carried)) { int move = Math.min(64 - s.count, carried.count); s.count += move; carried.count -= move; if (carried.count <= 0) carried = null; }
            else { arr[i] = carried; carried = s; }
        }

        @Override public void onMouseClick(int mx, int my, int button) {
            if (showCraft()) {
                if (book.click(mx, my, button)) return;
                if (RecipeBook.toggleHit(bookX(), bookY(), mx, my)) { book.open = !book.open; BuiltinUI.sound().play("click"); return; }
            }
            int tabIdx = tabHit(mx, my);
            if (tabIdx >= 0) { tab = tabIdx; page = 0; return; }
            int g = gridHit(mx, my), h = hotbarHit(mx, my), a = armorHit(mx, my), c = craftHit(mx, my);
            boolean shift = shift();
            if (c == 4) {
                if (craftResult != null) {
                    if (shift) { int guard = 0; while (craftResult != null && guard++ < 64 && Mining.give(Containers.ContainerScreen.copy(craftResult))) { Recipes.consume(craft); recomputeCraft(); } }
                    else if (carried == null) { carried = Containers.ContainerScreen.copy(craftResult); Recipes.consume(craft); }
                    else if (carried.same(craftResult) && carried.count + craftResult.count <= 64) { carried.count += craftResult.count; Recipes.consume(craft); }
                    recomputeCraft();
                }
                return;
            }
            if (c >= 0) {
                if (button == 1) rightClick(craft, c);
                else if (shift && craft[c] != null) { if (Mining.give(craft[c])) craft[c] = null; }
                else leftClick(craft, c);
                recomputeCraft();
                return;
            }
            // Click outside the window with something carried: throw it into the world.
            if (carried != null && button == 0 && !GLUIRenderer.isPointInRect(mx, my, px(), py(), panelW(), panelH())) {
                Drops.throwFromPlayer(engine, carried);
                carried = null;
                return;
            }
            if (g >= 0) {
                Items.Stack s = gridStack(g);
                if (paletteTab()) {
                    if (shift && s != null) quickMove(s.fresh());
                    else carried = button == 1 || carried != null ? null : (s == null ? null : s.fresh());
                } else if (button == 1 && creative() && carried == null) BuiltinUI.STORAGE[g] = null;
                else if (button == 1) rightClick(BuiltinUI.STORAGE, g);
                else if (shift && s != null) { if (quickMoveFromStorage(g)) return; }
                else leftClick(BuiltinUI.STORAGE, g);
            } else if (h >= 0) {
                if (button == 1 && creative() && carried == null) BuiltinUI.HOTBAR[h] = null;
                else if (button == 1) rightClick(BuiltinUI.HOTBAR, h);
                else if (shift && BuiltinUI.HOTBAR[h] != null) {
                    Items.Stack s = BuiltinUI.HOTBAR[h];
                    if (s.item != null && s.item.isArmor() && Survival.ARMOR[s.item.armorSlot()] == null) { Survival.ARMOR[s.item.armorSlot()] = s; BuiltinUI.HOTBAR[h] = null; }
                    else if (paletteTab()) BuiltinUI.HOTBAR[h] = null;
                    else for (int i = 0; i < BuiltinUI.STORAGE.length; i++) if (BuiltinUI.STORAGE[i] == null) { BuiltinUI.STORAGE[i] = s; BuiltinUI.HOTBAR[h] = null; break; }
                } else if (paletteTab() && carried != null) { BuiltinUI.HOTBAR[h] = carried; carried = null; }
                else leftClick(BuiltinUI.HOTBAR, h);
                BuiltinUI.select(engine, BuiltinUI.selected);
            } else if (a >= 0) {
                if (button == 1) Survival.ARMOR[a] = null;
                else if (carried == null) { Items.Stack t = Survival.ARMOR[a]; Survival.ARMOR[a] = null; if (!shift || !quickMove(t)) carried = t; }
                else if (carried.item != null && carried.item.armorSlot() == a) { Items.Stack t = Survival.ARMOR[a]; Survival.ARMOR[a] = carried; carried = paletteTab() ? null : t; }
            } else if (carried != null && button == 0) carried = null;
        }

        private boolean quickMoveFromStorage(int g) {
            Items.Stack s = BuiltinUI.STORAGE[g];
            if (s.item != null && s.item.isArmor() && Survival.ARMOR[s.item.armorSlot()] == null) { Survival.ARMOR[s.item.armorSlot()] = s; BuiltinUI.STORAGE[g] = null; return true; }
            for (int i = 0; i < 9; i++) if (BuiltinUI.HOTBAR[i] == null) { BuiltinUI.HOTBAR[i] = s; BuiltinUI.STORAGE[g] = null; return true; }
            return false;
        }

        @Override public void onKeyPress(int key) {
            if (key == Keybinds.key("inventory") || key == GLFW.GLFW_KEY_ESCAPE) { UIManager.get().closeTopGUI(); return; }
            if (key == Keybinds.key("drop")) {
                float[] m = Mc.mouse();
                Items.Stack s = carried != null ? carried : hovered(m[0], m[1]);
                if (s != null) {
                    Drops.throwFromPlayer(engine, s);
                    if (carried == s) carried = null;
                    else { int gi = gridHit(m[0], m[1]), hi = hotbarHit(m[0], m[1]), ai = armorHit(m[0], m[1]), ci = craftHit(m[0], m[1]);
                        if (hi >= 0) BuiltinUI.HOTBAR[hi] = null; else if (ai >= 0) Survival.ARMOR[ai] = null; else if (gi >= 0 && !paletteTab()) BuiltinUI.STORAGE[gi] = null; else if (ci >= 0 && ci < 4) { craft[ci] = null; recomputeCraft(); } }
                    BuiltinUI.select(engine, BuiltinUI.selected);
                }
                return;
            }
            if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9 && carried != null) {
                int i = key - GLFW.GLFW_KEY_1;
                BuiltinUI.HOTBAR[i] = carried;
                if (!paletteTab()) carried = null;
                BuiltinUI.select(engine, BuiltinUI.selected);
            }
        }

        @Override public void onClose() {
            if (skinTex != null) skinTex.cleanup();
            if (carried != null) { if (!Mining.give(carried)) Drops.throwFromPlayer(engine, carried); carried = null; }
            for (int i = 0; i < 4; i++) if (craft[i] != null) { if (!Mining.give(craft[i])) Drops.throwFromPlayer(engine, craft[i]); craft[i] = null; }
            if (engine.getCamera() != null && engine.getCamera().getInputHandler() != null && !UIManager.get().hasActiveGUIs()) engine.getCamera().getInputHandler().paused = false;
            BuiltinUI.select(engine, BuiltinUI.selected);
        }
    }

    static void async(Runnable task) {
        Thread t = new Thread(task, "ModLoader-ui");
        t.setDaemon(true);
        t.start();
    }
}
