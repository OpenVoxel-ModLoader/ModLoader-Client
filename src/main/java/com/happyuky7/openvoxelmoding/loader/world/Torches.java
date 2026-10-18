package com.happyuky7.openvoxelmoding.loader.world;

import com.happyuky7.openvoxelmoding.loader.core.Hooks;
import com.happyuky7.openvoxelmoding.loader.core.Settings;
import com.happyuky7.openvoxelmoding.loader.item.Drops;
import com.happyuky7.openvoxelmoding.loader.item.Items;
import com.happyuky7.openvoxelmoding.loader.ui.BuiltinUI;

import net.openvoxel.engine.VoxelEngine;
import net.openvoxel.engine.physics.PhysicsEngine;
import net.openvoxel.engine.world.AbstractBlock;
import net.openvoxel.engine.world.World;
import net.openvoxel.engine.world.block.BlockPlacement;
import net.openvoxel.engine.world.block.BlockState;
import net.openvoxel.engine.world.block.BlockType;
import net.openvoxel.engine.world.block.model.BlockModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Minecraft torch rules on top of the engine's single standing torch: torches placed on a wall
 * lean against it (state facing bits + the "upside" bit mark a wall torch, the chunk mesher gets
 * a tilted model variant), a torch needs a solid block below or behind it, loses it when that
 * block is broken, and clicking a torch places the new block next to it instead of replacing it.
 */
public final class Torches {
    private Torches() {}

    private static final BlockModel[] WALL = new BlockModel[4];
    private static final int[][] DIRS = {{0, -1}, {0, 1}, {1, 0}, {-1, 0}};   // NORTH, SOUTH, EAST, WEST (dx, dz)

    public static boolean isWall(long state) { return BlockState.stairsUpside(state); }
    public static int facing(long state) { return BlockState.facing(state); }

    static int facingOf(int dx, int dz) { return dz < 0 ? 0 : dz > 0 ? 1 : dx > 0 ? 2 : 3; }

    /** Placement state: wall torches get the face normal as facing plus the wall flag. */
    public static long placementState(BlockPlacement.PlacementContext ctx, long base) {
        if (ctx.ny == 0 && (ctx.nx != 0 || ctx.nz != 0)) return BlockState.asStairs(base, facingOf(ctx.nx, ctx.nz), true);
        return base;
    }

    static boolean solid(World w, int x, int y, int z) {
        if (w == null) return true;
        if (y < 0) return true;
        try { return BlockType.fromId(BlockState.typeId(w.getState(x, y, z))).isSolid(); } catch (Exception e) { return true; }
    }

    /** A torch stands on a solid block or hangs from a solid wall behind it. */
    public static boolean supported(World w, int x, int y, int z, long state) {
        if (!isWall(state)) return solid(w, x, y - 1, z);
        int[] d = DIRS[facing(state)];
        return solid(w, x - d[0], y, z - d[1]);
    }

    /** After the block at (x,y,z) was removed: torches it supported pop off (dropping in survival). */
    public static void onBlockRemoved(VoxelEngine e, int x, int y, int z) {
        World w = e.getWorld();
        if (w == null) return;
        List<int[]> remove = new ArrayList<>();
        try {
            long above = w.getState(x, y + 1, z);
            if (BlockState.typeId(above) == BlockType.TORCH.getId() && !isWall(above)) remove.add(new int[]{x, y + 1, z});
            for (int f = 0; f < 4; f++) {
                int cx = x + DIRS[f][0], cz = z + DIRS[f][1];
                long s = w.getState(cx, y, cz);
                if (BlockState.typeId(s) == BlockType.TORCH.getId() && isWall(s) && facing(s) == f) remove.add(new int[]{cx, y, cz});
            }
        } catch (Exception ignored) { return; }
        for (int[] p : remove) {
            Hooks.applyEditOrig(e, p[0], p[1], p[2], new AbstractBlock(BlockType.AIR));
            if (!Settings.isCreative()) Drops.spawn(Items.Stack.of(BlockType.TORCH), p[0], p[1], p[2]);
            BuiltinUI.sound().play("pop");
        }
    }

    /** Tilted copy of the standing torch model for a torch leaning away from the wall at {@code facing}. */
    public static BlockModel wallModel(BlockModel base, int facing) {
        if (base == null) return null;
        BlockModel m = WALL[facing & 3];
        if (m != null) return m;
        int[] d = DIRS[facing & 3];
        float nx = d[0], nz = d[1];
        double angle = Math.toRadians(24);
        float sin = (float) Math.sin(angle), cos = (float) Math.cos(angle);
        float ox = -nx * 0.37f, oz = -nz * 0.37f, oy = 0.18f;
        float minX = 1, minY = 1, minZ = 1, maxX = 0, maxY = 0, maxZ = 0;
        float[][] out = new float[base.quads.length][];
        for (int i = 0; i < base.quads.length; i++) {
            float[] q = base.quads[i], n = q.clone();
            for (int v = 0; v * 5 + 2 < q.length; v++) {
                int o = v * 5;
                float x = q[o] - 0.5f, y = q[o + 1], z = q[o + 2] - 0.5f;
                // lean the local "up" towards +normal: rotate in the plane spanned by up and the normal
                float along = x * nx + z * nz;                   // component along the normal
                float newAlong = along * cos + y * sin;
                float newY = -along * sin + y * cos;
                x += (newAlong - along) * nx;
                z += (newAlong - along) * nz;
                y = newY;
                x += 0.5f + ox; y += oy; z += 0.5f + oz;
                n[o] = x; n[o + 1] = y; n[o + 2] = z;
                minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
            }
            out[i] = n;
        }
        PhysicsEngine.AABB bounds = new PhysicsEngine.AABB(clamp(minX), clamp(minY), clamp(minZ), clamp(maxX), clamp(maxY), clamp(maxZ));
        List<PhysicsEngine.AABB> boxes = new ArrayList<>();
        boxes.add(bounds);
        m = new BlockModel(out, boxes, base.textures, base.noCull, bounds);
        WALL[facing & 3] = m;
        return m;
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
}
