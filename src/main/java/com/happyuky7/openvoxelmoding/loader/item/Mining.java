package com.happyuky7.openvoxelmoding.loader.item;

import com.happyuky7.openvoxelmoding.loader.NativeBlocks;




import com.happyuky7.openvoxelmoding.loader.core.Settings;
import com.happyuky7.openvoxelmoding.loader.gameplay.Mobs;
import com.happyuky7.openvoxelmoding.loader.ui.BuiltinUI;
import com.happyuky7.openvoxelmoding.loader.ui.Mc;
import com.happyuky7.openvoxelmoding.loader.world.Tiles;

import net.openvoxel.engine.VoxelEngine;
import net.openvoxel.engine.events.player.ClickEvent;
import net.openvoxel.engine.input.InputHandler;
import net.openvoxel.engine.ui.GLUIRenderer;
import net.openvoxel.engine.ui.UIManager;
import net.openvoxel.engine.world.block.BlockState;
import net.openvoxel.engine.world.block.BlockType;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.Map;

/**
 * Survival block breaking: hold the left button, break time depends on the block and the tool,
 * broken blocks drop into the inventory and placing consumes stacks.
 */
public final class Mining {
    private Mining() {}

    /** Set while the loader itself fires the break click so the applyEdit hook lets it through. */
    public static boolean approved;
    static float progress;
    private static int tx, ty, tz;
    private static boolean hadTarget;
    private static String pickupText = "";
    private static long pickupNs;

    private static final Map<BlockType, float[]> HARDNESS = new EnumMap<>(BlockType.class); // {seconds-by-hand-base, requiredTool(0 none,1 pick,2 shovel,3 axe), needsToolForDrop}

    private static void h(BlockType t, float hardness, int tool, boolean needsTool) { HARDNESS.put(t, new float[]{hardness, tool, needsTool ? 1 : 0}); }

    static {
        h(BlockType.DIRT, 0.5f, 2, false); h(BlockType.GRASS, 0.6f, 2, false); h(BlockType.SNOWY_GRASS, 0.6f, 2, false);
        h(BlockType.SAND, 0.5f, 2, false); h(BlockType.GRAVEL, 0.6f, 2, false); h(BlockType.SNOW, 0.2f, 2, false);
        h(BlockType.STONE, 1.5f, 1, true); h(BlockType.SLAB, 2f, 1, true); h(BlockType.STAIR, 2f, 1, true);
        h(BlockType.COAL_ORE, 3f, 1, true); h(BlockType.COPPER_ORE, 3f, 1, true); h(BlockType.IRON_ORE, 3f, 1, true);
        h(BlockType.GOLD_ORE, 3f, 1, true); h(BlockType.REDSTONE_ORE, 3f, 1, true); h(BlockType.LAPIS_ORE, 3f, 1, true);
        h(BlockType.DIAMOND_ORE, 3f, 1, true); h(BlockType.EMERALD_ORE, 3f, 1, true);
        h(BlockType.OAK_LOG, 2f, 3, false); h(BlockType.PUMPKIN, 1f, 3, false); h(BlockType.DOOR, 3f, 3, false); h(BlockType.DOOR_UPPER, 3f, 3, false);
        h(BlockType.OAK_LEAVES, 0.2f, 0, false); h(BlockType.GLASS, 0.3f, 0, false);
        h(BlockType.TORCH, 0f, 0, false); h(BlockType.FLOWER, 0f, 0, false); h(BlockType.TALLGRASS, 0f, 0, false);
        h(BlockType.BEDROCK, -1f, 1, true);
        for (NativeBlocks.Def d : NativeBlocks.all()) if (d.blockType() != null) h((BlockType) d.blockType(), d.hardness, d.tool, d.requiresTool);
    }

    private static int toolOf(Items.Stack s) {
        if (s == null || s.item == null) return 0;
        switch (s.item.kind) { case PICKAXE: return 1; case SHOVEL: return 2; case AXE: return 3; default: return 0; }
    }

    /** Seconds to break; 0 = instant, negative = unbreakable. */
    static float breakTime(BlockType type, Items.Stack held) {
        float[] info = HARDNESS.get(type);
        float hardness = info != null ? info[0] : 1f;
        int required = info != null ? (int) info[1] : 0;
        boolean needsTool = info != null && info[2] > 0;
        if (hardness < 0) return -1;
        if (hardness == 0) return 0;
        int tool = toolOf(held);
        if (required != 0 && tool == required) return hardness * 1.5f / Items.toolSpeed(held.item.tier);
        if (needsTool) return hardness * 5f;
        return hardness * 1.5f;
    }

    static boolean dropsWithout(BlockType type, Items.Stack held) {
        float[] info = HARDNESS.get(type);
        if (info == null || info[2] == 0) return true;
        return toolOf(held) == (int) info[1];
    }

    /** What the block turns into when picked up (ores drop their material like Minecraft). */
    static Items.Stack dropOf(BlockType type) {
        switch (type) {
            case GRASS: case SNOWY_GRASS: return Items.Stack.of(BlockType.DIRT);
            case OAK_LEAVES: return Math.random() < 0.08 ? Items.Stack.of(Items.byId("apple")) : null;
            case GLASS: case TALLGRASS: case BEDROCK: return null;
            case DOOR_UPPER: return Items.Stack.of(BlockType.DOOR);
            case COAL_ORE: return Items.Stack.of(Items.byId("coal"));
            case COPPER_ORE: return Items.Stack.of(Items.byId("copper_ingot"));
            case IRON_ORE: return Items.Stack.of(Items.byId("iron_ingot"));
            case GOLD_ORE: return Items.Stack.of(Items.byId("gold_ingot"));
            case DIAMOND_ORE: return Items.Stack.of(Items.byId("diamond"));
            case EMERALD_ORE: return Items.Stack.of(Items.byId("emerald"));
            case REDSTONE_ORE: return Items.Stack.of(Items.byId("redstone"), 4);
            case LAPIS_ORE: return Items.Stack.of(Items.byId("lapis"), 4);
            default: return Items.Stack.of(type);
        }
    }

    public static void tick(VoxelEngine e, float dt) {
        if (Settings.isCreative() || !e.isWorldActive() || e.getCamera() == null || e.getCamera().getInputHandler() == null) { progress = 0; return; }
        if (e.getLocalPlayer() != null && e.getLocalPlayer().dead) { progress = 0; return; }
        long window = UIManager.get().getWindow();
        boolean left = !UIManager.get().hasActiveGUIs() && GLFW.glfwGetMouseButton(window, 0) == GLFW.GLFW_PRESS;
        InputHandler.Hit hit = e.getCamera().getInputHandler().getHoverHit();
        if (!left || hit == null || Mobs.suppressBlockClick()) { progress = 0; hadTarget = false; return; }
        if (!hadTarget || hit.x != tx || hit.y != ty || hit.z != tz) { tx = hit.x; ty = hit.y; tz = hit.z; progress = 0; hadTarget = true; }
        BlockType type;
        try { type = BlockType.fromId(BlockState.typeId(e.getWorld().getState(tx, ty, tz))); } catch (Exception error) { return; }
        if (type == BlockType.AIR) { progress = 0; return; }
        float time = breakTime(type, BuiltinUI.held());
        if (time < 0) { progress = 0; return; }
        progress += time == 0 ? 1f : dt / time;
        if (progress >= 1f) {
            progress = 0;
            hadTarget = false;
            approved = true;
            try {
                if (e.getRenderer() != null) e.getRenderer().getPlayerRenderer().triggerSwing();
                e.getEventManager().fire(new ClickEvent(ClickEvent.ClickType.LEFT, tx, ty, tz));
            } finally { approved = false; }
        }
    }

    /** Called from the applyEdit hook after a survival break went through: spawns a drop entity. */
    public static void onBroken(BlockType broken, long brokenState, int x, int y, int z) {
        BuiltinUI.sound().play("break");
        Items.Stack tile = Tiles.onBlockBroken(x, y, z, brokenState);
        if (tile != null) { Drops.spawn(tile, x, y, z); return; }
        if (!dropsWithout(broken, BuiltinUI.held())) return;
        Items.Stack drop = dropOf(broken);
        if (drop == null) return;
        Drops.spawn(drop, x, y, z);
    }

    public static void pickup(Items.Stack stack) {
        pickupText = "+" + stack.count + " " + stack.name();
        pickupNs = System.nanoTime();
    }

    /** Adds to an existing stack in the hotbar/storage, else the first empty slot. */
    public static boolean give(Items.Stack stack) {
        Items.Stack[][] areas = {BuiltinUI.HOTBAR, BuiltinUI.STORAGE};
        for (Items.Stack[] area : areas) for (Items.Stack s : area) if (s != null && s.same(stack) && s.count < 64) { s.count += stack.count; return true; }
        for (Items.Stack[] area : areas) for (int i = 0; i < area.length; i++) if (area[i] == null) { area[i] = stack; if (area == BuiltinUI.HOTBAR) BuiltinUI.select(VoxelEngine.getEngine(), BuiltinUI.selected); return true; }
        return false;
    }

    /** Called from the applyEdit hook after a survival placement went through. */
    public static void onPlaced() {
        Items.Stack held = BuiltinUI.held();
        BuiltinUI.sound().play("place");
        if (held == null || !held.isBlock()) return;
        held.count--;
        if (held.count <= 0) { BuiltinUI.HOTBAR[BuiltinUI.selected] = null; BuiltinUI.select(VoxelEngine.getEngine(), BuiltinUI.selected); }
    }

    /** Break progress ring around the crosshair and the pickup popup. 2D rendering must be set up. */
    public static void renderHud(int w, int h) {
        if (progress > 0f) {
            float cx = w / 2f, cy = h / 2f + 18, bw = 60, bh = 6;
            GLUIRenderer.drawPanel(cx - bw / 2 - 1, cy - 1, bw + 2, bh + 2, 0, 0, 0, 0.6f);
            GLUIRenderer.drawPanel(cx - bw / 2, cy, bw * Math.min(1f, progress), bh, 0.9f, 0.9f, 0.9f, 0.9f);
        }
        float age = (System.nanoTime() - pickupNs) / 1e9f;
        if (!pickupText.isEmpty() && age < 2f) {
            float alpha = age < 1.4f ? 1f : Math.max(0f, 1f - (age - 1.4f) / 0.6f);
            Mc.text(pickupText, (w - GLUIRenderer.getTextWidth(pickupText)) / 2f, h - 48 - 54, 0.8f, 1f, 0.8f, alpha);
        }
    }
}
