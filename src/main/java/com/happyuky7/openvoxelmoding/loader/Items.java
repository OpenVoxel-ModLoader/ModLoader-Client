package com.happyuky7.openvoxelmoding.loader;

import net.openvoxel.engine.rendering.Texture;
import net.openvoxel.engine.ui.BlockIconRenderer;
import net.openvoxel.engine.ui.GLUIRenderer;
import net.openvoxel.engine.world.block.BlockType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;

/** Client-side items (swords, armor, food) with generated 16x16 sprites, and the hotbar Stack type. */
final class Items {
    private Items() {}

    enum Kind { SWORD, PICKAXE, SHOVEL, AXE, HELMET, CHESTPLATE, LEGGINGS, BOOTS, FOOD, MATERIAL, TILE }

    static float toolSpeed(String tier) {
        switch (tier) { case "wooden": return 2f; case "stone": return 4f; case "iron": return 6f; case "golden": return 12f; case "diamond": return 8f; default: return 1f; }
    }

    static final class Item {
        final String id, tier; final Kind kind; final int value; final int[] palette; final String[] rows;
        private Texture texture; private byte[] png;
        /** Display name for mod items (used when no translation key exists). */
        String label;
        Item(String id, Kind kind, String tier, int value, int[] palette, String[] rows) { this.id = id; this.kind = kind; this.tier = tier; this.value = value; this.palette = palette; this.rows = rows; }
        boolean isArmor() { return kind == Kind.HELMET || kind == Kind.CHESTPLATE || kind == Kind.LEGGINGS || kind == Kind.BOOTS; }
        boolean isTool() { return kind == Kind.PICKAXE || kind == Kind.SHOVEL || kind == Kind.AXE; }
        int defaultCount() { return kind == Kind.FOOD ? 16 : kind == Kind.MATERIAL ? 64 : 1; }
        boolean isTile() { return kind == Kind.TILE; }
        int armorSlot() { switch (kind) { case HELMET: return 0; case CHESTPLATE: return 1; case LEGGINGS: return 2; case BOOTS: return 3; default: return -1; } }
        String name() {
            String key = "item." + id.replace(':', '.');
            String t = Lang.t(key);
            return t.equals(key) && label != null ? label : t;
        }
        String tooltip() {
            switch (kind) {
                case SWORD: return Lang.t("tip.damage", value);
                case FOOD: return Lang.t("tip.food", value);
                case PICKAXE: case SHOVEL: case AXE: return Lang.t("tip.tool", toolSpeed(tier));
                case MATERIAL: return Lang.t("tip.material");
                case TILE: return Lang.t("tip.tile");
                default: return Lang.t("tip.armor", value);
            }
        }
        byte[] png() {
            if (png == null) try { png = rows == null ? new byte[0] : encode(sprite(rows, palette)); } catch (Exception e) { png = new byte[0]; }
            return png;
        }
        Texture texture() {
            if (texture == null && png().length > 0) try { texture = new Texture(png()); } catch (Exception e) { texture = null; }
            return texture;
        }
    }

    /** A hotbar/inventory entry: either a block type or an item. */
    static final class Stack {
        final BlockType block; final Item item;
        int count = 1;
        private Stack(BlockType block, Item item) { this.block = block; this.item = item; }
        static Stack of(BlockType block) { return block == null || block == BlockType.AIR ? null : new Stack(block, null); }
        static Stack of(Item item) { return item == null ? null : new Stack(null, item); }
        static Stack of(BlockType block, int count) { Stack s = of(block); if (s != null) s.count = count; return s; }
        static Stack of(Item item, int count) { Stack s = of(item); if (s != null) s.count = count; return s; }
        /** A fresh stack of the same kind, full size (used when taking from the creative palette). */
        Stack fresh() { return block != null ? of(block, 64) : of(item, item.defaultCount()); }
        boolean isBlock() { return block != null; }
        String name() {
            if (block != null) { String n = NativeBlocks.label(block); return n != null ? n : Lang.t("block." + block.name().toLowerCase(), pretty(block.name())); }
            return item.name();
        }
        String tooltip() { return item != null ? item.tooltip() : null; }
        boolean same(Stack o) { return o != null && o.block == block && o.item == item; }
        void drawIcon(float cx, float cy, float size) {
            if (block != null) BlockIconRenderer.drawBlockIcon(block, cx, cy, size);
            else { Texture t = item.texture(); if (t != null) GLUIRenderer.drawTexture(t, cx - size * 0.7f, cy - size * 0.7f, size * 1.4f, size * 1.4f); }
            if (count > 1) {
                String n = Integer.toString(count);
                Mc.text(n, cx + size * 0.75f - GLUIRenderer.getTextWidth(n), cy + size * 0.8f, Mc.TEXT);
            }
        }
    }

    static String pretty(String enumName) {
        String s = enumName.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---- Sprites -----------------------------------------------------------------------
    // Palette slots: A main, a dark, L light, G guard, H handle, h dark handle, R leaf/green, W white.

    private static final String[] SWORD = {
        "..............LA", ".............LAa", "............LAa.", "...........LAa..", "..........LAa...", ".........LAa....",
        "........LAa.....", ".......LAa......", "..G...LAa.......", "...G.LAa........", "....GAa.........", "...hGG..........",
        "..h..GG.........", ".h..............", "h...............", "................"};
    private static final String[] PICKAXE = {
        "................", "........LLLAa...", "......LLAAAAaa..", ".....LAAa..aAa..", "....LAa.....aa..", "....Aa..........",
        "....a..hh.......", "......hGh.......", ".....hGh........", "....hGh.........", "...hGh..........", "..hGh...........",
        ".hGh............", "hGh.............", "hh..............", "................"};
    private static final String[] SHOVEL = {
        "................", "...........LLA..", "..........LAAAa.", "..........LAAAa.", "..........aAAa..", ".........hGaa...",
        "........hGh.....", ".......hGh......", "......hGh.......", ".....hGh........", "....hGh.........", "...hGh..........",
        "..hGh...........", ".hGh............", "hh..............", "................"};
    private static final String[] AXE = {
        "................", "........LLLA....", ".......LAAAAa...", "......LAAAAAa...", ".......AAAAAa...", ".......hGaAa....",
        "......hGh.aa....", ".....hGh........", "....hGh.........", "...hGh..........", "..hGh...........", ".hGh............",
        "hGh.............", "hh..............", "................", "................"};
    private static final String[] STICK = {
        "................", "................", "............Hh..", "...........Hh...", "..........Hh....", ".........Hh.....",
        "........Hh......", ".......Hh.......", "......Hh........", ".....Hh.........", "....Hh..........", "...Hh...........",
        "..Hh............", "................", "................", "................"};
    private static final String[] PLANKS = {
        "hhhhhhhhhhhhhhhh", "hAAAAAAAAAAAAAAh", "hAaaaaaaaaaaaaAh", "hAAAAAAAAAAAAAAh", "hhhhhhhhhhhhhhhh", "hAAAAAAAAAAAAAAh",
        "hAaaaaaaaaaaaaAh", "hAAAAAAAAAAAAAAh", "hhhhhhhhhhhhhhhh", "hAAAAAAAAAAAAAAh", "hAaaaaaaaaaaaaAh", "hAAAAAAAAAAAAAAh",
        "hhhhhhhhhhhhhhhh", "hAAAAAAAAAAAAAAh", "hAaaaaaaaaaaaaAh", "hhhhhhhhhhhhhhhh"};
    private static final String[] INGOT = {
        "................", "................", "................", "................", "......LLLLLLLA..", ".....LLLLLLLAAa.",
        "....LLLLLLAAAAa.", "...LAAAAAAAAAAa.", "..LAAAAAAAAAaa..", "..AAAAAAAAaaa...", "..aaaaaaaaaa....", "................",
        "................", "................", "................", "................"};
    private static final String[] GEM = {
        "................", "................", "................", ".....LLLLLL.....", "....LLAAAAAa....", "...LLAAAAAAAa...",
        "...LAAAAAAAAa...", "....AAAAAAAa....", ".....AAAAAa.....", "......AAAa......", ".......Aa.......", "........a.......",
        "................", "................", "................", "................"};
    private static final String[] COAL = {
        "................", "................", "................", "....aaAAAa......", "...aAAAAAAa.....", "..aAAALAAAAa....",
        "..aAALLAAAAAa...", "..aAAAAAAAAAa...", "..aAAAAAAAAa....", "...aAAAAAAa.....", "....aaaaaa......", "................",
        "................", "................", "................", "................"};
    private static final String[] TABLE = {
        "................", ".LLLLLLLLLLLLLL.", ".LAAAAAAAAAAAAa.", ".LAaAaAaAaAaAAa.", ".aaaaaaaaaaaaaa.", ".HHHHHHHHHHHHHH.",
        ".HhhhhhHHhhhhhH.", ".HhGGGhHHhhhhhH.", ".HhGhGhHHhhhhhH.", ".HhGGGhHHhhhhhH.", ".HhhhhhHHhhhhhH.", ".HHHHHHHHHHHHHH.",
        ".HH..........HH.", ".HH..........HH.", ".HH..........HH.", "................"};
    private static final String[] CHEST = {
        "................", ".hhhhhhhhhhhhhh.", ".hLAAAAAAAAAAAh.", ".hAAAAAAAAAAAAh.", ".hAAAAAAAAAAAAh.", ".hAAAAAAAAAAAAh.",
        ".hhhhhhhGGhhhhh.", ".hAAAAAAGGAAAAh.", ".hAAAAAAaaAAAAh.", ".hAAAAAAAAAAAAh.", ".hAaAAAAAAAAaAh.", ".hAAAAAAAAAAAAh.",
        ".hAAAAAAAAAAAAh.", ".hAaAAAAAAAAaAh.", ".hhhhhhhhhhhhhh.", "................"};
    private static final String[] BED = {
        "................", "................", "................", "................", "..LLL...........", ".LWWWLAAAAAAAAA.", ".LWWWLAAAAAAAAa.",
        ".LLLLLAAAAAAAAa.", ".HHHHHHHHHHHHHH.", ".HhhhhhhhhhhhhH.", ".HH..........HH.", ".HH..........HH.", "................",
        "................", "................", "................"};
    private static final String[] HELMET = {
        "................", "................", "................", "................", ".....LLLLLL.....", "....LAAAAAAL....",
        "...LAAAAAAAAa...", "...AAAAAAAAAa...", "...AAAAAAAAAa...", "...AAa....aAa...", "...AAa....aAa...", "...aaa....aaa...",
        "................", "................", "................", "................"};
    private static final String[] CHESTPLATE = {
        "................", "................", "..LLL......LLL..", ".LAAAL....LAAAa.", ".LAAAAaaaaAAAAa.", ".LAAAAAAAAAAAAa.",
        ".aAAAAAAAAAAAAa.", ".aAa.AAAAAAa.Aa.", ".aaa.AAAAAAa.aa.", ".....AAAAAAa....", ".....AAAAAAa....", ".....AAAAAAa....",
        ".....AAAAAAa....", ".....aaaaaaa....", "................", "................"};
    private static final String[] LEGGINGS = {
        "................", "................", "...LLLLLLLLLL...", "...LAAAAAAAAa...", "...AAAAAAAAAa...", "...AAAa..aAAa...",
        "...AAAa..aAAa...", "...AAAa..aAAa...", "...AAAa..aAAa...", "...AAAa..aAAa...", "...AAAa..aAAa...", "...AAAa..aAAa...",
        "...aaaa..aaaa...", "................", "................", "................"};
    private static final String[] BOOTS = {
        "................", "................", "................", "................", "................", "...LAAa..LAAa...",
        "...LAAa..LAAa...", "...AAAa..AAAa...", "...AAAa..AAAa...", "..LAAAAa.LAAAAa.", "..AAAAAa.AAAAAa.", "..aaaaaa.aaaaaa.",
        "................", "................", "................", "................"};
    private static final String[] APPLE = {
        "................", ".......h........", "......hR........", ".....RR.........", "....AAAAAAAA....", "...AAAALAAAAa...",
        "..AAAALLAAAAAa..", "..AAAALAAAAAAa..", "..AAAAAAAAAAAa..", "..AAAAAAAAAAAa..", "..AAAAAAAAAAAa..", "...AAAAAAAAAa...",
        "....AAa..AAa....", "................", "................", "................"};
    private static final String[] BREAD = {
        "................", "................", "................", "................", ".........LLL....", "......LLLAAAL...",
        "...LLLAAAAAAAa..", "..LAAAAAAAAAAa..", "..AAAAAAAAAAa...", "..aAAAAAAAAa....", "...aaAAAAaa.....", ".....aaaa.......",
        "................", "................", "................", "................"};
    private static final String[] BEEF = {
        "................", "................", "................", "....aaaaaaa.....", "...aAAAAAAAa....", "..aAALLLLLAAa...",
        "..aALLLLLLLAa...", "..aALLLLLLLAa...", "..aAALLLLLAAa...", "...aAAAAAAAa....", "....aaaaaaa.....", "................",
        "................", "................", "................", "................"};

    private static int[] pal(int main, int dark, int light) {
        // index: 0 A, 1 a, 2 L, 3 G, 4 H, 5 h, 6 R, 7 W
        return new int[]{main, dark, light, 0xFF8A8A8A, 0xFF6B4A2A, 0xFF3E2A16, 0xFF3DA33D, 0xFFFFFFFF};
    }

    private static final Map<String, int[]> TIERS = new LinkedHashMap<>();
    static {
        TIERS.put("wooden", pal(0xFFA0703A, 0xFF6E4A24, 0xFFC89A5E));
        TIERS.put("stone", pal(0xFF8C8C8C, 0xFF5A5A5A, 0xFFB8B8B8));
        TIERS.put("iron", pal(0xFFE0E0E0, 0xFF9A9A9A, 0xFFFFFFFF));
        TIERS.put("golden", pal(0xFFF2D13B, 0xFFB08A10, 0xFFFFF4A0));
        TIERS.put("diamond", pal(0xFF4EE2E2, 0xFF1F9A9A, 0xFFB9FFFF));
        TIERS.put("leather", pal(0xFFA06030, 0xFF6B3F1D, 0xFFC98A52));
    }

    static final List<Item> ALL = new ArrayList<>();
    private static final Map<String, Item> BY_ID = new HashMap<>();

    private static Item add(Item i) { ALL.add(i); BY_ID.put(i.id, i); return i; }

    static {
        int[][] swordDamage = {{4}, {5}, {6}, {4}, {7}};
        String[] swordTiers = {"wooden", "stone", "iron", "golden", "diamond"};
        for (int i = 0; i < swordTiers.length; i++) add(new Item(swordTiers[i] + "_sword", Kind.SWORD, swordTiers[i], swordDamage[i][0], TIERS.get(swordTiers[i]), SWORD));
        for (String tier : swordTiers) {
            add(new Item(tier + "_pickaxe", Kind.PICKAXE, tier, (int) toolSpeed(tier), TIERS.get(tier), PICKAXE));
            add(new Item(tier + "_shovel", Kind.SHOVEL, tier, (int) toolSpeed(tier), TIERS.get(tier), SHOVEL));
            add(new Item(tier + "_axe", Kind.AXE, tier, (int) toolSpeed(tier), TIERS.get(tier), AXE));
        }
        String[] armorTiers = {"leather", "iron", "golden", "diamond"};
        int[][] armorPoints = {{1, 3, 2, 1}, {2, 6, 5, 2}, {2, 5, 3, 1}, {3, 8, 6, 3}};
        Kind[] kinds = {Kind.HELMET, Kind.CHESTPLATE, Kind.LEGGINGS, Kind.BOOTS};
        String[] names = {"helmet", "chestplate", "leggings", "boots"};
        String[][] shapes = {HELMET, CHESTPLATE, LEGGINGS, BOOTS};
        for (int t = 0; t < armorTiers.length; t++)
            for (int k = 0; k < 4; k++) add(new Item(armorTiers[t] + "_" + names[k], kinds[k], armorTiers[t], armorPoints[t][k], TIERS.get(armorTiers[t]), shapes[k]));
        add(new Item("oak_planks", Kind.TILE, "wood", 0, pal(0xFFA06A3A, 0xFF6B4320, 0xFFC99A5E), PLANKS));
        add(new Item("stick", Kind.MATERIAL, "wood", 0, pal(0xFF8B5A2B, 0xFF5E3A18, 0xFFB07A42), STICK));
        add(new Item("coal", Kind.MATERIAL, "coal", 0, pal(0xFF2B2B2B, 0xFF111111, 0xFF4A4A4A), COAL));
        add(new Item("copper_ingot", Kind.MATERIAL, "copper", 0, pal(0xFFC87533, 0xFF8A4A1E, 0xFFE8A26A), INGOT));
        add(new Item("iron_ingot", Kind.MATERIAL, "iron", 0, TIERS.get("iron"), INGOT));
        add(new Item("gold_ingot", Kind.MATERIAL, "golden", 0, TIERS.get("golden"), INGOT));
        add(new Item("diamond", Kind.MATERIAL, "diamond", 0, TIERS.get("diamond"), GEM));
        add(new Item("emerald", Kind.MATERIAL, "emerald", 0, pal(0xFF2ECC5A, 0xFF157A33, 0xFF8CF5A8), GEM));
        add(new Item("redstone", Kind.MATERIAL, "redstone", 0, pal(0xFFD41E1E, 0xFF7A0C0C, 0xFFFF6A6A), COAL));
        add(new Item("lapis", Kind.MATERIAL, "lapis", 0, pal(0xFF2B4FD6, 0xFF162A80, 0xFF7E9BFF), COAL));
        add(new Item("crafting_table", Kind.TILE, "wood", 0, pal(0xFFA06A3A, 0xFF6B4320, 0xFFC99A5E), TABLE));
        add(new Item("chest", Kind.TILE, "wood", 0, pal(0xFFA06A3A, 0xFF6B4320, 0xFFC99A5E), CHEST));
        add(new Item("bed", Kind.TILE, "wool", 0, pal(0xFFC43030, 0xFF7A1818, 0xFFE87070), BED));
        add(new Item("apple", Kind.FOOD, "food", 4, pal(0xFFD92B2B, 0xFF8A1515, 0xFFFF9F9F), APPLE));
        add(new Item("golden_apple", Kind.FOOD, "food", 4, pal(0xFFF2D13B, 0xFFB08A10, 0xFFFFF4A0), APPLE));
        add(new Item("bread", Kind.FOOD, "food", 5, pal(0xFFC98A3E, 0xFF8A5A22, 0xFFE8C078), BREAD));
        add(new Item("cooked_beef", Kind.FOOD, "food", 8, pal(0xFF7A4A2A, 0xFF4A2A14, 0xFFC97A7A), BEEF));
        add(new Item("porkchop", Kind.FOOD, "food", 3, pal(0xFFE89AA0, 0xFFB05A60, 0xFFF7C9CC), BEEF));
        add(new Item("rotten_flesh", Kind.FOOD, "food", 2, pal(0xFF7A5A3A, 0xFF4A3220, 0xFF9A8A5A), BEEF));
    }

    static Item byId(String id) {
        if (id == null) return null;
        Item i = BY_ID.get(id);
        if (i == null && id.startsWith("minecraft:")) i = BY_ID.get(id.substring(10));
        if (i == null && id.startsWith("modloader:")) i = BY_ID.get(id.substring(10));
        return i;
    }

    /** Item for a mod block ({@code namespace:path}); its icon is the block's side texture. */
    static Item registerBlockItem(String id, String label, byte[] iconPng) {
        Item existing = BY_ID.get(id);
        if (existing != null) return existing;
        Item item = new Item(id, Kind.TILE, "mod", 0, null, null);
        item.label = label;
        item.png = iconPng != null ? iconPng : new byte[0];
        return add(item);
    }

    static List<Item> ofKind(Kind... kinds) {
        List<Item> out = new ArrayList<>();
        for (Item i : ALL) for (Kind k : kinds) if (i.kind == k) { out.add(i); break; }
        return out;
    }

    static BufferedImage sprite(String[] rows, int[] palette) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16 && y < rows.length; y++) for (int x = 0; x < 16 && x < rows[y].length(); x++) {
            int c;
            switch (rows[y].charAt(x)) {
                case 'A': c = palette[0]; break; case 'a': c = palette[1]; break; case 'L': c = palette[2]; break;
                case 'G': c = palette[3]; break; case 'H': c = palette[4]; break; case 'h': c = palette[5]; break;
                case 'R': c = palette[6]; break; case 'W': c = palette[7]; break; default: c = 0;
            }
            img.setRGB(x, y, c);
        }
        return img;
    }

    static byte[] encode(BufferedImage img) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
