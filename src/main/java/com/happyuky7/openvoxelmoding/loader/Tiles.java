package com.happyuky7.openvoxelmoding.loader;

import net.openvoxel.engine.VoxelEngine;
import net.openvoxel.engine.events.player.ClickEvent;
import net.openvoxel.engine.input.InputHandler;
import net.openvoxel.engine.rendering.Camera;
import net.openvoxel.engine.rendering.Renderer;
import net.openvoxel.engine.rendering.Texture;
import net.openvoxel.engine.ui.ChatHud;
import net.openvoxel.engine.ui.GLUIRenderer;
import net.openvoxel.engine.ui.UIManager;
import net.openvoxel.engine.util.Json;
import net.openvoxel.engine.world.AbstractBlock;
import net.openvoxel.engine.world.World;
import net.openvoxel.engine.world.block.BlockState;
import net.openvoxel.engine.world.block.BlockType;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Placed ModLoader blocks ({@link BlockRegistry}). Each one is a real engine block in the world
 * (its {@code base}: collision, mining, light) plus an entry here with the namespaced id that
 * makes it render and behave as the modded block.
 *
 * <p>Entries are saved per world, not per seed: singleplayer worlds keep them inside the world
 * folder ({@code <world>/modloader/blocks.json}, so they travel with the save), servers in
 * {@code instance/config/servers/<host>_<port>/blocks.json}. Entries whose base block is gone
 * (world reset, server rejected the placement, edited elsewhere) are dropped automatically.
 */
final class Tiles {
    private Tiles() {}

    static final class Tile {
        final BlockRegistry.BlockDef def; final int x, y, z; int facing;
        final Items.Stack[] chest;
        final Map<String, String> data = new LinkedHashMap<>();
        final long placedNs;
        Tile(BlockRegistry.BlockDef def, int x, int y, int z, int facing, long placedNs) {
            this.def = def; this.x = x; this.y = y; this.z = z; this.facing = facing; this.placedNs = placedNs;
            chest = def.kind == BlockRegistry.Kind.CHEST ? new Items.Stack[27] : null;
        }
    }

    private static final Map<Long, Tile> TILES = new HashMap<>();
    /** Entries whose id is not registered (mod not installed): kept as-is so they come back with the mod. */
    private static final List<Map<String, Object>> UNKNOWN = new ArrayList<>();
    private static boolean loaded, dirty;
    /** Set by the join hooks: the singleplayer world folder, or null for servers. */
    static volatile File worldDir;
    /** Set by the join hooks: {@code host:port} of the server. */
    static volatile String serverId = "";
    private static Path file;
    private static long lastValidateNs, lastSaveNs;
    static BlockRegistry.BlockDef pendingPlace; private static Items.Stack pendingHeld;
    static float sleepFade; private static long sleepNs;
    private static Field timeField;
    private static final Map<String, Texture> TEX = new HashMap<>();

    private static long key(int x, int y, int z) { return ((long) x & 0xFFFFFFL) << 40 | ((long) y & 0xFFFFL) << 24 | ((long) z & 0xFFFFFFL); }
    static Tile at(int x, int y, int z) { return TILES.get(key(x, y, z)); }
    static Collection<Tile> all() { return TILES.values(); }

    // ---- Placement & interaction -----------------------------------------------------

    /** Right click: open/use the block under the crosshair, or start placing a block item. Returns true when consumed. */
    static boolean onRightClick(VoxelEngine e, ClickEvent click) {
        if (!e.isWorldActive() || e.getCamera() == null) return false;
        InputHandler.Hit hit = hit(e);
        Tile tile = hit != null ? at(hit.x, hit.y, hit.z) : at(click.worldX, click.worldY, click.worldZ);
        if (tile != null && !GameMode.sneaking(e)) {
            switch (tile.def.kind) {
                case CRAFTING_TABLE: UIManager.get().openGUI(new Containers.CraftingScreen(e, 3)); return true;
                case CHEST: UIManager.get().openGUI(new Containers.ChestScreen(e, tile)); return true;
                case BED: sleep(e, tile); return true;
                default:
                    if (tile.def.onUse != null) {
                        try { if (tile.def.onUse.use(tile.x, tile.y, tile.z)) return true; }
                        catch (Throwable error) { System.err.println("[ModLoader] " + tile.def.id + " onUse: " + error); }
                    }
            }
        }
        Items.Stack held = BuiltinUI.held();
        BlockRegistry.BlockDef def = held != null && held.item != null ? BlockRegistry.ofItem(held.item) : null;
        if (def == null) return false;
        // Let the engine place the base block; applyEdit tells us where it landed.
        pendingPlace = def;
        pendingHeld = held;
        e.getCamera().setBlockInHand(new AbstractBlock(def.base));
        return false;
    }

    /** After the engine processed the click: put the hand back to "item" (no block). */
    static void finishPlace(VoxelEngine e) {
        if (pendingPlace == null) return;
        pendingPlace = null;
        pendingHeld = null;
        BuiltinUI.select(e, BuiltinUI.selected);
    }

    /** Called from the applyEdit hook when the base block got placed. */
    static void register(VoxelEngine e, int x, int y, int z) {
        if (pendingPlace == null) return;
        int facing = e.getCamera() != null ? Math.floorMod(Math.round(e.getCamera().getYaw() / 90f), 4) : 0;
        TILES.put(key(x, y, z), new Tile(pendingPlace, x, y, z, facing, System.nanoTime()));
        if (!Settings.isCreative() && pendingHeld != null && --pendingHeld.count <= 0) BuiltinUI.HOTBAR[BuiltinUI.selected] = null;
        BuiltinUI.sound().play("place");
        save();
    }

    /** Called when any block is broken; returns the block item to drop (chest contents are dropped too). */
    static Items.Stack onBlockBroken(int x, int y, int z) {
        Tile tile = TILES.remove(key(x, y, z));
        if (tile == null) return null;
        if (tile.chest != null) for (Items.Stack s : tile.chest) if (s != null) Drops.spawn(s, x, y, z);
        save();
        return tile.def.item != null ? Items.Stack.of(tile.def.item) : null;
    }

    private static void sleep(VoxelEngine e, Tile bed) {
        Renderer r = e.getRenderer();
        if (r == null) return;
        if (r.getDayFactor() > 0.35f) { ChatHud.addLine(Lang.t("bed.day")); return; }
        try {
            if (timeField == null) { timeField = Renderer.class.getDeclaredField("timeOfDay01"); timeField.setAccessible(true); }
            sleepFade = 1f;
            sleepNs = System.nanoTime();
            e.setSpawnPoint(bed.x + 0.5, bed.y + 1, bed.z + 0.5);
            Field f = timeField;
            new Thread(() -> {
                try { Thread.sleep(1200); } catch (InterruptedException ignored) { }
                net.openvoxel.client.ClientMain.runOnMainThread(() -> { try { f.setFloat(r, 0.02f); } catch (Exception ignored) { } ChatHud.addLine(Lang.t("bed.wake")); });
            }, "ModLoader-sleep").start();
        } catch (Exception error) { ChatHud.addLine("&c" + error.getMessage()); }
    }

    static void tick(VoxelEngine e, float dt) {
        if (sleepFade > 0) { float age = (System.nanoTime() - sleepNs) / 1e9f; sleepFade = age < 1.2f ? Math.min(1f, age / 0.4f) : Math.max(0f, 1f - (age - 1.2f) / 0.8f); if (age > 2f) sleepFade = 0; }
        long now = System.nanoTime();
        if (loaded && e.isWorldActive() && now - lastValidateNs > 1_000_000_000L) { lastValidateNs = now; validate(e); }
        if (dirty && now - lastSaveNs > 2_000_000_000L) save();
    }

    static void renderHud(int w, int h) { if (sleepFade > 0) GLUIRenderer.drawPanel(0, 0, w, h, 0, 0, 0, sleepFade); }

    /**
     * Drops entries whose base block is no longer there. Only checks chunks that have data
     * (a neighbour is not air) and skips blocks placed in the last few seconds, so a placement
     * still travelling to the server is not lost.
     */
    private static void validate(VoxelEngine e) {
        World w = e.getWorld();
        if (w == null || TILES.isEmpty()) return;
        long now = System.nanoTime();
        List<Long> ghosts = new ArrayList<>();
        for (Map.Entry<Long, Tile> en : TILES.entrySet()) {
            Tile t = en.getValue();
            if (now - t.placedNs < 5_000_000_000L) continue;
            try {
                if (w.getChunkIfLoaded(Math.floorDiv(t.x, 16), Math.floorDiv(t.z, 16)) == null) continue;
                int type = BlockState.typeId(w.getState(t.x, t.y, t.z));
                if (type == t.def.base.getId()) continue;
                if (!hasData(w, t.x, t.y, t.z)) continue;
                ghosts.add(en.getKey());
            } catch (Exception ignored) { }
        }
        if (ghosts.isEmpty()) return;
        for (Long k : ghosts) { Tile t = TILES.remove(k); System.out.println("[ModLoader] blocks: dropped " + t.def.id + " at " + t.x + "," + t.y + "," + t.z + " (base block is gone)"); }
        save();
    }

    private static boolean hasData(World w, int x, int y, int z) {
        int[][] n = {{0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -2, 0}, {0, -3, 0}};
        for (int[] d : n) if (BlockState.typeId(w.getState(x + d[0], y + d[1], z + d[2])) != BlockType.AIR.getId()) return true;
        return false;
    }

    // ---- Persistence ---------------------------------------------------------------

    private static Path resolveFile() {
        File dir = worldDir;
        if (dir != null) return dir.toPath().resolve("modloader").resolve("blocks.json");
        String id = serverId == null || serverId.isEmpty() ? "unknown" : serverId;
        return Agent.instanceDir().resolve("config").resolve("servers").resolve(id.replaceAll("[^A-Za-z0-9._-]", "_")).resolve("blocks.json");
    }

    private static Path legacyFile(VoxelEngine e) {
        long seed = e.getWorld() != null ? e.getWorld().getSeed() : 0;
        return Agent.instanceDir().resolve("config").resolve("tiles").resolve(seed + ".json");
    }

    static void load(VoxelEngine e) {
        TILES.clear();
        UNKNOWN.clear();
        loaded = true; dirty = false;
        file = resolveFile();
        Path source = file;
        boolean migrated = false;
        if (!Files.isRegularFile(source)) {
            Path legacy = legacyFile(e);
            if (Files.isRegularFile(legacy)) { source = legacy; migrated = true; }
            else { System.out.println("[ModLoader] blocks: new file " + file); return; }
        }
        try {
            Object root = Json.parse(Files.readString(source, StandardCharsets.UTF_8));
            List<Object> arr = Json.arr(root);
            Map<String, Object> obj = Json.obj(root);
            if (arr == null && obj != null) arr = Json.arr(obj.get("blocks"));
            if (arr == null) return;
            long placed = System.nanoTime() - 60_000_000_000L;   // loaded entries are validated right away
            int unknown = 0;
            for (Object o : arr) {
                Map<String, Object> m = Json.obj(o);
                if (m == null) continue;
                String id = Json.str(m.get("id"));
                BlockRegistry.BlockDef def = id != null ? BlockRegistry.get(id) : BlockRegistry.legacy(Json.str(m.get("type")));
                if (def == null) { unknown++; if (id != null) UNKNOWN.add(m); continue; }
                Tile t = new Tile(def, (int) Json.num(m.get("x"), 0), (int) Json.num(m.get("y"), 0), (int) Json.num(m.get("z"), 0), (int) Json.num(m.get("facing"), 0), placed);
                List<Object> items = Json.arr(m.get("items"));
                if (t.chest != null && items != null) for (int i = 0; i < Math.min(27, items.size()); i++) t.chest[i] = parseStack(Json.str(items.get(i)));
                Map<String, Object> data = Json.obj(m.get("data"));
                if (data != null) for (Map.Entry<String, Object> d : data.entrySet()) if (d.getValue() != null) t.data.put(d.getKey(), String.valueOf(d.getValue()));
                TILES.put(key(t.x, t.y, t.z), t);
            }
            System.out.println("[ModLoader] blocks: loaded " + TILES.size() + " from " + source + (unknown > 0 ? " (" + unknown + " with unknown ids kept for later: mod missing?)" : ""));
            if (migrated) { System.out.println("[ModLoader] blocks: migrated legacy seed file to " + file); save(); }
        } catch (Exception error) { System.err.println("[ModLoader] blocks load: " + error); }
    }

    static void unload() { if (loaded) save(); TILES.clear(); UNKNOWN.clear(); loaded = false; file = null; }

    static void save() {
        if (!loaded || file == null) return;
        dirty = false; lastSaveNs = System.nanoTime();
        StringBuilder sb = new StringBuilder("{\"format\":2,\"blocks\":[");
        boolean first = true;
        for (Tile t : TILES.values()) {
            if (!first) sb.append(','); first = false;
            sb.append("{\"id\":").append(q(t.def.id)).append(",\"x\":").append(t.x).append(",\"y\":").append(t.y).append(",\"z\":").append(t.z).append(",\"facing\":").append(t.facing);
            if (t.chest != null) { sb.append(",\"items\":["); for (int i = 0; i < 27; i++) { if (i > 0) sb.append(','); sb.append(q(encodeStack(t.chest[i]))); } sb.append(']'); }
            if (!t.data.isEmpty()) { sb.append(",\"data\":{"); boolean f = true; for (Map.Entry<String, String> d : t.data.entrySet()) { if (!f) sb.append(','); f = false; sb.append(q(d.getKey())).append(':').append(q(d.getValue())); } sb.append('}'); }
            sb.append('}');
        }
        for (Map<String, Object> raw : UNKNOWN) { if (!first) sb.append(','); first = false; json(sb, raw); }
        sb.append("]}");
        try { Files.createDirectories(file.getParent()); Files.writeString(file, sb.toString(), StandardCharsets.UTF_8); }
        catch (Exception error) { System.err.println("[ModLoader] blocks save: " + error); }
    }

    static void markDirty() { dirty = true; }

    @SuppressWarnings("unchecked")
    private static void json(StringBuilder sb, Object v) {
        if (v == null) sb.append("null");
        else if (v instanceof String) sb.append(q((String) v));
        else if (v instanceof Number) { double d = ((Number) v).doubleValue(); if (d == Math.rint(d) && Math.abs(d) < 1e15) sb.append((long) d); else sb.append(d); }
        else if (v instanceof Boolean) sb.append(v);
        else if (v instanceof Map) { sb.append('{'); boolean f = true; for (Map.Entry<String, Object> en : ((Map<String, Object>) v).entrySet()) { if (!f) sb.append(','); f = false; sb.append(q(en.getKey())).append(':'); json(sb, en.getValue()); } sb.append('}'); }
        else if (v instanceof List) { sb.append('['); boolean f = true; for (Object o : (List<Object>) v) { if (!f) sb.append(','); f = false; json(sb, o); } sb.append(']'); }
        else sb.append(q(String.valueOf(v)));
    }

    private static String q(String s) { return "\"" + (s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")) + "\""; }

    /** {@code item:<id>:<count>} / {@code block:<NAME>:<count>}; ids may contain ':' (namespaces). */
    static String encodeStack(Items.Stack s) { return s == null ? "" : (s.isBlock() ? "block:" + s.block.name() : "item:" + s.item.id) + ":" + s.count; }

    static Items.Stack parseStack(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            int first = s.indexOf(':'), last = s.lastIndexOf(':');
            String kind = s.substring(0, first), id = s.substring(first + 1, last);
            int count = Integer.parseInt(s.substring(last + 1));
            return kind.equals("block") ? Items.Stack.of(BlockType.valueOf(id), count) : Items.Stack.of(Items.byId(id), count);
        } catch (Exception e) { return null; }
    }

    // ---- Rendering ---------------------------------------------------------------

    private static final String[] TABLE_TOP = {
        "LLLLLLLLLLLLLLLL", "LAAAAAAALAAAAAAL", "LAaaaaaALAaaaaAL", "LAaAAAaALAaAAaAL", "LAaaaaaALAaaaaAL", "LAAAAAAALAAAAAAL", "LLLLLLLLLLLLLLLL", "LAAAAAAALAAAAAAL",
        "LAaaaaaALAaaaaAL", "LAaAAAaALAaAAaAL", "LAaaaaaALAaaaaAL", "LAAAAAAALAAAAAAL", "LLLLLLLLLLLLLLLL", "LAAAAAAALAAAAAAL", "LAaaaaaALAaaaaAL", "LLLLLLLLLLLLLLLL"};
    private static final String[] PLANKS_BLOCK = {
        "hhhhhhhhhhhhhhhh", "hAAAAAAAAAAAAAAh", "hAaaaaaaaaaaaaAh", "hAAAAAAAAAAAAAAh", "hhhhhhhhhhhhhhhh", "hAAAAAAAAAAAAAAh", "hAaaaaaaaaaaaaAh", "hAAAAAAAAAAAAAAh",
        "hhhhhhhhhhhhhhhh", "hAAAAAAAAAAAAAAh", "hAaaaaaaaaaaaaAh", "hAAAAAAAAAAAAAAh", "hhhhhhhhhhhhhhhh", "hAAAAAAAAAAAAAAh", "hAaaaaaaaaaaaaAh", "hhhhhhhhhhhhhhhh"};
    private static final String[] TABLE_SIDE = {
        "LLLLLLLLLLLLLLLL", "AAAAAAAAAAAAAAAA", "aaaaaaaaaaaaaaaa", "HHHHHHHHHHHHHHHH", "HhGGGhHHhhGGGhhH", "HhGhGhHHhhGhGhhH", "HhGGGhHHhhGGGhhH", "HhhhhhHHhhhhhhhH",
        "HHHHHHHHHHHHHHHH", "HhhhhhhhhhhhhhhH", "HhhhhhhhhhhhhhhH", "HHHHHHHHHHHHHHHH", "HH............HH", "HH............HH", "HH............HH", "HH............HH"};
    private static final String[] CHEST_SIDE = {
        "hhhhhhhhhhhhhhhh", "hAAAAAAAAAAAAAAh", "hAAAAAAAAAAAAAAh", "hAAAAAAAAAAAAAAh", "hAaAAAAAAAAAAaAh", "hhhhhhhhhhhhhhhh", "hAAAAAAAAAAAAAAh", "hAAAAAAAAAAAAAAh",
        "hAaAAAAAAAAAAaAh", "hAAAAAAAAAAAAAAh", "hAAAAAAAAAAAAAAh", "hAaAAAAAAAAAAaAh", "hAAAAAAAAAAAAAAh", "hAAAAAAAAAAAAAAh", "hAAAAAAAAAAAAAAh", "hhhhhhhhhhhhhhhh"};
    private static final String[] CHEST_FRONT = {
        "hhhhhhhhhhhhhhhh", "hAAAAAAAAAAAAAAh", "hAAAAAAAAAAAAAAh", "hAAAAAAGGAAAAAAh", "hAaAAAAGGAAAAaAh", "hhhhhhhGGhhhhhhh", "hAAAAAAGGAAAAAAh", "hAAAAAAAaAAAAAAh",
        "hAaAAAAAAAAAAaAh", "hAAAAAAAAAAAAAAh", "hAAAAAAAAAAAAAAh", "hAaAAAAAAAAAAaAh", "hAAAAAAAAAAAAAAh", "hAAAAAAAAAAAAAAh", "hAAAAAAAAAAAAAAh", "hhhhhhhhhhhhhhhh"};
    private static final String[] CHEST_TOP = {
        "hhhhhhhhhhhhhhhh", "hAAAAAAAAAAAAAAh", "hAaaaaaaaaaaaaAh", "hAaAAAAAAAAAAaAh", "hAaAAAAAAAAAAaAh", "hAaAAAAAAAAAAaAh", "hAaAAAAAAAAAAaAh", "hAaAAAAGGAAAAaAh",
        "hAaAAAAGGAAAAaAh", "hAaAAAAAAAAAAaAh", "hAaAAAAAAAAAAaAh", "hAaAAAAAAAAAAaAh", "hAaAAAAAAAAAAaAh", "hAaaaaaaaaaaaaAh", "hAAAAAAAAAAAAAAh", "hhhhhhhhhhhhhhhh"};
    private static final String[] BED_TOP = {
        "WWWWWWWWWWWWWWWW", "WLLLLLWAAAAAAAAW", "WLLLLLWAAAAAAAAW", "WLLLLLWAAAAAAAAW", "WLLLLLWAaaaaaaAW", "WLLLLLWAaAAAAaAW", "WLLLLLWAaAAAAaAW", "WLLLLLWAaAAAAaAW",
        "WLLLLLWAaAAAAaAW", "WLLLLLWAaAAAAaAW", "WLLLLLWAaAAAAaAW", "WLLLLLWAaaaaaaAW", "WLLLLLWAAAAAAAAW", "WLLLLLWAAAAAAAAW", "WLLLLLWAAAAAAAAW", "WWWWWWWWWWWWWWWW"};
    private static final String[] BED_SIDE = {
        "AAAAAAAAAAAAAAAA", "AAAAAAAAAAAAAAAA", "aaaaaaaaaaaaaaaa", "HHHHHHHHHHHHHHHH", "HhhhhhhhhhhhhhhH", "HHHHHHHHHHHHHHHH", "HH............HH", "HH............HH",
        "................", "................", "................", "................", "................", "................", "................", "................"};

    private static Texture tex(String name, String[] rows, int[] palette) {
        Texture t = TEX.get(name);
        if (t == null) {
            try { t = new Texture(Items.encode(Items.sprite(rows, palette))); } catch (Exception e) { return null; }
            TEX.put(name, t);
        }
        return t;
    }

    private static int[] wood() { return new int[]{0xFFA06A3A, 0xFF6B4320, 0xFFC99A5E, 0xFF8A8A8A, 0xFF7A4A24, 0xFF5A3418, 0xFF3DA33D, 0xFFFFFFFF}; }
    private static int[] bed() { return new int[]{0xFFC43030, 0xFF8A1E1E, 0xFFF0F0F0, 0xFF8A8A8A, 0xFF7A4A24, 0xFF5A3418, 0xFF3DA33D, 0xFFE8E8E8}; }

    /** Draws every tile near the camera; matrices (projection + view) must already be loaded. */
    static void render(Renderer renderer, VoxelEngine e, Camera cam) {
        if (TILES.isEmpty()) return;
        Vector3d p = cam.getPosition();
        for (Tile t : TILES.values()) {
            double rx = t.x - p.x, ry = t.y - p.y - 1.6, rz = t.z - p.z;
            if (rx * rx + ry * ry + rz * rz > 48 * 48) continue;
            float bright = HandRenderer.brightnessAt(e, renderer, t.x + 0.5, t.y + 0.5, t.z + 0.5);
            GL11.glPushMatrix();
            GL11.glTranslatef((float) rx, (float) ry, (float) rz);
            switch (t.def.kind) {
                case OAK_PLANKS: box(tex("oak_planks", PLANKS_BLOCK, wood()), tex("oak_planks", PLANKS_BLOCK, wood()), tex("oak_planks", PLANKS_BLOCK, wood()), -0.005f, 1.01f, 1.01f, bright); break;
                case CRAFTING_TABLE: box(tex("table_top", TABLE_TOP, wood()), tex("table_side", TABLE_SIDE, wood()), tex("table_side", TABLE_SIDE, wood()), -0.005f, 1.01f, 1.01f, bright); break;
                case CHEST: {
                    Texture side = tex("chest_side", CHEST_SIDE, wood()), front = tex("chest_front", CHEST_FRONT, wood());
                    GL11.glTranslatef(0.5f, 0, 0.5f); GL11.glRotatef(t.facing * 90f, 0, 1, 0); GL11.glTranslatef(-0.5f, 0, -0.5f);
                    box(tex("chest_top", CHEST_TOP, wood()), side, front, -0.005f, 1.0f, 1.01f, bright);
                    break;
                }
                case BED: {
                    GL11.glTranslatef(0.5f, 0, 0.5f); GL11.glRotatef(t.facing * 90f, 0, 1, 0); GL11.glTranslatef(-0.5f, 0, -0.5f);
                    Texture top = tex("bed_top", BED_TOP, bed()), side = tex("bed_side", BED_SIDE, bed());
                    bedBox(top, side, bright);
                    break;
                }
                case CUBE: {
                    Texture top = t.def.top(), side = t.def.side();
                    if (top != null) box(top, side != null ? side : top, side != null ? side : top, -0.005f, 1.01f, 1.01f, bright);
                    break;
                }
            }
            GL11.glPopMatrix();
        }
    }

    /** Axis-aligned box from (inset, 0, inset) to (1-inset, h, 1-inset), scaled by s around the block centre. */
    private static void box(Texture top, Texture side, Texture front, float inset, float h, float s, float bright) {
        float x0 = inset, x1 = 1 - inset, z0 = inset, z1 = 1 - inset, y0 = -0.005f, y1 = h;
        GL11.glPushMatrix();
        GL11.glTranslatef(0.5f, 0, 0.5f); GL11.glScalef(s, 1, s); GL11.glTranslatef(-0.5f, 0, -0.5f);
        GL11.glColor4f(bright, bright, bright, 1f);
        quad(top, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, bright);            // top
        quad(side, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, bright * 0.8f);    // +z
        quad(front, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, bright * 0.8f);   // -z (front)
        quad(side, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, bright * 0.6f);    // +x
        quad(side, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, bright * 0.6f);    // -x
        quad(side, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, bright * 0.5f);    // bottom
        GL11.glPopMatrix();
    }

    /** Two blocks long (towards -z before rotation), 9/16 high. */
    private static void bedBox(Texture top, Texture side, float bright) {
        float x0 = 0f, x1 = 1f, z0 = -1f, z1 = 1f, y0 = -0.005f, y1 = 0.5625f;
        quad(top, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, bright);
        quad(side, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, bright * 0.8f);
        quad(side, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, bright * 0.8f);
        quad(side, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, bright * 0.6f);
        quad(side, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, bright * 0.6f);
    }

    private static void quad(Texture t, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz, float shade) {
        if (t == null) return;
        t.bind();
        GL11.glColor4f(shade, shade, shade, 1f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0); GL11.glVertex3f(ax, ay, az);
        GL11.glTexCoord2f(1, 0); GL11.glVertex3f(bx, by, bz);
        GL11.glTexCoord2f(1, 1); GL11.glVertex3f(cx, cy, cz);
        GL11.glTexCoord2f(0, 1); GL11.glVertex3f(dx, dy, dz);
        GL11.glEnd();
    }

    static InputHandler.Hit hit(VoxelEngine e) { return e.getCamera() != null && e.getCamera().getInputHandler() != null ? e.getCamera().getInputHandler().getHoverHit() : null; }
}
