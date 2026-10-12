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
import net.openvoxel.engine.world.block.BlockType;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Client-side "tile" blocks the engine does not know about: crafting table, chest and bed.
 * Each one sits on a real engine block (so collision works) and is drawn over it; the tile
 * registry is saved per world seed in {@code client/instance/config/tiles/<seed>.json}.
 */
final class Tiles {
    private Tiles() {}

    enum Type {
        CRAFTING_TABLE("crafting_table", BlockType.OAK_LOG, 1f), CHEST("chest", BlockType.OAK_LOG, 0.875f), BED("bed", BlockType.SLAB, 0.5f);
        final String item; final BlockType base; final float height;
        Type(String item, BlockType base, float height) { this.item = item; this.base = base; this.height = height; }
        static Type of(Items.Item item) { if (item == null) return null; for (Type t : values()) if (t.item.equals(item.id)) return t; return null; }
    }

    static final class Tile {
        final Type type; final int x, y, z; int facing;
        final Items.Stack[] chest;
        Tile(Type type, int x, int y, int z, int facing) { this.type = type; this.x = x; this.y = y; this.z = z; this.facing = facing; chest = type == Type.CHEST ? new Items.Stack[27] : null; }
    }

    private static final Map<Long, Tile> TILES = new HashMap<>();
    private static long seed; private static boolean loaded;
    static Type pendingPlace; private static Items.Stack pendingHeld;
    static float sleepFade; private static long sleepNs;
    private static Field timeField;
    private static final Map<String, Texture> TEX = new HashMap<>();

    private static long key(int x, int y, int z) { return ((long) x & 0xFFFFFFL) << 40 | ((long) y & 0xFFFFL) << 24 | ((long) z & 0xFFFFFFL); }
    static Tile at(int x, int y, int z) { return TILES.get(key(x, y, z)); }
    static Collection<Tile> all() { return TILES.values(); }

    // ---- Placement & interaction -----------------------------------------------------

    /** Right click: open the tile under the crosshair, or start placing a tile item. Returns true when consumed. */
    static boolean onRightClick(VoxelEngine e, ClickEvent click) {
        if (!e.isWorldActive() || e.getCamera() == null) return false;
        Tile tile = at(click.worldX, click.worldY, click.worldZ);
        if (tile != null && !GameMode.sneaking(e)) {
            switch (tile.type) {
                case CRAFTING_TABLE: UIManager.get().openGUI(new Containers.CraftingScreen(e, 3)); break;
                case CHEST: UIManager.get().openGUI(new Containers.ChestScreen(e, tile)); break;
                case BED: sleep(e, tile); break;
            }
            return true;
        }
        Items.Stack held = BuiltinUI.held();
        Type type = held != null && held.item != null ? Type.of(held.item) : null;
        if (type == null) return false;
        // Let the engine place the base block; applyEdit tells us where it landed.
        pendingPlace = type;
        pendingHeld = held;
        e.getCamera().setBlockInHand(new AbstractBlock(type.base));
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
        TILES.put(key(x, y, z), new Tile(pendingPlace, x, y, z, facing));
        if (!Settings.isCreative() && pendingHeld != null && --pendingHeld.count <= 0) BuiltinUI.HOTBAR[BuiltinUI.selected] = null;
        BuiltinUI.sound().play("place");
        save();
    }

    /** Called when any block is broken; returns the tile item to drop (chest contents are dropped too). */
    static Items.Stack onBlockBroken(int x, int y, int z) {
        Tile tile = TILES.remove(key(x, y, z));
        if (tile == null) return null;
        if (tile.chest != null) for (Items.Stack s : tile.chest) if (s != null) Drops.spawn(s, x, y, z);
        save();
        return Items.Stack.of(Items.byId(tile.type.item));
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

    static void tick(float dt) { if (sleepFade > 0) { float age = (System.nanoTime() - sleepNs) / 1e9f; sleepFade = age < 1.2f ? Math.min(1f, age / 0.4f) : Math.max(0f, 1f - (age - 1.2f) / 0.8f); if (age > 2f) sleepFade = 0; } }

    static void renderHud(int w, int h) { if (sleepFade > 0) GLUIRenderer.drawPanel(0, 0, w, h, 0, 0, 0, sleepFade); }

    // ---- Persistence ---------------------------------------------------------------

    private static Path file() { return Agent.instanceDir().resolve("config").resolve("tiles").resolve(seed + ".json"); }

    static void load(VoxelEngine e) {
        TILES.clear();
        loaded = true;
        seed = e.getWorld() != null ? e.getWorld().getSeed() : 0;
        try {
            if (!Files.isRegularFile(file())) return;
            List<Object> arr = Json.arr(Json.parse(Files.readString(file(), StandardCharsets.UTF_8)));
            if (arr == null) return;
            for (Object o : arr) {
                Map<String, Object> m = Json.obj(o);
                if (m == null) continue;
                Type type = Type.valueOf(Json.str(m.get("type")));
                Tile t = new Tile(type, (int) Json.num(m.get("x"), 0), (int) Json.num(m.get("y"), 0), (int) Json.num(m.get("z"), 0), (int) Json.num(m.get("facing"), 0));
                List<Object> items = Json.arr(m.get("items"));
                if (t.chest != null && items != null) for (int i = 0; i < Math.min(27, items.size()); i++) t.chest[i] = parseStack(Json.str(items.get(i)));
                TILES.put(key(t.x, t.y, t.z), t);
            }
            System.out.println("[ModLoader] loaded " + TILES.size() + " tile(s) for seed " + seed);
        } catch (Exception error) { System.err.println("[ModLoader] tiles load: " + error); }
    }

    static void unload() { if (loaded) save(); TILES.clear(); loaded = false; }

    static void save() {
        if (!loaded) return;
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Tile t : TILES.values()) {
            if (!first) sb.append(','); first = false;
            sb.append("{\"type\":\"").append(t.type.name()).append("\",\"x\":").append(t.x).append(",\"y\":").append(t.y).append(",\"z\":").append(t.z).append(",\"facing\":").append(t.facing);
            if (t.chest != null) { sb.append(",\"items\":["); for (int i = 0; i < 27; i++) { if (i > 0) sb.append(','); sb.append('"').append(encodeStack(t.chest[i])).append('"'); } sb.append(']'); }
            sb.append('}');
        }
        sb.append(']');
        try { Files.createDirectories(file().getParent()); Files.writeString(file(), sb.toString(), StandardCharsets.UTF_8); }
        catch (Exception error) { System.err.println("[ModLoader] tiles save: " + error); }
    }

    static String encodeStack(Items.Stack s) { return s == null ? "" : (s.isBlock() ? "block:" + s.block.name() : "item:" + s.item.id) + ":" + s.count; }

    static Items.Stack parseStack(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            String[] p = s.split(":");
            int count = Integer.parseInt(p[2]);
            return p[0].equals("block") ? Items.Stack.of(BlockType.valueOf(p[1]), count) : Items.Stack.of(Items.byId(p[1]), count);
        } catch (Exception e) { return null; }
    }

    // ---- Rendering ---------------------------------------------------------------

    private static final String[] TABLE_TOP = {
        "LLLLLLLLLLLLLLLL", "LAAAAAAALAAAAAAL", "LAaaaaaALAaaaaAL", "LAaAAAaALAaAAaAL", "LAaaaaaALAaaaaAL", "LAAAAAAALAAAAAAL", "LLLLLLLLLLLLLLLL", "LAAAAAAALAAAAAAL",
        "LAaaaaaALAaaaaAL", "LAaAAAaALAaAAaAL", "LAaaaaaALAaaaaAL", "LAAAAAAALAAAAAAL", "LLLLLLLLLLLLLLLL", "LAAAAAAALAAAAAAL", "LAaaaaaALAaaaaAL", "LLLLLLLLLLLLLLLL"};
    private static final String[] TABLE_SIDE = {
        "LLLLLLLLLLLLLLLL", "AAAAAAAAAAAAAAAA", "aaaaaaaaaaaaaaaa", "HHHHHHHHHHHHHHHH", "HhGGGhHHhhGGGhhH", "HhGhGhHHhhGhGhhH", "HhGGGhHHhhGGGhhH", "HhhhhhHHhhhhhhhH",
        "HHHHHHHHHHHHHHHH", "HhhhhhhhhhhhhhhH", "HhhhhhhhhhhhhhhH", "HHHHHHHHHHHHHHHH", "HH............HH", "HH............HH", "HH............HH", "HH............HH"};
    private static final String[] CHEST_SIDE = {
        "HHHHHHHHHHHHHHHH", "HhhhhhhhhhhhhhhH", "HhhhhhhhhhhhhhhH", "HhhhhhhhhhhhhhhH", "HHHHHHHHHHHHHHHH", "HhhhhhhhhhhhhhhH", "HhhhhhhhhhhhhhhH", "HhhhhhhhhhhhhhhH",
        "HhhhhhhhhhhhhhhH", "HhhhhhhhhhhhhhhH", "HhhhhhhhhhhhhhhH", "HhhhhhhhhhhhhhhH", "HhhhhhhhhhhhhhhH", "HhhhhhhhhhhhhhhH", "HhhhhhhhhhhhhhhH", "HHHHHHHHHHHHHHHH"};
    private static final String[] CHEST_FRONT = {
        "HHHHHHHHHHHHHHHH", "HhhhhhhhhhhhhhhH", "HhhhhhhhhhhhhhhH", "HhhhhhhGGhhhhhhH", "HHHHHHHGGHHHHHHH", "HhhhhhhGGhhhhhhH", "HhhhhhhhhhhhhhhH", "HhhhhhhhhhhhhhhH",
        "HhhhhhhhhhhhhhhH", "HhhhhhhhhhhhhhhH", "HhhhhhhhhhhhhhhH", "HhhhhhhhhhhhhhhH", "HhhhhhhhhhhhhhhH", "HhhhhhhhhhhhhhhH", "HhhhhhhhhhhhhhhH", "HHHHHHHHHHHHHHHH"};
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
            switch (t.type) {
                case CRAFTING_TABLE: box(tex("table_top", TABLE_TOP, wood()), tex("table_side", TABLE_SIDE, wood()), tex("table_side", TABLE_SIDE, wood()), -0.005f, 1.01f, 1.01f, bright); break;
                case CHEST: {
                    Texture side = tex("chest_side", CHEST_SIDE, wood()), front = tex("chest_front", CHEST_FRONT, wood());
                    GL11.glTranslatef(0.5f, 0, 0.5f); GL11.glRotatef(t.facing * 90f, 0, 1, 0); GL11.glTranslatef(-0.5f, 0, -0.5f);
                    box(side, side, front, 0.0625f, 0.875f, 1.01f, bright);
                    break;
                }
                case BED: {
                    GL11.glTranslatef(0.5f, 0, 0.5f); GL11.glRotatef(t.facing * 90f, 0, 1, 0); GL11.glTranslatef(-0.5f, 0, -0.5f);
                    Texture top = tex("bed_top", BED_TOP, bed()), side = tex("bed_side", BED_SIDE, bed());
                    bedBox(top, side, bright);
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
