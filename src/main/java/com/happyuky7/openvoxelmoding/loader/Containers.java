package com.happyuky7.openvoxelmoding.loader;

import net.openvoxel.engine.VoxelEngine;
import net.openvoxel.engine.gui.GUI;
import net.openvoxel.engine.ui.GLUIRenderer;
import net.openvoxel.engine.ui.UIManager;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Generic slot-based container screens: crafting (2x2 / 3x3) and chests, with the player inventory below. */
final class Containers {
    private Containers() {}

    static final float SLOT = 40, GAP = 4;

    static final class Slot {
        final Items.Stack[] arr; final int idx; final float x, y; final boolean result, container;
        Slot(Items.Stack[] arr, int idx, float x, float y, boolean result, boolean container) { this.arr = arr; this.idx = idx; this.x = x; this.y = y; this.result = result; this.container = container; }
        Items.Stack get() { return arr[idx]; }
        void set(Items.Stack s) { arr[idx] = s; }
    }

    abstract static class ContainerScreen extends GUI {
        final VoxelEngine engine; final String title;
        int width, height; float px, py, pw, ph;
        Items.Stack carried;
        final List<Slot> slots = new ArrayList<>();

        ContainerScreen(VoxelEngine engine, String title, float pw, float ph) { this.engine = engine; this.title = title; this.pw = pw; this.ph = ph; }

        abstract void layout();
        void onChanged() {}
        /** Horizontal shift of the window (the recipe book opens to the left). */
        float windowShift() { return 0; }
        void relayout() { px = (width - pw) / 2f + windowShift(); py = (height - ph) / 2f; slots.clear(); layout(); }

        @Override public void onOpen() {
            int[] s = Mc.windowSize(); width = s[0]; height = s[1];
            if (engine.getCamera() != null && engine.getCamera().getInputHandler() != null) engine.getCamera().getInputHandler().paused = true;
            relayout(); onChanged();
        }
        @Override public void onTick(long ticks) {
            int[] s = Mc.windowSize();
            if (s[0] != width || s[1] != height) { width = s[0]; height = s[1]; relayout(); }
        }

        /** Player storage (27) + hotbar (9) at the bottom of the window. */
        void addPlayerInventory(float x, float y) {
            for (int i = 0; i < 27; i++) slots.add(new Slot(BuiltinUI.STORAGE, i, x + (i % 9) * (SLOT + GAP), y + (i / 9) * (SLOT + GAP), false, false));
            for (int i = 0; i < 9; i++) slots.add(new Slot(BuiltinUI.HOTBAR, i, x + i * (SLOT + GAP), y + 3 * (SLOT + GAP) + 10, false, false));
        }

        void renderBefore(float[] mouse) {}

        @Override public void render() {
            GLUIRenderer.setup2DRendering(width, height);
            try {
                GLUIRenderer.drawPanel(0, 0, width, height, 0, 0, 0, 0.6f);
                renderBefore(Mc.mouse());
                Mc.window(px, py, pw, ph);
                float[] d = Mc.TEXT_DARK;
                GLUIRenderer.drawText(title, px + 16, py + 26, d[0], d[1], d[2], 1f);
                float[] mouse = Mc.mouse();
                for (Slot s : slots) {
                    boolean hover = GLUIRenderer.isPointInRect(mouse[0], mouse[1], s.x, s.y, SLOT, SLOT);
                    Mc.slot(s.x, s.y, SLOT, hover);
                    if (s.result) GLUIRenderer.drawBorder(s.x, s.y, SLOT, SLOT, 2, 0.95f, 0.8f, 0.3f, 1);
                    if (s.arr == BuiltinUI.HOTBAR && s.idx == BuiltinUI.selected) GLUIRenderer.drawBorder(s.x, s.y, SLOT, SLOT, 2, 1, 1, 1, 1);
                    Items.Stack st = s.get();
                    if (st != null) st.drawIcon(s.x + SLOT / 2, s.y + SLOT / 2, SLOT * 0.62f);
                }
                renderExtra();
                if (carried != null) carried.drawIcon(mouse[0], mouse[1], SLOT * 0.62f);
                else { Slot h = hit(mouse[0], mouse[1]); if (h != null && h.get() != null) tooltip(h.get(), mouse[0], mouse[1]); }
            } finally { GLUIRenderer.restore3DRendering(); }
        }

        void renderExtra() {}

        void tooltip(Items.Stack s, float mx, float my) {
            String name = s.name(), tip = s.tooltip();
            float tw = Math.max(GLUIRenderer.getTextWidth(name), tip == null ? 0 : GLUIRenderer.getTextWidth(tip)) + 10, th = tip == null ? 24 : 44;
            float x = Math.min(mx + 12, width - tw - 4), y = my - th - 4;
            GLUIRenderer.drawPanel(x, y, tw, th, 0.06f, 0.0f, 0.06f, 0.92f);
            GLUIRenderer.drawBorder(x, y, tw, th, 1, 0.3f, 0.1f, 0.5f, 1);
            Mc.text(name, x + 5, y + 17, Mc.TEXT);
            if (tip != null) Mc.text(tip, x + 5, y + 37, Mc.TEXT_DIM);
        }

        Slot hit(float mx, float my) { for (Slot s : slots) if (GLUIRenderer.isPointInRect(mx, my, s.x, s.y, SLOT, SLOT)) return s; return null; }

        boolean shift() { long w = UIManager.get().getWindow(); return GLFW.glfwGetKey(w, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(w, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS; }

        /** Moves a stack into the first compatible slot of the "other" side (container <-> player). */
        boolean quickMove(Slot from) {
            Items.Stack s = from.get();
            if (s == null) return false;
            for (Slot t : slots) if (t.container != from.container && !t.result && t.get() != null && t.get().same(s) && t.get().count < 64) {
                int move = Math.min(64 - t.get().count, s.count); t.get().count += move; s.count -= move;
                if (s.count <= 0) { from.set(null); return true; }
            }
            for (Slot t : slots) if (t.container != from.container && !t.result && t.get() == null) { t.set(s); from.set(null); return true; }
            return false;
        }

        /** First chance for subclasses (recipe book); return true to consume the click. */
        boolean clickBefore(int mx, int my, int button) { return false; }

        @Override public void onMouseClick(int mx, int my, int button) {
            if (clickBefore(mx, my, button)) return;
            Slot s = hit(mx, my);
            if (s == null) {
                if (carried != null && button == 0 && !GLUIRenderer.isPointInRect(mx, my, px, py, pw, ph)) { Drops.throwFromPlayer(engine, carried); carried = null; }
                return;
            }
            Items.Stack cur = s.get();
            if (s.result) {
                if (cur == null) return;
                if (shift()) { int guard = 0; while (s.get() != null && guard++ < 64 && Mining.give(copy(s.get()))) { onResultTaken(); onChanged(); } }
                else if (carried == null) { carried = copy(cur); onResultTaken(); }
                else if (carried.same(cur) && carried.count + cur.count <= 64) { carried.count += cur.count; onResultTaken(); }
                else return;
                onChanged();
                afterChange();
                return;
            }
            if (shift() && cur != null) { quickMove(s); onChanged(); afterChange(); return; }
            if (button == 1) {
                if (carried == null && cur != null) { int half = (cur.count + 1) / 2; carried = copy(cur); carried.count = half; cur.count -= half; if (cur.count <= 0) s.set(null); }
                else if (carried != null && (cur == null || (cur.same(carried) && cur.count < 64))) { if (cur == null) { s.set(copy(carried)); s.get().count = 1; } else cur.count++; if (--carried.count <= 0) carried = null; }
            } else {
                if (carried == null) { carried = cur; s.set(null); }
                else if (cur == null) { s.set(carried); carried = null; }
                else if (cur.same(carried)) { int move = Math.min(64 - cur.count, carried.count); cur.count += move; carried.count -= move; if (carried.count <= 0) carried = null; }
                else { s.set(carried); carried = cur; }
            }
            onChanged();
            afterChange();
        }

        void onResultTaken() {}
        void afterChange() { BuiltinUI.select(engine, BuiltinUI.selected); }

        static Items.Stack copy(Items.Stack s) { return s.isBlock() ? Items.Stack.of(s.block, s.count) : Items.Stack.of(s.item, s.count); }

        @Override public void onKeyPress(int key) {
            if (key == Keybinds.key("inventory") || key == GLFW.GLFW_KEY_ESCAPE) { UIManager.get().closeTopGUI(); return; }
            if (key == Keybinds.key("drop")) {
                float[] m = Mc.mouse();
                Slot s = hit(m[0], m[1]);
                Items.Stack st = carried != null ? carried : (s != null && !s.result ? s.get() : null);
                if (st == null) return;
                Drops.throwFromPlayer(engine, st);
                if (carried == st) carried = null; else s.set(null);
                onChanged(); afterChange();
            }
        }

        @Override public void onClose() {
            if (carried != null) { if (!Mining.give(carried)) Drops.throwFromPlayer(engine, carried); carried = null; }
            if (engine.getCamera() != null && engine.getCamera().getInputHandler() != null && !UIManager.get().hasActiveGUIs()) engine.getCamera().getInputHandler().paused = false;
            BuiltinUI.select(engine, BuiltinUI.selected);
        }
    }

    // ---- Crafting ----------------------------------------------------------------------

    static final class CraftingScreen extends ContainerScreen implements RecipeBook.Host {
        final int size; final Items.Stack[] grid; final Items.Stack[] result = new Items.Stack[1];
        final RecipeBook book = new RecipeBook(this);
        CraftingScreen(VoxelEngine engine, int size) { super(engine, Lang.t("container.crafting"), 9 * (SLOT + GAP) - GAP + 32, 60 + size * (SLOT + GAP) + 24 + 4 * (SLOT + GAP) + 26); this.size = size; grid = new Items.Stack[size * size]; }
        private float gx() { return px + 16 + (size == 2 ? 2 * (SLOT + GAP) : 0); }
        private float gy() { return py + 44; }
        private float bookX() { return px + pw - 16 - 28; }
        private float bookY() { return gy() + ((size - 1) * (SLOT + GAP)) / 2f + SLOT / 2 - 12; }
        @Override float windowShift() { return book.open ? (RecipeBook.W + 8) / 2f : 0; }
        @Override void layout() {
            float gx = gx(), gy = gy();
            for (int i = 0; i < size * size; i++) slots.add(new Slot(grid, i, gx + (i % size) * (SLOT + GAP), gy + (i / size) * (SLOT + GAP), false, true));
            float rx = gx + size * (SLOT + GAP) + 50, ry = gy + ((size - 1) * (SLOT + GAP)) / 2f;
            slots.add(new Slot(result, 0, rx, ry, true, true));
            addPlayerInventory(px + 16, gy + size * (SLOT + GAP) + 24);
        }
        @Override void renderBefore(float[] mouse) { book.layout(px, py, ph); book.render(mouse); }
        @Override void renderExtra() {
            float gx = gx(), gy = gy();
            float ax = gx + size * (SLOT + GAP) + 10, ay = gy + ((size - 1) * (SLOT + GAP)) / 2f + SLOT / 2;
            GLUIRenderer.drawRect(ax, ay - 3, 24, 6, 0.3f, 0.3f, 0.3f, 1);
            GLUIRenderer.drawRect(ax + 22, ay - 8, 6, 16, 0.3f, 0.3f, 0.3f, 1);
            RecipeBook.drawToggle(bookX(), bookY(), Mc.mouse());
        }
        @Override boolean clickBefore(int mx, int my, int button) {
            if (book.click(mx, my, button)) { afterChange(); return true; }
            if (RecipeBook.toggleHit(bookX(), bookY(), mx, my)) { book.open = !book.open; relayout(); BuiltinUI.sound().play("click"); return true; }
            return false;
        }
        @Override public int gridSize() { return size; }
        @Override public Items.Stack[] grid() { return grid; }
        @Override public void gridChanged() { onChanged(); }
        @Override void onChanged() { result[0] = Recipes.resultOf(grid, size); }
        @Override void onResultTaken() { Recipes.consume(grid); }
        @Override public void onClose() {
            for (int i = 0; i < grid.length; i++) if (grid[i] != null) { if (!Mining.give(grid[i])) Drops.throwFromPlayer(engine, grid[i]); grid[i] = null; }
            super.onClose();
        }
    }

    // ---- Chest -------------------------------------------------------------------------

    static final class ChestScreen extends ContainerScreen {
        final Tiles.Tile tile;
        ChestScreen(VoxelEngine engine, Tiles.Tile tile) { super(engine, Lang.t("container.chest"), 9 * (SLOT + GAP) - GAP + 32, 44 + 3 * (SLOT + GAP) + 24 + 4 * (SLOT + GAP) + 26); this.tile = tile; }
        @Override void layout() {
            float gx = px + 16, gy = py + 44;
            for (int i = 0; i < 27; i++) slots.add(new Slot(tile.chest, i, gx + (i % 9) * (SLOT + GAP), gy + (i / 9) * (SLOT + GAP), false, true));
            addPlayerInventory(gx, gy + 3 * (SLOT + GAP) + 24);
        }
        @Override void afterChange() { super.afterChange(); Tiles.save(); }
        @Override public void onClose() { Tiles.save(); super.onClose(); }
    }
}
