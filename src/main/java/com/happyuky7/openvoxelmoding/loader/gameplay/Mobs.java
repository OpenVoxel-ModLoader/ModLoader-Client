package com.happyuky7.openvoxelmoding.loader.gameplay;

import com.happyuky7.openvoxelmoding.loader.Lang;




import com.happyuky7.openvoxelmoding.loader.core.Settings;
import com.happyuky7.openvoxelmoding.loader.item.Drops;
import com.happyuky7.openvoxelmoding.loader.item.Items;
import com.happyuky7.openvoxelmoding.loader.ui.BuiltinUI;
import com.happyuky7.openvoxelmoding.loader.ui.HandRenderer;

import net.openvoxel.engine.VoxelEngine;
import net.openvoxel.engine.rendering.Camera;
import net.openvoxel.engine.rendering.Renderer;
import net.openvoxel.engine.ui.UIManager;
import net.openvoxel.engine.world.World;
import net.openvoxel.engine.world.block.BlockState;
import net.openvoxel.engine.world.block.BlockType;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Client-side mobs: pigs wander on grass by day, zombies spawn in the dark and chase the player.
 * They live only on this client (not synced to servers), use box models drawn in immediate mode,
 * can be hit with the left button (swords do more damage) and drop food when killed.
 */
public final class Mobs {
    private Mobs() {}

    public enum Kind {
        PIG(10f, 0.9f, 0.9f, 1.2f, false), ZOMBIE(20f, 0.6f, 1.95f, 2.0f, true);
        final float maxHealth, width, height, speed; final boolean hostile;
        Kind(float maxHealth, float width, float height, float speed, boolean hostile) { this.maxHealth = maxHealth; this.width = width; this.height = height; this.speed = speed; this.hostile = hostile; }
        public String label() { return Lang.t("mob." + name().toLowerCase()); }
    }

    static final class Mob {
        final Kind kind;
        double x, y, z, vx, vy, vz;
        float yaw, targetYaw, health, legPhase, legAmount;
        double wanderTimer = 1;
        boolean moving, onGround;
        long hurtNs, lastAttackNs;
        final long born = System.nanoTime();
        Mob(Kind kind, double x, double y, double z) { this.kind = kind; this.x = x; this.y = y; this.z = z; health = kind.maxHealth; yaw = targetYaw = RANDOM.nextFloat() * 360f; }
    }

    private static final List<Mob> MOBS = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static long lastSpawnNs, suppressClicksUntil;
    private static boolean leftWas;
    public static boolean enabled = true;

    public static int count() { return MOBS.size(); }
    public static void clear() { MOBS.clear(); }
    static List<Mob> all() { return MOBS; }

    /** While true the engine's left click (block breaking) is skipped because a mob was hit. */
    public static boolean suppressBlockClick() { return System.nanoTime() < suppressClicksUntil; }

    private static boolean solid(World w, double x, double y, double z) {
        int by = (int) Math.floor(y);
        if (by < 0) return true;
        if (by >= 512) return false;
        try { return BlockType.fromId(BlockState.typeId(w.getState((int) Math.floor(x), by, (int) Math.floor(z)))).isSolid(); }
        catch (Exception e) { return false; }
    }

    private static boolean free(World w, double x, double y, double z, float width, float height) {
        double h = width / 2;
        for (double dx = -h; dx <= h; dx += h) for (double dz = -h; dz <= h; dz += h) for (double dy = 0.05; dy < height; dy += 0.9) {
            if (solid(w, x + dx, y + dy, z + dz)) return false;
        }
        return !solid(w, x, y + height - 0.05, z);
    }

    public static void tick(VoxelEngine e, float dt) {
        if (!enabled || !e.isWorldActive() || e.getCamera() == null || e.getWorld() == null || e.getRenderer() == null) { MOBS.clear(); return; }
        World w = e.getWorld();
        Vector3d p = e.getCamera().getPosition();
        long now = System.nanoTime();
        float day = e.getRenderer().getDayFactor();
        boolean creative = Settings.isCreative();
        boolean dead = e.getLocalPlayer() != null && e.getLocalPlayer().dead;
        if (now - lastSpawnNs > 2_000_000_000L) { lastSpawnNs = now; trySpawn(w, p, day); }
        for (Iterator<Mob> it = MOBS.iterator(); it.hasNext(); ) {
            Mob m = it.next();
            double dx = p.x - m.x, dz = p.z - m.z, dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 72 || m.y < -8 || (m.kind == Kind.ZOMBIE && day > 0.75f && now - m.born > 20_000_000_000L)) { it.remove(); continue; }
            // AI
            m.wanderTimer -= dt;
            boolean chase = m.kind.hostile && !dead && !creative && dist < 24;
            if (chase) {
                m.targetYaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
                m.moving = dist > 1.1;
                if (dist < 1.6 && now - m.lastAttackNs > 1_000_000_000L && Math.abs(p.y - m.y) < 2.2) {
                    m.lastAttackNs = now;
                    Survival.damage(e, 3f, true, "zombie");
                }
            } else if (m.wanderTimer <= 0) {
                m.wanderTimer = 1.5 + RANDOM.nextDouble() * 4;
                m.moving = RANDOM.nextFloat() < 0.6f;
                if (m.moving) m.targetYaw = RANDOM.nextFloat() * 360f;
            }
            float diff = ((m.targetYaw - m.yaw) % 360 + 540) % 360 - 180;
            m.yaw += diff * Math.min(1f, dt * 6f);
            double speed = m.moving ? (chase ? m.kind.speed : m.kind.speed * 0.5) : 0;
            double rad = Math.toRadians(m.yaw);
            double wx = Math.sin(rad) * speed, wz = -Math.cos(rad) * speed;
            if (now - m.hurtNs < 350_000_000L) { wx = 0; wz = 0; }
            m.vx += (wx - m.vx) * Math.min(1f, dt * 8f);
            m.vz += (wz - m.vz) * Math.min(1f, dt * 8f);
            // physics
            m.vy -= 24.0 * dt;
            double nx = m.x + m.vx * dt, nz = m.z + m.vz * dt, ny = m.y + m.vy * dt;
            float hw = m.kind.width / 2;
            boolean blocked = false;
            if (free(w, nx, m.y, m.z, m.kind.width, m.kind.height)) m.x = nx; else { blocked = true; m.vx = 0; }
            if (free(w, m.x, m.y, nz, m.kind.width, m.kind.height)) m.z = nz; else { blocked = true; m.vz = 0; }
            if (m.vy <= 0) {
                if (solid(w, m.x, ny - 0.01, m.z) || solid(w, m.x + hw, ny - 0.01, m.z + hw) || solid(w, m.x - hw, ny - 0.01, m.z - hw)) { m.vy = 0; m.y = Math.floor(ny) + 1; m.onGround = true; }
                else { m.y = ny; m.onGround = false; }
            } else { if (free(w, m.x, ny, m.z, m.kind.width, m.kind.height)) m.y = ny; else m.vy = 0; }
            if (blocked && m.onGround && m.moving) { m.vy = 7.5; m.onGround = false; }
            else if (m.moving && m.onGround && !chase && m.wanderTimer > 0.5 && !solid(w, m.x + Math.sin(rad) * 0.8, m.y - 0.6, m.z - Math.cos(rad) * 0.8)) { m.moving = false; m.targetYaw += 180; }  // cliff ahead: turn around
            double hs = Math.sqrt(m.vx * m.vx + m.vz * m.vz);
            m.legAmount += ((hs > 0.1 ? 1f : 0f) - m.legAmount) * Math.min(1f, dt * 8f);
            m.legPhase += hs * dt * 3.5f;
            if (m.health <= 0) { it.remove(); onDeath(e, m); }
        }
        // player attack
        boolean gui = UIManager.get().hasActiveGUIs();
        boolean left = !gui && GLFW.glfwGetMouseButton(UIManager.get().getWindow(), 0) == GLFW.GLFW_PRESS;
        if (left && !leftWas && !dead) attack(e);
        leftWas = left;
    }

    /** Spawns a mob 4 blocks in front of the player (debug command). */
    public static void spawnInFront(VoxelEngine e, Kind kind) {
        Vector3d p = e.getCamera().getPosition();
        double yaw = Math.toRadians(e.getCamera().getYaw());
        double x = p.x + Math.sin(yaw) * 4, z = p.z - Math.cos(yaw) * 4;
        World w = e.getWorld();
        double y = p.y + 1;
        while (y > 0 && !solid(w, x, y - 1, z)) y--;
        MOBS.add(new Mob(kind, x, y, z));
    }

    private static void trySpawn(World w, Vector3d p, float day) {
        boolean night = day < 0.3f;
        int pigs = 0, zombies = 0;
        for (Mob m : MOBS) if (m.kind == Kind.PIG) pigs++; else zombies++;
        for (int attempt = 0; attempt < 6; attempt++) {
            double ang = RANDOM.nextDouble() * Math.PI * 2, r = 14 + RANDOM.nextDouble() * 26;
            int bx = (int) Math.floor(p.x + Math.cos(ang) * r), bz = (int) Math.floor(p.z + Math.sin(ang) * r);
            int top = -1;
            for (int y = Math.min(200, (int) p.y + 24); y > 0; y--) {
                if (solid(w, bx + 0.5, y, bz + 0.5)) { top = y; break; }
            }
            if (top < 0) continue;
            BlockType ground;
            try { ground = BlockType.fromId(BlockState.typeId(w.getState(bx, top, bz))); } catch (Exception e) { continue; }
            if (!free(w, bx + 0.5, top + 1, bz + 0.5, 0.9f, 1.95f)) continue;
            Kind kind;
            if (night) { if (zombies >= 5 || RANDOM.nextFloat() < 0.4f) continue; kind = Kind.ZOMBIE; zombies++; }
            else { if (pigs >= 6 || (ground != BlockType.GRASS && ground != BlockType.SNOWY_GRASS) || RANDOM.nextFloat() < 0.5f) continue; kind = Kind.PIG; pigs++; }
            MOBS.add(new Mob(kind, bx + 0.5, top + 1, bz + 0.5));
            return;
        }
    }

    private static void attack(VoxelEngine e) {
        Camera cam = e.getCamera();
        Vector3d p = cam.getPosition();
        double yaw = Math.toRadians(cam.getYaw()), pitch = Math.toRadians(cam.getPitch());
        double dx = Math.sin(yaw) * Math.cos(pitch), dy = -Math.sin(pitch), dz = -Math.cos(yaw) * Math.cos(pitch);
        double ex = p.x, ey = p.y + Camera.EYE_OFFSET, ez = p.z;
        Mob best = null; double bestT = 4.5;
        for (Mob m : MOBS) {
            double hw = m.kind.width / 2;
            double t = ray(ex, ey, ez, dx, dy, dz, m.x - hw, m.y, m.z - hw, m.x + hw, m.y + m.kind.height, m.z + hw);
            if (t >= 0 && t < bestT) { bestT = t; best = m; }
        }
        if (best == null) return;
        // a block in between wins
        net.openvoxel.engine.input.InputHandler.Hit hit = cam.getInputHandler() != null ? cam.getInputHandler().getHoverHit() : null;
        if (hit != null) {
            double bd = Math.sqrt(Math.pow(hit.x + 0.5 - ex, 2) + Math.pow(hit.y + 0.5 - ey, 2) + Math.pow(hit.z + 0.5 - ez, 2));
            if (bd + 0.6 < bestT) return;
        }
        Items.Stack held = BuiltinUI.held();
        float damage = held != null && held.item != null && held.item.kind == Items.Kind.SWORD ? held.item.value : held != null && held.item != null && held.item.isTool() ? 2f : 1f;
        best.health -= damage;
        best.hurtNs = System.nanoTime();
        best.vx = dx * 6; best.vz = dz * 6; best.vy = 4.5;
        if (best.kind.hostile) { best.targetYaw = (float) Math.toDegrees(Math.atan2(p.x - best.x, -(p.z - best.z))); best.moving = true; }
        else { best.targetYaw = (float) Math.toDegrees(Math.atan2(best.x - p.x, -(best.z - p.z))); best.moving = true; best.wanderTimer = 3; }
        suppressClicksUntil = System.nanoTime() + 250_000_000L;
        BuiltinUI.sound().play("hurt");
        if (e.getRenderer() != null) try { e.getRenderer().getPlayerRenderer().triggerSwing(); } catch (Throwable ignored) { }
    }

    private static void onDeath(VoxelEngine e, Mob m) {
        BuiltinUI.sound().play("pop");
        int bx = (int) Math.floor(m.x), by = (int) Math.floor(m.y), bz = (int) Math.floor(m.z);
        if (m.kind == Kind.PIG) Drops.spawn(Items.Stack.of(Items.byId("porkchop"), 1 + RANDOM.nextInt(2)), bx, by, bz);
        else if (RANDOM.nextFloat() < 0.8f) Drops.spawn(Items.Stack.of(Items.byId("rotten_flesh"), 1 + RANDOM.nextInt(2)), bx, by, bz);
    }

    /** Slab ray/AABB test; returns the entry distance or -1. */
    private static double ray(double ox, double oy, double oz, double dx, double dy, double dz, double x0, double y0, double z0, double x1, double y1, double z1) {
        double tmin = 0, tmax = 1e9;
        double[] o = {ox, oy, oz}, d = {dx, dy, dz}, lo = {x0, y0, z0}, hi = {x1, y1, z1};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-9) { if (o[i] < lo[i] || o[i] > hi[i]) return -1; continue; }
            double t1 = (lo[i] - o[i]) / d[i], t2 = (hi[i] - o[i]) / d[i];
            if (t1 > t2) { double t = t1; t1 = t2; t2 = t; }
            tmin = Math.max(tmin, t1); tmax = Math.min(tmax, t2);
            if (tmin > tmax) return -1;
        }
        return tmin;
    }

    // ---- Rendering (matrices loaded, GL state saved by the caller) -------------------------

    public static void render(Renderer renderer, VoxelEngine e, Camera cam) {
        if (MOBS.isEmpty()) return;
        Vector3d p = cam.getPosition();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        try {
            for (Mob m : MOBS) {
                double rx = m.x - p.x, ry = m.y - p.y - Camera.EYE_OFFSET, rz = m.z - p.z;
                if (rx * rx + ry * ry + rz * rz > 64 * 64) continue;
                float bright = HandRenderer.brightnessAt(e, renderer, m.x, m.y + 0.5, m.z);
                boolean hurt = System.nanoTime() - m.hurtNs < 300_000_000L;
                GL11.glPushMatrix();
                GL11.glTranslatef((float) rx, (float) ry, (float) rz);
                GL11.glRotatef(-m.yaw, 0, 1, 0);
                float swing = (float) Math.sin(m.legPhase) * 0.7f * m.legAmount;
                if (m.kind == Kind.PIG) pig(swing, bright, hurt);
                else zombie(swing, bright, hurt);
                GL11.glPopMatrix();
            }
        } finally { GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glColor4f(1, 1, 1, 1); }
    }

    private static void color(int rgb, float bright, boolean hurt) {
        float r = ((rgb >> 16) & 255) / 255f * bright, g = ((rgb >> 8) & 255) / 255f * bright, b = (rgb & 255) / 255f * bright;
        if (hurt) { r = Math.min(1f, r + 0.45f); g *= 0.55f; b *= 0.55f; }
        GL11.glColor4f(r, g, b, 1f);
    }

    /** Box in 1/16 units with per-face shading; the current colour is multiplied by the shade. */
    private static void box(float x0, float y0, float z0, float x1, float y1, float z1, int rgb, float bright, boolean hurt) {
        float ax = x0 / 16f, ay = y0 / 16f, az = z0 / 16f, bx = x1 / 16f, by = y1 / 16f, bz = z1 / 16f;
        float[] shades = {1f, 0.5f, 0.8f, 0.8f, 0.6f, 0.6f};
        float[][] faces = {
            {ax, by, az, bx, by, az, bx, by, bz, ax, by, bz},   // top
            {ax, ay, bz, bx, ay, bz, bx, ay, az, ax, ay, az},   // bottom
            {ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz},   // +z
            {bx, ay, az, ax, ay, az, ax, by, az, bx, by, az},   // -z
            {bx, ay, bz, bx, ay, az, bx, by, az, bx, by, bz},   // +x
            {ax, ay, az, ax, ay, bz, ax, by, bz, ax, by, az}};  // -x
        GL11.glBegin(GL11.GL_QUADS);
        for (int f = 0; f < 6; f++) {
            color(rgb, bright * shades[f], hurt);
            float[] q = faces[f];
            for (int v = 0; v < 4; v++) GL11.glVertex3f(q[v * 3], q[v * 3 + 1], q[v * 3 + 2]);
        }
        GL11.glEnd();
    }

    private static void pig(float swing, float bright, boolean hurt) {
        int pink = 0xF0A5A5, dark = 0xD98A8A, eye = 0x222222;
        box(-5, 6, -8, 5, 14, 8, pink, bright, hurt);                // body
        box(-4, 8, -12, 4, 16, -8, pink, bright, hurt);              // head
        box(-2, 9, -13, 2, 12, -12, dark, bright, hurt);             // snout
        box(-3, 13, -12.1f, -2, 14, -11.9f, eye, bright, hurt);      // eyes
        box(2, 13, -12.1f, 3, 14, -11.9f, eye, bright, hurt);
        float[][] legs = {{-5, -7}, {1, -7}, {-5, 5}, {1, 5}};
        for (int i = 0; i < 4; i++) {
            float a = (i % 2 == 0 ? swing : -swing) * (i < 2 ? 1 : -1);
            GL11.glPushMatrix();
            GL11.glTranslatef(0, 6 / 16f, (legs[i][1] + 2) / 16f);
            GL11.glRotatef((float) Math.toDegrees(a), 1, 0, 0);
            GL11.glTranslatef(0, -6 / 16f, -(legs[i][1] + 2) / 16f);
            box(legs[i][0], 0, legs[i][1], legs[i][0] + 4, 6, legs[i][1] + 4, pink, bright, hurt);
            GL11.glPopMatrix();
        }
    }

    private static void zombie(float swing, float bright, boolean hurt) {
        int skin = 0x4E9A4E, shirt = 0x2F7F9A, pants = 0x3A3A7A, eye = 0x111111;
        box(-4, 24, -4, 4, 32, 4, skin, bright, hurt);               // head
        box(-2, 28, -4.1f, -1, 29, -3.9f, eye, bright, hurt);
        box(1, 28, -4.1f, 2, 29, -3.9f, eye, bright, hurt);
        box(-4, 12, -2, 4, 24, 2, shirt, bright, hurt);              // body
        for (int side = -1; side <= 1; side += 2) {                  // arms held forward
            GL11.glPushMatrix();
            GL11.glTranslatef(side * 6 / 16f, 22 / 16f, 0);
            GL11.glRotatef(-85f + (float) Math.sin(swing * 2) * 6f, 1, 0, 0);
            box(-2, -12, -2, 2, 0, 2, skin, bright, hurt);
            GL11.glPopMatrix();
        }
        for (int side = -1; side <= 1; side += 2) {                  // legs
            GL11.glPushMatrix();
            GL11.glTranslatef(side * 2 / 16f, 12 / 16f, 0);
            GL11.glRotatef((float) Math.toDegrees(swing * side), 1, 0, 0);
            box(-2, -12, -2, 2, 0, 2, pants, bright, hurt);
            GL11.glPopMatrix();
        }
    }
}
