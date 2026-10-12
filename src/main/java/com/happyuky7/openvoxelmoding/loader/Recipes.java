package com.happyuky7.openvoxelmoding.loader;

import net.openvoxel.engine.world.block.BlockType;

import java.util.ArrayList;
import java.util.List;

/** Shaped crafting recipes (2x2 inventory grid or 3x3 crafting table), matched at any offset and mirrored. */
final class Recipes {
    private Recipes() {}

    static final class Recipe {
        final String[] pattern; final char[] keys; final Items.Stack[] ingredients; final Items.Stack result;
        Recipe(String[] pattern, char[] keys, Items.Stack[] ingredients, Items.Stack result) { this.pattern = pattern; this.keys = keys; this.ingredients = ingredients; this.result = result; }
        int width() { int w = 0; for (String r : pattern) w = Math.max(w, r.length()); return w; }
        int height() { return pattern.length; }
        Items.Stack ingredient(char c) { if (c == ' ') return null; for (int i = 0; i < keys.length; i++) if (keys[i] == c) return ingredients[i]; return null; }
    }

    static final List<Recipe> ALL = new ArrayList<>();

    private static Items.Stack b(BlockType t) { return Items.Stack.of(t); }
    private static Items.Stack i(String id) { return Items.Stack.of(Items.byId(id)); }

    private static void add(Items.Stack result, String[] pattern, Object... keyValues) {
        char[] keys = new char[keyValues.length / 2];
        Items.Stack[] ing = new Items.Stack[keys.length];
        for (int k = 0; k < keys.length; k++) { keys[k] = (Character) keyValues[k * 2]; ing[k] = (Items.Stack) keyValues[k * 2 + 1]; }
        ALL.add(new Recipe(pattern, keys, ing, result));
    }

    static {
        Items.Stack log = b(BlockType.OAK_LOG), stone = b(BlockType.STONE), stick = i("stick");
        add(Items.Stack.of(Items.byId("stick"), 4), new String[]{"L"}, 'L', log);
        add(Items.Stack.of(Items.byId("crafting_table")), new String[]{"LL", "LL"}, 'L', log);
        add(Items.Stack.of(Items.byId("chest")), new String[]{"LLL", "L L", "LLL"}, 'L', log);
        add(Items.Stack.of(Items.byId("bed")), new String[]{"FFF", "LLL"}, 'F', b(BlockType.OAK_LEAVES), 'L', log);
        add(Items.Stack.of(BlockType.TORCH, 4), new String[]{"C", "S"}, 'C', i("coal"), 'S', stick);
        add(Items.Stack.of(BlockType.SLAB, 6), new String[]{"SSS"}, 'S', stone);
        add(Items.Stack.of(BlockType.STAIR, 4), new String[]{"S  ", "SS ", "SSS"}, 'S', stone);
        add(Items.Stack.of(BlockType.DOOR, 3), new String[]{"LL", "LL", "LL"}, 'L', log);
        add(Items.Stack.of(BlockType.GLASS, 1), new String[]{"S", "C"}, 'S', b(BlockType.SAND), 'C', i("coal"));
        add(Items.Stack.of(Items.byId("golden_apple")), new String[]{"GGG", "GAG", "GGG"}, 'G', i("gold_ingot"), 'A', i("apple"));
        add(Items.Stack.of(Items.byId("bread")), new String[]{"PPP"}, 'P', b(BlockType.PUMPKIN));
        String[] tiers = {"wooden", "stone", "iron", "golden", "diamond"};
        Items.Stack[] mats = {log, stone, i("iron_ingot"), i("gold_ingot"), i("diamond")};
        for (int t = 0; t < tiers.length; t++) {
            Items.Stack m = mats[t];
            add(i(tiers[t] + "_sword"), new String[]{"M", "M", "S"}, 'M', m, 'S', stick);
            add(i(tiers[t] + "_pickaxe"), new String[]{"MMM", " S ", " S "}, 'M', m, 'S', stick);
            add(i(tiers[t] + "_shovel"), new String[]{"M", "S", "S"}, 'M', m, 'S', stick);
            add(i(tiers[t] + "_axe"), new String[]{"MM", "MS", " S"}, 'M', m, 'S', stick);
        }
        String[] armorTiers = {"leather", "iron", "golden", "diamond"};
        Items.Stack[] armorMats = {b(BlockType.OAK_LEAVES), i("iron_ingot"), i("gold_ingot"), i("diamond")};
        for (int t = 0; t < armorTiers.length; t++) {
            Items.Stack m = armorMats[t];
            add(i(armorTiers[t] + "_helmet"), new String[]{"MMM", "M M"}, 'M', m);
            add(i(armorTiers[t] + "_chestplate"), new String[]{"M M", "MMM", "MMM"}, 'M', m);
            add(i(armorTiers[t] + "_leggings"), new String[]{"MMM", "M M", "M M"}, 'M', m);
            add(i(armorTiers[t] + "_boots"), new String[]{"M M", "M M"}, 'M', m);
        }
    }

    /** Finds the recipe matching a grid of {@code size x size} (row-major, null = empty). */
    static Recipe match(Items.Stack[] grid, int size) {
        for (Recipe r : ALL) {
            int w = r.width(), h = r.height();
            if (w > size || h > size) continue;
            for (int oy = 0; oy + h <= size; oy++) for (int ox = 0; ox + w <= size; ox++) {
                if (matches(r, grid, size, ox, oy, false) || matches(r, grid, size, ox, oy, true)) return r;
            }
        }
        return null;
    }

    private static boolean matches(Recipe r, Items.Stack[] grid, int size, int ox, int oy, boolean mirror) {
        int w = r.width(), h = r.height();
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            Items.Stack cell = grid[y * size + x];
            int rx = x - ox, ry = y - oy;
            Items.Stack want = null;
            if (rx >= 0 && rx < w && ry >= 0 && ry < h) {
                String row = r.pattern[ry];
                int col = mirror ? w - 1 - rx : rx;
                want = col < row.length() ? r.ingredient(row.charAt(col)) : null;
            }
            if (want == null ? cell != null : (cell == null || !cell.same(want))) return false;
        }
        return true;
    }

    /** Consumes one of each ingredient from the grid (call after taking the result). */
    static void consume(Items.Stack[] grid) {
        for (int i = 0; i < grid.length; i++) if (grid[i] != null && --grid[i].count <= 0) grid[i] = null;
    }

    static Items.Stack resultOf(Items.Stack[] grid, int size) {
        Recipe r = match(grid, size);
        if (r == null) return null;
        return r.result.isBlock() ? Items.Stack.of(r.result.block, r.result.count) : Items.Stack.of(r.result.item, r.result.count);
    }
}
