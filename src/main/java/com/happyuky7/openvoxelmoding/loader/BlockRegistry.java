package com.happyuky7.openvoxelmoding.loader;

import net.openvoxel.engine.rendering.Texture;
import net.openvoxel.engine.world.block.BlockType;

import java.util.*;

/**
 * Registry of ModLoader blocks, addressed by namespaced ids like Fabric/Minecraft
 * ({@code modloader:chest}, {@code mymod:ruby_block}). The engine's block enum cannot be
 * extended, so every registered block is stored in the world as a real engine "base" block
 * (collision, mining, lighting) plus a ModLoader entry with its id, which is what makes it
 * render and behave as the modded block. Entries are saved per world by {@link Tiles}.
 */
public final class BlockRegistry {
    private BlockRegistry() {}

    /** Behaviour family of a block. {@code CUBE} is a plain textured block (mods). */
    enum Kind { OAK_PLANKS, CRAFTING_TABLE, CHEST, BED, CUBE }

    static final class BlockDef {
        final String id;               // namespace:path
        final Kind kind;
        final BlockType base;          // engine block it is stored as
        final Items.Item item;         // item that places it / drops when broken
        final float height;
        final ModContext.BlockUseHandler onUse;
        final byte[] topPng, sidePng, bottomPng;
        private Texture top, side, bottom;

        BlockDef(String id, Kind kind, BlockType base, Items.Item item, float height, ModContext.BlockUseHandler onUse, byte[] topPng, byte[] sidePng, byte[] bottomPng) {
            this.id = id; this.kind = kind; this.base = base; this.item = item; this.height = height; this.onUse = onUse;
            this.topPng = topPng; this.sidePng = sidePng; this.bottomPng = bottomPng;
        }

        String namespace() { int c = id.indexOf(':'); return c < 0 ? "minecraft" : id.substring(0, c); }

        Texture top() { return top != null ? top : (top = tex(topPng)); }
        Texture side() { return side != null ? side : (side = tex(sidePng)); }
        Texture bottom() { return bottom != null ? bottom : (bottom = tex(bottomPng != null ? bottomPng : topPng)); }

        private static Texture tex(byte[] png) {
            if (png == null || png.length == 0) return null;
            try { return new Texture(png); } catch (Exception e) { return null; }
        }
    }

    private static final Map<String, BlockDef> BY_ID = new LinkedHashMap<>();
    private static final Map<Items.Item, BlockDef> BY_ITEM = new HashMap<>();

    static {
        builtin("modloader:oak_planks", Kind.OAK_PLANKS, BlockType.OAK_LOG, "oak_planks", 1f);
        builtin("modloader:crafting_table", Kind.CRAFTING_TABLE, BlockType.OAK_LOG, "crafting_table", 1f);
        builtin("modloader:chest", Kind.CHEST, BlockType.OAK_LOG, "chest", 0.875f);
        builtin("modloader:bed", Kind.BED, BlockType.SLAB, "bed", 0.5f);
    }

    private static void builtin(String id, Kind kind, BlockType base, String item, float height) {
        add(new BlockDef(id, kind, base, Items.byId(item), height, null, null, null, null));
    }

    private static void add(BlockDef def) {
        BY_ID.put(def.id, def);
        if (def.item != null) BY_ITEM.put(def.item, def);
    }

    static BlockDef get(String id) {
        if (id == null) return null;
        BlockDef d = BY_ID.get(id);
        if (d == null && id.indexOf(':') < 0) d = BY_ID.get("modloader:" + id);
        return d;
    }

    static BlockDef ofItem(Items.Item item) { return item == null ? null : BY_ITEM.get(item); }

    static Collection<BlockDef> all() { return Collections.unmodifiableCollection(BY_ID.values()); }

    /** Pre-1.1 save files used enum names (CHEST, CRAFTING_TABLE, BED). */
    static BlockDef legacy(String typeName) {
        if (typeName == null) return null;
        return get("modloader:" + typeName.toLowerCase(Locale.ROOT));
    }

    static boolean validId(String id) { return id != null && id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"); }

    /**
     * Registers a textured cube block for a mod. Returns null (and logs) if the id is invalid,
     * taken, or the base block is unknown.
     */
    static BlockDef registerCube(String id, String displayName, String baseBlock, byte[] top, byte[] side, byte[] bottom, ModContext.BlockUseHandler onUse) {
        if (!validId(id)) { System.err.println("[ModLoader] registerBlock: invalid id '" + id + "' (use namespace:path, lowercase)"); return null; }
        if (BY_ID.containsKey(id)) { System.err.println("[ModLoader] registerBlock: id already registered: " + id); return null; }
        BlockType base;
        try { base = BlockType.valueOf(baseBlock == null ? "STONE" : baseBlock.toUpperCase(Locale.ROOT)); }
        catch (Exception e) { System.err.println("[ModLoader] registerBlock: unknown base block '" + baseBlock + "' for " + id); return null; }
        if (base == BlockType.AIR || !base.isSolid()) { System.err.println("[ModLoader] registerBlock: base block must be solid: " + baseBlock); return null; }
        Items.Item item = Items.registerBlockItem(id, displayName, side != null ? side : top);
        BlockDef def = new BlockDef(id, Kind.CUBE, base, item, 1f, onUse, top, side != null ? side : top, bottom);
        add(def);
        return def;
    }
}
