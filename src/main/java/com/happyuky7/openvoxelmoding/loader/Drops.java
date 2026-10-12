package com.happyuky7.openvoxelmoding.loader;

import net.openvoxel.engine.VoxelEngine;
import net.openvoxel.engine.light.LightEngine;
import net.openvoxel.engine.rendering.Camera;
import net.openvoxel.engine.rendering.Renderer;
import net.openvoxel.engine.world.World;
import net.openvoxel.engine.world.block.BlockState;
import net.openvoxel.engine.world.block.BlockType;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Client-side item entities: broken blocks pop out, fall, bob/spin and are picked up by walking over them. */
final class Drops {
    private Drops() {}

    static final class Drop {
        final Items.Stack stack;
        double x, y, z, vx, vy, vz;
        final long born = System.nanoTime();
        final float phase;
        long pickupDelayNs = 400_000_000L;
        Drop(Items.Stack stack, double x, double y, double z, float phase) { this.stack = stack; this.x = x; this.y = y; this.z = z; this.phase = phase; }
    }

    /** Throws a stack from the player's eyes along the view direction (Q key, inventory drag-out). */
    static void throwFromPlayer(VoxelEngine e, Items.Stack stack) {
        if (stack == null || e.getCamera() == null) return;
        Vector3d p = e.getCamera().getPosition();
        double yaw = Math.toRadians(e.getCamera().getYaw()), pitch = Math.toRadians(e.getCamera().getPitch());
        double dx = Math.sin(yaw) * Math.cos(pitch), dy = -Math.sin(pitch), dz = -Math.cos(yaw) * Math.cos(pitch);
        Drop d = new Drop(stack, p.x + dx * 0.5, p.y + 1.4, p.z + dz * 0.5, RANDOM.nextFloat() * 6.28f);
        d.vx = dx * 5.0 + (RANDOM.nextDouble() - 0.5) * 0.4;
        d.vy = dy * 5.0 + 1.5;
        d.vz = dz * 5.0 + (RANDOM.nextDouble() - 0.5) * 0.4;
        d.pickupDelayNs = 1_500_000_000L;
        DROPS.add(d);
        BuiltinUI.sound().play("pop");
    }

    private static final List<Drop> DROPS = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static final double SIZE = 0.25;

    static int count() { return DROPS.size(); }
    static void clear() { DROPS.clear(); }

    static void spawn(Items.Stack stack, int bx, int by, int bz) {
        Drop d = new Drop(stack, bx + 0.5, by + 0.3, bz + 0.5, RANDOM.nextFloat() * 6.28f);
        d.vx = (RANDOM.nextDouble() - 0.5) * 2.0;
        d.vz = (RANDOM.nextDouble() - 0.5) * 2.0;
        d.vy = 3.0;
        DROPS.add(d);
    }

    private static boolean solid(World w, double x, double y, double z) {
        int by = (int) Math.floor(y);
        if (by < 0) return true;
        try { return BlockType.fromId(BlockState.typeId(w.getState((int) Math.floor(x), by, (int) Math.floor(z)))).isSolid(); }
        catch (Exception e) { return false; }
    }

    static void tick(VoxelEngine e, float dt) {
        if (!e.isWorldActive() || e.getCamera() == null || e.getWorld() == null) { DROPS.clear(); return; }
        World w = e.getWorld();
        Vector3d p = e.getCamera().getPosition();
        long now = System.nanoTime();
        boolean dead = e.getLocalPlayer() != null && e.getLocalPlayer().dead;
        for (Iterator<Drop> it = DROPS.iterator(); it.hasNext(); ) {
            Drop d = it.next();
            if (now - d.born > 300_000_000_000L) { it.remove(); continue; }
            d.vy -= 20.0 * dt;
            double nx = d.x + d.vx * dt, ny = d.y + d.vy * dt, nz = d.z + d.vz * dt;
            if (!solid(w, nx, d.y, d.z)) d.x = nx; else d.vx = 0;
            if (!solid(w, d.x, d.y, nz)) d.z = nz; else d.vz = 0;
            if (d.vy < 0 && solid(w, d.x, ny - SIZE * 0.5, d.z)) { d.vy = 0; d.y = Math.floor(ny - SIZE * 0.5) + 1 + SIZE * 0.5; d.vx *= 0.6; d.vz *= 0.6; }
            else if (d.vy > 0 && solid(w, d.x, ny + SIZE, d.z)) d.vy = 0;
            else d.y = ny;
            if (d.y < -8) { it.remove(); continue; }
            // Magnet pickup like Minecraft: within reach after a short delay.
            if (!dead && now - d.born > d.pickupDelayNs) {
                double dx = p.x - d.x, dy = (p.y + 0.9) - d.y, dz = p.z - d.z, dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist < 1.6) {
                    if (Mining.give(d.stack)) { BuiltinUI.sound().play("pickup"); Mining.pickup(d.stack); it.remove(); }
                } else if (dist < 3.0) { d.x += dx / dist * 6 * dt; d.y += dy / dist * 6 * dt; d.z += dz / dist * 6 * dt; }
            }
        }
    }

    /** Renders all drops; call with the camera's projection/view matrices already loaded. */
    static void render(Renderer renderer, VoxelEngine e, Camera cam) {
        if (DROPS.isEmpty()) return;
        Vector3d p = cam.getPosition();
        LightEngine le = VoxelEngine.getLightEngine();
        World w = e.getWorld();
        float day = renderer.getDayFactor();
        float t = (System.nanoTime() % 100_000_000_000L) / 1e9f;
        for (Drop d : DROPS) {
            double rx = d.x - p.x, ry = d.y - p.y - 1.6, rz = d.z - p.z;
            if (rx * rx + ry * ry + rz * rz > 64 * 64) continue;
            float bright = 1f;
            if (le != null && w != null) {
                try {
                    int bx = (int) Math.floor(d.x), by = (int) Math.floor(d.y + 0.2), bz = (int) Math.floor(d.z);
                    bright = Math.max(Math.max(le.sampleSkyLight01(w, bx, by, bz, day), le.sampleBlockLight01(w, bx, by, bz)), 0.15f);
                } catch (Exception ignored) { }
            }
            float bob = (float) Math.sin(t * 2.5f + d.phase) * 0.06f;
            GL11.glPushMatrix();
            GL11.glTranslatef((float) rx, (float) ry + bob, (float) rz);
            if (d.stack.isBlock()) {
                GL11.glRotatef((t * 60f + d.phase * 57f) % 360f, 0, 1, 0);
                GL11.glScalef((float) SIZE, (float) SIZE, (float) SIZE);
                GL11.glTranslatef(-0.5f, -0.5f, -0.5f);
                HandRenderer.drawBlock(d.stack.block, BlockState.make(d.stack.block.getId()), bright);
            } else {
                GL11.glRotatef(-cam.getYaw(), 0, 1, 0);   // billboard towards the camera
                HandRenderer.drawItem(d.stack.item, 0.35f, bright);
            }
            GL11.glPopMatrix();
        }
    }
}
