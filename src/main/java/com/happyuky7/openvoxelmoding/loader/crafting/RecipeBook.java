package com.happyuky7.openvoxelmoding.loader.crafting;

import com.happyuky7.openvoxelmoding.loader.Lang;

import com.happyuky7.openvoxelmoding.loader.core.Settings;
import com.happyuky7.openvoxelmoding.loader.item.Containers;
import com.happyuky7.openvoxelmoding.loader.item.Items;
import com.happyuky7.openvoxelmoding.loader.item.Mining;
import com.happyuky7.openvoxelmoding.loader.ui.BuiltinUI;
import com.happyuky7.openvoxelmoding.loader.ui.Mc;

import net.openvoxel.engine.ui.GLUIRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Recipe book panel shown left of a crafting grid (inventory 2x2 or crafting table 3x3).
 * Lists the recipes that fit the grid, craftable ones first; clicking a recipe moves the
 * ingredients from the inventory into the grid (creative conjures them).
 */
public final class RecipeBook {
    public interface Host { int gridSize(); Items.Stack[] grid(); void gridChanged(); }

    public static final float W = 250;
    private static final float SLOT = 36, GAP = 4;
    private static final int COLS = 5, ROWS = 6;

    public boolean open, craftableOnly = true;
    private int page;
    private Recipes.Recipe selected;
    private float x, y, h;
    private final Host host;

    public RecipeBook(Host host) { this.host = host; }

    public void layout(float panelX, float panelY, float panelH) { x = panelX - W - 8; y = panelY; h = panelH; }

    /** Small book toggle button; call from the host, returns true if the click toggled it. */
    public static void drawToggle(float bx, float by, float[] mouse) {
        boolean hover = GLUIRenderer.isPointInRect(mouse[0], mouse[1], bx, by, 28, 24);
        Mc.button(bx, by, 28, 24, "", hover, true);
        GLUIRenderer.drawRect(bx + 7, by + 5, 14, 14, 0.55f, 0.32f, 0.16f, 1f);   // book cover
        GLUIRenderer.drawRect(bx + 9, by + 7, 10, 10, 0.95f, 0.92f, 0.80f, 1f);   // pages
        GLUIRenderer.drawRect(bx + 13, by + 5, 2, 14, 0.35f, 0.2f, 0.1f, 1f);     // spine
    }

    public static boolean toggleHit(float bx, float by, float mx, float my) { return GLUIRenderer.isPointInRect(mx, my, bx, by, 28, 24); }

    List<Recipes.Recipe> visible() {
        List<Recipes.Recipe> out = new ArrayList<>(), later = new ArrayList<>();
        int size = host.gridSize();
        boolean creative = Settings.isCreative();
        for (Recipes.Recipe r : Recipes.ALL) {
            if (r.width() > size || r.height() > size) continue;
            boolean can = creative || hasIngredients(r);
            if (can) out.add(r); else if (!craftableOnly) later.add(r);
        }
        out.addAll(later);
        return out;
    }

    static boolean hasIngredients(Recipes.Recipe r) {
        List<Items.Stack> need = needed(r);
        for (Items.Stack n : need) if (available(n) < n.count) return false;
        return true;
    }

    private static List<Items.Stack> needed(Recipes.Recipe r) {
        List<Items.Stack> need = new ArrayList<>();
        for (String row : r.pattern) for (char c : row.toCharArray()) {
            Items.Stack ing = r.ingredient(c);
            if (ing == null) continue;
            boolean found = false;
            for (Items.Stack n : need) if (n.same(ing)) { n.count++; found = true; break; }
            if (!found) need.add(Containers.ContainerScreen.copy(ing));
        }
        for (Items.Stack n : need) if (n.count == 0) n.count = 1;
        return need;
    }

    private static int available(Items.Stack want) {
        int n = 0;
        for (Items.Stack s : BuiltinUI.HOTBAR) if (s != null && s.same(want)) n += s.count;
        for (Items.Stack s : BuiltinUI.STORAGE) if (s != null && s.same(want)) n += s.count;
        return n;
    }

    private static boolean take(Items.Stack want) {
        Items.Stack[][] areas = {BuiltinUI.STORAGE, BuiltinUI.HOTBAR};
        for (Items.Stack[] area : areas) for (int i = 0; i < area.length; i++) {
            Items.Stack s = area[i];
            if (s != null && s.same(want)) { if (--s.count <= 0) area[i] = null; return true; }
        }
        return false;
    }

    /** Puts the recipe into the host grid (returning whatever was there to the inventory first). */
    void fill(Recipes.Recipe r) {
        Items.Stack[] grid = host.grid();
        int size = host.gridSize();
        for (int i = 0; i < grid.length; i++) if (grid[i] != null) { if (!Mining.give(grid[i])) return; grid[i] = null; }
        boolean creative = Settings.isCreative();
        if (!creative && !hasIngredients(r)) return;
        int ox = (size - r.width()) / 2, oy = (size - r.height()) / 2;
        for (int ry = 0; ry < r.height(); ry++) {
            String row = r.pattern[ry];
            for (int rx = 0; rx < row.length(); rx++) {
                Items.Stack ing = r.ingredient(row.charAt(rx));
                if (ing == null) continue;
                if (!creative && !take(ing)) continue;
                int idx = (oy + ry) * size + ox + rx;
                Items.Stack cell = grid[idx];
                if (cell != null && cell.same(ing)) cell.count++;
                else { Items.Stack c = Containers.ContainerScreen.copy(ing); c.count = 1; grid[idx] = c; }
            }
        }
        host.gridChanged();
        BuiltinUI.sound().play("click");
    }

    public void render(float[] mouse) {
        if (!open) return;
        Mc.window(x, y, W, h);
        float[] d = Mc.TEXT_DARK;
        GLUIRenderer.drawText(Lang.t("recipes.title"), x + 14, y + 24, d[0], d[1], d[2], 1f);
        float bx = x + W - 14 - 110, by = y + 8;
        boolean hover = GLUIRenderer.isPointInRect(mouse[0], mouse[1], bx, by, 110, 22);
        Mc.button(bx, by, 110, 22, Lang.t(craftableOnly ? "recipes.craftable" : "recipes.all"), hover, true);
        List<Recipes.Recipe> list = visible();
        int per = COLS * ROWS, pages = Math.max(1, (list.size() + per - 1) / per);
        page = Math.max(0, Math.min(page, pages - 1));
        float gx = x + 14, gy = y + 40;
        boolean creative = Settings.isCreative();
        Recipes.Recipe hovered = null;
        for (int i = 0; i < per; i++) {
            int idx = page * per + i;
            float sx = gx + (i % COLS) * (SLOT + GAP), sy = gy + (i / COLS) * (SLOT + GAP);
            boolean h = GLUIRenderer.isPointInRect(mouse[0], mouse[1], sx, sy, SLOT, SLOT);
            Mc.slot(sx, sy, SLOT, h);
            if (idx >= list.size()) continue;
            Recipes.Recipe r = list.get(idx);
            boolean can = creative || hasIngredients(r);
            r.result.drawIcon(sx + SLOT / 2, sy + SLOT / 2, SLOT * 0.6f);
            if (!can) GLUIRenderer.drawPanel(sx + 2, sy + 2, SLOT - 4, SLOT - 4, 0.3f, 0.3f, 0.3f, 0.55f);
            if (r == selected) GLUIRenderer.drawBorder(sx, sy, SLOT, SLOT, 2, 0.95f, 0.8f, 0.3f, 1);
            if (h) hovered = r;
        }
        float ny = gy + ROWS * (SLOT + GAP) + 4;
        if (pages > 1) {
            Mc.button(gx, ny, 60, 22, "<", GLUIRenderer.isPointInRect(mouse[0], mouse[1], gx, ny, 60, 22), page > 0);
            Mc.button(gx + COLS * (SLOT + GAP) - GAP - 60, ny, 60, 22, ">", GLUIRenderer.isPointInRect(mouse[0], mouse[1], gx + COLS * (SLOT + GAP) - GAP - 60, ny, 60, 22), page < pages - 1);
            Mc.centered((page + 1) + "/" + pages, gx + (COLS * (SLOT + GAP) - GAP) / 2f, ny + 16, Mc.TEXT_DARK);
        }
        // preview of the selected (or hovered) recipe: pattern with ingredient icons
        Recipes.Recipe show = hovered != null ? hovered : selected;
        float py = ny + 30;
        if (show != null && py + 3 * (SLOT + GAP) < y + h) {
            float cell = 28, cx0 = gx;
            int size = host.gridSize();
            for (int ry = 0; ry < size; ry++) for (int rx = 0; rx < size; rx++) {
                float sx = cx0 + rx * (cell + 2), sy = py + ry * (cell + 2);
                Mc.slot(sx, sy, cell, false);
                int ox = (size - show.width()) / 2, oy = (size - show.height()) / 2;
                int px = rx - ox, pyy = ry - oy;
                if (px >= 0 && pyy >= 0 && pyy < show.height() && px < show.pattern[pyy].length()) {
                    Items.Stack ing = show.ingredient(show.pattern[pyy].charAt(px));
                    if (ing != null) ing.drawIcon(sx + cell / 2, sy + cell / 2, cell * 0.55f);
                }
            }
            float tx = cx0 + size * (cell + 2) + 12;
            Mc.text(show.result.name() + (show.result.count > 1 ? " x" + show.result.count : ""), tx, py + 16, Mc.TEXT_DARK);
            float ly = py + 36;
            for (Items.Stack n : needed(show)) {
                int have = available(n);
                boolean ok = creative || have >= n.count;
                GLUIRenderer.drawText(n.count + "x " + n.name() + (creative ? "" : " (" + have + ")"), tx, ly, ok ? 0.1f : 0.6f, ok ? 0.45f : 0.1f, 0.1f, 1f);
                ly += 18;
                if (ly > y + h - 10) break;
            }
            if (!creative && !hasIngredients(show)) GLUIRenderer.drawText(Lang.t("recipes.missing"), gx, Math.min(y + h - 12, py + size * (cell + 2) + 16), 0.6f, 0.1f, 0.1f, 1f);
        }
    }

    /** Returns true when the click was inside the book. */
    public boolean click(float mx, float my, int button) {
        if (!open || !GLUIRenderer.isPointInRect(mx, my, x, y, W, h)) return false;
        float bx = x + W - 14 - 110, by = y + 8;
        if (GLUIRenderer.isPointInRect(mx, my, bx, by, 110, 22)) { craftableOnly = !craftableOnly; page = 0; return true; }
        List<Recipes.Recipe> list = visible();
        int per = COLS * ROWS, pages = Math.max(1, (list.size() + per - 1) / per);
        float gx = x + 14, gy = y + 40;
        for (int i = 0; i < per; i++) {
            float sx = gx + (i % COLS) * (SLOT + GAP), sy = gy + (i / COLS) * (SLOT + GAP);
            if (GLUIRenderer.isPointInRect(mx, my, sx, sy, SLOT, SLOT)) {
                int idx = page * per + i;
                if (idx < list.size()) { selected = list.get(idx); fill(selected); }
                return true;
            }
        }
        float ny = gy + ROWS * (SLOT + GAP) + 4;
        if (pages > 1) {
            if (GLUIRenderer.isPointInRect(mx, my, gx, ny, 60, 22) && page > 0) page--;
            else if (GLUIRenderer.isPointInRect(mx, my, gx + COLS * (SLOT + GAP) - GAP - 60, ny, 60, 22) && page < pages - 1) page++;
        }
        return true;
    }
}
