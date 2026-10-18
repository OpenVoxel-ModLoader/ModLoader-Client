package com.happyuky7.openvoxelmoding.loader.gameplay;

import com.happyuky7.openvoxelmoding.loader.Lang;

import com.happyuky7.openvoxelmoding.loader.core.Settings;
import com.happyuky7.openvoxelmoding.loader.net.Net;
import com.happyuky7.openvoxelmoding.loader.ui.Keybinds;

import net.openvoxel.engine.VoxelEngine;
import net.openvoxel.engine.physics.PhysicsEngine;
import net.openvoxel.engine.ui.UIManager;
import net.openvoxel.engine.world.World;
import net.openvoxel.engine.world.block.BlockState;
import net.openvoxel.engine.world.block.BlockType;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;

/** Creative flight (double-tap space), sneak edge-guard and survival fall damage, evaluated once per frame. */
public final class GameMode {
    private GameMode() {}

    public static boolean flying;
    private static long lastSpaceTap, lastNs;
    private static boolean spaceWas;
    private static double fallStart = Double.NaN;

    public static boolean skipPhysics() { return flying && Settings.isCreative(); }

    /** Requests a game mode: on a ModLoader server the server decides (ops only), otherwise applied locally. */
    public static void set(String mode) { Net.requestGameMode(VoxelEngine.getEngine(), mode.equals("survival") ? "survival" : "creative"); }

    public static void setLocal(String mode) {
        Settings.gameMode = mode.equals("survival") ? "survival" : "creative";
        if (!Settings.isCreative()) flying = false;
        else {
            VoxelEngine e = VoxelEngine.getEngine();
            if (e != null && e.getLocalPlayer() != null) { e.getLocalPlayer().health = 20f; e.getLocalPlayer().dead = false; }
        }
        Settings.save();
    }

    public static String label() { return Lang.t(Settings.isCreative() ? "gamemode.creative" : "gamemode.survival"); }

    public static boolean sneaking(VoxelEngine e) {
        return e.getCamera() != null && e.getCamera().getInputHandler() != null && e.getCamera().getInputHandler().isCrouchPressed();
    }

    /** While sneaking on the ground, horizontal movement that would leave the edge is cancelled (bridging). */
    public static double[] sneakClamp(double dx, double dz) {
        VoxelEngine e = VoxelEngine.getEngine();
        if (e == null || !e.isWorldActive() || flying || e.getPhysics() == null || !e.getPhysics().isOnGround() || !sneaking(e)) return new double[]{dx, dz};
        Vector3d pos = e.getCamera().getPosition();
        World world = e.getWorld();
        if (!supported(world, pos.x, pos.y, pos.z)) return new double[]{dx, dz};
        if (dx != 0 && !supported(world, pos.x + dx, pos.y, pos.z)) dx = 0;
        if (dz != 0 && !supported(world, pos.x, pos.y, pos.z + dz)) dz = 0;
        if (dx == 0 && dz == 0) return new double[]{0, 0};
        if (!supported(world, pos.x + dx, pos.y, pos.z + dz)) { dx = 0; dz = 0; }
        return new double[]{dx, dz};
    }

    private static boolean supported(World world, double x, double y, double z) {
        int by = (int) Math.floor(y - 0.05);
        if (by < 0) return true;
        int x0 = (int) Math.floor(x - 0.3), x1 = (int) Math.floor(x + 0.3), z0 = (int) Math.floor(z - 0.3), z1 = (int) Math.floor(z + 0.3);
        for (int bx = x0; bx <= x1; bx++) for (int bz = z0; bz <= z1; bz++) {
            try { if (BlockType.fromId(BlockState.typeId(world.getState(bx, by, bz))).isSolid()) return true; }
            catch (Exception ignored) { return true; }
        }
        return false;
    }

    public static void tick(VoxelEngine e) {
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        if (!e.isWorldActive() || e.getCamera() == null || e.getPhysics() == null) { flying = false; fallStart = Double.NaN; return; }
        long window = UIManager.get().getWindow();
        boolean gui = UIManager.get().hasActiveGUIs();
        boolean space = !gui && Keybinds.down(window, "jump");
        boolean down = !gui && Keybinds.down(window, "sneak");
        PhysicsEngine physics = e.getPhysics();
        Vector3d pos = e.getCamera().getPosition();

        if (!Settings.isCreative()) flying = false;
        else if (space && !spaceWas) {
            if (now - lastSpaceTap < 300_000_000L) { flying = !flying; lastSpaceTap = 0; physics.resetFall(); }
            else lastSpaceTap = now;
        }
        spaceWas = space;

        if (flying) {
            double speed = 6.0 * Settings.flySpeed, dy = 0;
            if (space) dy += speed * dt;
            if (down) dy -= speed * dt;
            // Horizontal boost: the engine moves at walking speed, Minecraft flight is ~11 m/s.
            float fwd = (key(window, Keybinds.key("forward"), gui) ? 1 : 0) - (key(window, Keybinds.key("back"), gui) ? 1 : 0);
            float strafe = (key(window, Keybinds.key("right"), gui) ? 1 : 0) - (key(window, Keybinds.key("left"), gui) ? 1 : 0);
            if (fwd != 0 || strafe != 0) {
                double len = Math.sqrt(fwd * fwd + strafe * strafe);
                double yaw = Math.toRadians(e.getCamera().getYaw());
                double base = down ? 1.4 : (key(window, Keybinds.key("sprint"), gui) ? 7.0 : 5.0);
                double extra = Math.max(0, 11.0 * Settings.flySpeed - base) * dt;
                double dx = (Math.sin(yaw) * fwd + Math.cos(yaw) * strafe) / len * extra;
                double dz = (-Math.cos(yaw) * fwd + Math.sin(yaw) * strafe) / len * extra;
                physics.moveHorizontal(dx, dz, down ? 1.0 : 1.8);
            }
            if (dy != 0) {
                double ny = pos.y + dy;
                if (canOccupy(e.getWorld(), pos.x, ny, pos.z, down ? 1.0 : 1.8)) pos.y = ny;
                else if (dy < 0) flying = false;      // touched the ground while descending
            }
            fallStart = Double.NaN;
            return;
        }

        if (Settings.isCreative()) { fallStart = Double.NaN; return; }
        if (!physics.isOnGround()) {
            if (Double.isNaN(fallStart) || pos.y > fallStart) fallStart = pos.y;
        } else {
            if (!Double.isNaN(fallStart)) {
                double distance = fallStart - pos.y;
                if (distance > 3.0 && physics.getSubmergedLiquid() == null) Survival.damage(e, (float) Math.floor(distance - 3.0), true, "fall");
            }
            fallStart = Double.NaN;
        }
    }

    private static boolean key(long window, int key, boolean gui) { return !gui && GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS; }

    private static boolean canOccupy(World world, double x, double y, double z, double height) {
        if (world == null) return true;
        int x0 = (int) Math.floor(x - 0.3), x1 = (int) Math.floor(x + 0.3);
        int z0 = (int) Math.floor(z - 0.3), z1 = (int) Math.floor(z + 0.3);
        int y0 = (int) Math.floor(y), y1 = (int) Math.floor(y + height - 0.001);
        for (int bx = x0; bx <= x1; bx++) for (int by = y0; by <= y1; by++) for (int bz = z0; bz <= z1; bz++) {
            if (by < 0 || by >= 512) return false;
            try {
                long state = world.getState(bx, by, bz);
                if (BlockType.fromId(BlockState.typeId(state)).isSolid()) return false;
            } catch (Exception ignored) { }
        }
        return true;
    }
}
