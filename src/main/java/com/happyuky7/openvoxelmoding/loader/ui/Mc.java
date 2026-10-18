package com.happyuky7.openvoxelmoding.loader.ui;

import net.openvoxel.engine.VoxelEngine;
import net.openvoxel.engine.rendering.Camera;
import net.openvoxel.engine.rendering.Texture;
import net.openvoxel.engine.ui.GLUIRenderer;
import net.openvoxel.engine.ui.UIManager;
import net.openvoxel.engine.ui.widgets.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Consumer;

/** Minecraft-flavoured drawing helpers and widgets built on the game's immediate-mode UI renderer. */
public final class Mc {
    private Mc() {}

    public static final float[] TEXT = {1f, 1f, 1f};
    public static final float[] TEXT_DARK = {0.25f, 0.25f, 0.25f};
    public static final float[] TEXT_DIM = {0.63f, 0.63f, 0.63f};
    static final float[] YELLOW = {1f, 1f, 0.63f};

    private static Texture dirt, stone, logo;
    private static boolean texturesTried;

    private static void loadTextures() {
        if (texturesTried) return;
        texturesTried = true;
        try { dirt = new Texture("textures/blocks/dirt.png"); } catch (Exception e) { dirt = null; }
        try { stone = new Texture("textures/blocks/stone.png"); } catch (Exception e) { stone = null; }
        try { logo = new Texture("textures/logo.png"); } catch (Exception e) { logo = null; }
    }

    static Texture logo() { loadTextures(); return logo; }

    /** Tiled, darkened dirt like the classic options background. */
    static void dirt(float x, float y, float w, float h, float shade) {
        loadTextures();
        if (dirt == null) { GLUIRenderer.drawPanel(x, y, w, h, 0.16f, 0.11f, 0.07f, 1f); return; }
        float tile = 32f;
        for (float ty = y; ty < y + h; ty += tile)
            for (float tx = x; tx < x + w; tx += tile) {
                float tw = Math.min(tile, x + w - tx), th = Math.min(tile, y + h - ty);
                float u1 = tw / tile, v1 = th / tile;
                GLUIRenderer.drawTexturedQuad(dirt, tx, ty, tx + tw, ty, tx + tw, ty + th, tx, ty + th,
                        0, 1, u1, 1, u1, 1 - v1, 0, 1 - v1, shade);
            }
    }

    public static void text(String s, float x, float y, float[] c) { text(s, x, y, c[0], c[1], c[2], 1f); }

    /** Text with the classic 1px dark drop shadow. {@code y} is the baseline like {@link Label}. */
    public static void text(String s, float x, float y, float r, float g, float b, float a) {
        GLUIRenderer.drawText(s, x + 1, y + 1, r * 0.25f, g * 0.25f, b * 0.25f, a);
        GLUIRenderer.drawText(s, x, y, r, g, b, a);
    }

    public static void centered(String s, float cx, float y, float[] c) {
        text(s, cx - GLUIRenderer.getTextWidth(s) / 2f, y, c);
    }

    static float baseline(float y, float h) {
        float fh = GLUIRenderer.getFontHeight();
        return y + (h - fh) / 2f + fh * 0.75f;
    }

    public static void button(float x, float y, float w, float h, String label, boolean hovered, boolean enabled) {
        float r, g, b;
        if (!enabled) { r = g = b = 0.30f; }
        else if (hovered) { r = 0.50f; g = 0.56f; b = 0.78f; }
        else { r = g = b = 0.43f; }
        GLUIRenderer.drawPanel(x, y, w, h, 0f, 0f, 0f, 1f);                       // outline
        GLUIRenderer.drawPanel(x + 1, y + 1, w - 2, h - 2, r, g, b, 1f);
        GLUIRenderer.drawPanel(x + 1, y + 1, w - 2, 2, r + 0.22f, g + 0.22f, b + 0.22f, 1f);   // top highlight
        GLUIRenderer.drawPanel(x + 1, y + 1, 2, h - 2, r + 0.22f, g + 0.22f, b + 0.22f, 1f);   // left highlight
        GLUIRenderer.drawPanel(x + 1, y + h - 3, w - 2, 2, r * 0.5f, g * 0.5f, b * 0.5f, 1f);   // bottom shadow
        GLUIRenderer.drawPanel(x + w - 3, y + 1, 2, h - 2, r * 0.5f, g * 0.5f, b * 0.5f, 1f);   // right shadow
        float[] c = !enabled ? TEXT_DIM : hovered ? YELLOW : TEXT;
        centered(label, x + w / 2f, baseline(y, h), c);
    }

    /** Light gray bevelled container like the inventory window. */
    public static void window(float x, float y, float w, float h) {
        GLUIRenderer.drawPanel(x, y, w, h, 0f, 0f, 0f, 1f);
        GLUIRenderer.drawPanel(x + 2, y + 2, w - 4, h - 4, 0.78f, 0.78f, 0.78f, 1f);
        GLUIRenderer.drawPanel(x + 2, y + 2, w - 4, 3, 1f, 1f, 1f, 1f);
        GLUIRenderer.drawPanel(x + 2, y + 2, 3, h - 4, 1f, 1f, 1f, 1f);
        GLUIRenderer.drawPanel(x + 2, y + h - 5, w - 4, 3, 0.33f, 0.33f, 0.33f, 1f);
        GLUIRenderer.drawPanel(x + w - 5, y + 2, 3, h - 4, 0.33f, 0.33f, 0.33f, 1f);
    }

    /** Sunken inventory slot. */
    public static void slot(float x, float y, float s, boolean highlight) {
        GLUIRenderer.drawPanel(x, y, s, s, 0.22f, 0.22f, 0.22f, 1f);
        GLUIRenderer.drawPanel(x + 1, y + 1, s - 2, s - 2, 0.55f, 0.55f, 0.55f, 1f);
        GLUIRenderer.drawPanel(x + 1, y + 1, s - 2, 1, 0.22f, 0.22f, 0.22f, 1f);
        GLUIRenderer.drawPanel(x + 1, y + 1, 1, s - 2, 0.22f, 0.22f, 0.22f, 1f);
        GLUIRenderer.drawPanel(x + 1, y + s - 2, s - 2, 1, 1f, 1f, 1f, 1f);
        GLUIRenderer.drawPanel(x + s - 2, y + 1, 1, s - 2, 1f, 1f, 1f, 1f);
        if (highlight) GLUIRenderer.drawPanel(x + 2, y + 2, s - 4, s - 4, 1f, 1f, 1f, 0.45f);
    }

    /** Translucent hotbar slot used on the in-game HUD. */
    static void hudSlot(float x, float y, float s, boolean selected) {
        GLUIRenderer.drawPanel(x, y, s, s, 0f, 0f, 0f, 0.45f);
        GLUIRenderer.drawBorder(x, y, s, s, 1.5f, 0.55f, 0.55f, 0.55f, 0.85f);
        if (selected) GLUIRenderer.drawBorder(x - 2, y - 2, s + 4, s + 4, 3f, 1f, 1f, 1f, 1f);
    }

    public static float[] mouse() {
        double[] mx = new double[1], my = new double[1];
        GLFW.glfwGetCursorPos(UIManager.get().getWindow(), mx, my);
        return new float[]{(float) mx[0], (float) my[0]};
    }

    public static int[] windowSize() {
        int[] w = new int[1], h = new int[1];
        GLFW.glfwGetWindowSize(UIManager.get().getWindow(), w, h);
        return new int[]{w[0], h[0]};
    }

    static Camera cameraIfInWorld() {
        VoxelEngine engine = VoxelEngine.getEngine();
        return engine != null && engine.isWorldActive() ? engine.getCamera() : null;
    }

    private static Field widgetsField;

    @SuppressWarnings("unchecked")
    static List<Widget> widgetsOf(Screen screen) throws Exception {
        if (widgetsField == null) { widgetsField = Screen.class.getDeclaredField("widgets"); widgetsField.setAccessible(true); }
        return (List<Widget>) widgetsField.get(screen);
    }

    // ---- Widgets ---------------------------------------------------------------------

    public static final class McButton extends Button {
        private String label;
        McButton(float x, float y, float w, float h, String text, Consumer<Button> onClick) { super(x, y, w, h, text, onClick); label = text; }
        @Override public void setText(String text) { super.setText(text); label = text; }
        @Override protected void renderWidget() { button(x, y, width, height, label, hovered, enabled); }
    }

    public static final class McLabel extends Label {
        private String label; private float[] color = TEXT; private boolean centered; private float cw;
        McLabel(float x, float y, String text) { super(x, y, text); label = text; }
        McLabel color(float[] c) { color = c; return this; }
        McLabel centerIn(float w) { centered = true; cw = w; return this; }
        @Override public void setText(String text) { super.setText(text); label = text; }
        @Override protected void renderWidget() {
            if (label == null) return;
            if (centered) centered(label, x + cw / 2f, y, color); else text(label, x, y, color);
        }
    }

    /** Horizontal slider in the options style; value in [0,1]. */
    public static final class McSlider extends Widget {
        interface Format { String label(float v); }
        private float value; private final Format format; private final Consumer<Float> onChange; private boolean dragging;
        McSlider(float x, float y, float w, float h, float value, Format format, Consumer<Float> onChange) { super(x, y, w, h); this.value = value; this.format = format; this.onChange = onChange; }
        @Override protected void renderWidget() {
            if (dragging) {
                if (GLFW.glfwGetMouseButton(UIManager.get().getWindow(), 0) != GLFW.GLFW_PRESS) dragging = false;
                else set(mouse()[0]);
            }
            GLUIRenderer.drawPanel(x, y, width, height, 0f, 0f, 0f, 1f);
            GLUIRenderer.drawPanel(x + 1, y + 1, width - 2, height - 2, 0.30f, 0.30f, 0.30f, 1f);
            float kw = 16, kx = x + 2 + (width - 4 - kw) * value;
            GLUIRenderer.drawPanel(kx, y + 2, kw, height - 4, 0f, 0f, 0f, 1f);
            GLUIRenderer.drawPanel(kx + 1, y + 3, kw - 2, height - 6, hovered || dragging ? 0.56f : 0.43f, hovered || dragging ? 0.62f : 0.43f, hovered || dragging ? 0.80f : 0.43f, 1f);
            centered(format.label(value), x + width / 2f, baseline(y, height), hovered ? YELLOW : TEXT);
        }
        private void set(float mx) {
            float v = Math.max(0f, Math.min(1f, (mx - x - 2 - 8) / (width - 4 - 16)));
            if (v != value) { value = v; onChange.accept(v); }
        }
        @Override public void onClick(float mx, float my, int button) { if (button == 0) { dragging = true; set(mx); } }
    }

    /** Text field that shows asterisks. Used by the account screen for the password. */
    public static final class PasswordField extends TextField {
        PasswordField(float x, float y, float w, float h, String placeholder) { super(x, y, w, h, placeholder); }
        @Override protected void renderWidget() {
            String real = getText();
            StringBuilder mask = new StringBuilder();
            for (int i = 0; i < real.length(); i++) mask.append('*');
            super.setText(mask.toString());
            super.renderWidget();
            super.setText(real);
        }
    }
}
