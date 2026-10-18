package com.happyuky7.openvoxelmoding.loader.gameplay;

import com.happyuky7.openvoxelmoding.loader.Lang;




import com.happyuky7.openvoxelmoding.loader.core.Settings;
import com.happyuky7.openvoxelmoding.loader.item.Drops;
import com.happyuky7.openvoxelmoding.loader.item.Items;
import com.happyuky7.openvoxelmoding.loader.ui.BuiltinUI;
import com.happyuky7.openvoxelmoding.loader.ui.DeathScreen;
import com.happyuky7.openvoxelmoding.loader.ui.Keybinds;

import net.openvoxel.engine.VoxelEngine;
import net.openvoxel.engine.entity.PlayerEntity;
import net.openvoxel.engine.physics.PhysicsEngine;
import net.openvoxel.engine.ui.ChatHud;
import net.openvoxel.engine.ui.GLUIRenderer;
import net.openvoxel.engine.ui.UIManager;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;

/** Survival mechanics on top of the engine's health: hunger, regeneration, armor, eating and the HUD bars. */
public final class Survival {
    private Survival() {}

    public static float hunger = 20f, exhaustion;
    /** 0..1 while holding right click with food (drives the first-person eating animation). */
    public static float eating;
    private static long lastCrunchNs;
    public static final Items.Stack[] ARMOR = new Items.Stack[4]; // helmet, chestplate, leggings, boots
    private static long lastRegenNs, lastStarveNs, lastEatNs;
    private static boolean rightWas, wasOnGround;
    private static double lastX = Double.NaN, lastZ;

    private static final int[] HEART = {102, 255, 255, 255, 126, 60, 24, 0};
    private static final int[] DRUMSTICK = {0b00000110, 0b00001111, 0b00011111, 0b00111110, 0b01111100, 0b11111000, 0b11110000, 0b01100000};
    private static final int[] ARMOR_ICON = {0b11000011, 0b11100111, 0b11111111, 0b01111110, 0b00111100, 0b00111100, 0b00111100, 0b00011000};

    /** How many stacks were dropped at the last death (shown on the death screen). */
    public static int droppedOnDeath;
    /** Set by die(): the death screen then knows the inventory was already dropped. */
    public static boolean diedLocally;

    public static void reset() { hunger = 20f; exhaustion = 0; lastX = Double.NaN; eating = 0f; }

    public static int armorPoints() {
        int p = 0;
        for (Items.Stack s : ARMOR) if (s != null && s.item != null) p += s.item.value;
        return Math.min(20, p);
    }

    static float applyArmor(float damage) { return damage * (1f - armorPoints() * 0.04f); }

    public static void tick(VoxelEngine e, float dt) {
        if (!e.isWorldActive() || e.getCamera() == null || e.getLocalPlayer() == null) { lastX = Double.NaN; return; }
        PlayerEntity p = e.getLocalPlayer();
        if (Settings.isCreative()) { hunger = 20f; exhaustion = 0; return; }
        if (p.dead) return;
        long now = System.nanoTime();
        PhysicsEngine physics = e.getPhysics();
        Vector3d pos = e.getCamera().getPosition();
        long window = UIManager.get().getWindow();
        boolean gui = UIManager.get().hasActiveGUIs();
        boolean sprint = !gui && Keybinds.down(window, "sprint");
        if (!Double.isNaN(lastX) && sprint) exhaustion += Math.hypot(pos.x - lastX, pos.z - lastZ) * 0.1;
        lastX = pos.x; lastZ = pos.z;
        boolean onGround = physics != null && physics.isOnGround();
        if (wasOnGround && !onGround && !GameMode.flying && !gui && Keybinds.down(window, "jump")) exhaustion += sprint ? 0.2f : 0.05f;
        wasOnGround = onGround;
        if (hunger >= 18f && p.health < p.maxHealth && now - lastRegenNs > 4_000_000_000L) {
            p.health = Math.min(p.maxHealth, p.health + 1f);
            exhaustion += 6f;
            lastRegenNs = now;
        }
        while (exhaustion >= 4f) { exhaustion -= 4f; hunger = Math.max(0f, hunger - 1f); }
        if (hunger <= 0f && p.health > 1f && now - lastStarveNs > 4_000_000_000L) { p.health = Math.max(1f, p.health - 1f); lastStarveNs = now; }
        // eating: hold right click with food in hand for 1.6 s, like Minecraft
        boolean right = !gui && GLFW.glfwGetMouseButton(window, 1) == GLFW.GLFW_PRESS;
        Items.Stack held = BuiltinUI.held();
        boolean food = held != null && held.item != null && held.item.kind == Items.Kind.FOOD && (hunger < 20f || held.item.id.equals("golden_apple"));
        if (right && food && now - lastEatNs > 400_000_000L) {
            eating = Math.min(1f, eating + dt / 1.6f);
            if (now - lastCrunchNs > 320_000_000L) { BuiltinUI.sound().play("eat"); lastCrunchNs = now; }
            if (eating >= 1f) {
                hunger = Math.min(20f, hunger + held.item.value);
                if (held.item.id.equals("golden_apple")) p.health = Math.min(p.maxHealth, p.health + 4f);
                lastEatNs = now;
                eating = 0f;
                BuiltinUI.sound().play("pop");
                held.count--;
                if (held.count <= 0) { BuiltinUI.HOTBAR[BuiltinUI.selected] = null; BuiltinUI.select(e, BuiltinUI.selected); }
            }
        } else eating = 0f;
        rightWas = right;
    }

    static void damage(VoxelEngine e, float amount, boolean useArmor) { damage(e, amount, useArmor, "generic"); }

    /** {@code cause}: fall, zombie, starve, generic (used for the death message). */
    static void damage(VoxelEngine e, float amount, boolean useArmor, String cause) {
        PlayerEntity p = e.getLocalPlayer();
        if (p == null || p.dead || amount <= 0 || Settings.isCreative()) return;
        if (useArmor) amount = applyArmor(amount);
        p.health = Math.max(0f, p.health - amount);
        BuiltinUI.sound().play("hurt");
        if (p.health <= 0f) die(e, cause);
    }

    /** Local death: message, inventory dropped where you died (survival), death screen opens next frame. */
    static void die(VoxelEngine e, String cause) {
        PlayerEntity p = e.getLocalPlayer();
        if (p == null) return;
        p.dead = true;
        diedLocally = true;
        DeathScreen.cause = cause;
        String name = p.username != null && !p.username.isEmpty() ? p.username : "Player";
        ChatHud.addLine("&c" + Lang.t("death." + cause, name));
        dropEverything(e);
    }

    public static void dropEverything(VoxelEngine e) {
        droppedOnDeath = 0;
        if (Settings.isCreative() || e.getCamera() == null) return;
        Vector3d pos = e.getCamera().getPosition();
        int bx = (int) Math.floor(pos.x), by = (int) Math.floor(pos.y), bz = (int) Math.floor(pos.z);
        Items.Stack[][] areas = {BuiltinUI.HOTBAR, BuiltinUI.STORAGE, ARMOR};
        for (Items.Stack[] area : areas) for (int i = 0; i < area.length; i++) {
            if (area[i] == null) continue;
            Drops.spawnDeath(area[i], bx, by, bz);
            area[i] = null;
            droppedOnDeath++;
        }
        BuiltinUI.select(e, BuiltinUI.selected);
    }

    /** Hearts, hunger and armor above the hotbar (survival only). Expects 2D rendering already set up. */
    public static void renderHud(VoxelEngine e, int w, int h) {
        PlayerEntity p = e.getLocalPlayer();
        if (p == null) return;
        float size = 14f, gap = 3f, hotbarW = 9 * 40 + 8 * 4, left = (w - hotbarW) / 2f, right = left + hotbarW;
        float y = h - 48 - 8 - size;
        for (int i = 0; i < 10; i++) {
            float x = left + i * (size + gap);
            drawIcon(HEART, x, y, size, 0.15f, 0.05f, 0.05f, 0.6f, 8);
            float hp = p.health - i * 2f;
            if (hp >= 2f) drawIcon(HEART, x, y, size, 0.85f, 0.12f, 0.15f, 1f, 8);
            else if (hp >= 1f) drawIcon(HEART, x, y, size, 0.85f, 0.12f, 0.15f, 1f, 4);
        }
        for (int i = 0; i < 10; i++) {
            float x = right - size - i * (size + gap);
            drawIcon(DRUMSTICK, x, y, size, 0.12f, 0.08f, 0.04f, 0.6f, 8);
            float food = hunger - i * 2f;
            if (food >= 2f) drawIcon(DRUMSTICK, x, y, size, 0.80f, 0.50f, 0.22f, 1f, 8);
            else if (food >= 1f) drawIcon(DRUMSTICK, x, y, size, 0.80f, 0.50f, 0.22f, 1f, 4);
        }
        int armor = armorPoints();
        if (armor > 0) {
            float ay = y - size - 2;
            for (int i = 0; i < 10; i++) {
                float x = left + i * (size + gap);
                drawIcon(ARMOR_ICON, x, ay, size, 0.2f, 0.2f, 0.2f, 0.6f, 8);
                int a = armor - i * 2;
                if (a >= 2) drawIcon(ARMOR_ICON, x, ay, size, 0.85f, 0.85f, 0.9f, 1f, 8);
                else if (a >= 1) drawIcon(ARMOR_ICON, x, ay, size, 0.85f, 0.85f, 0.9f, 1f, 4);
            }
        }
    }

    private static void drawIcon(int[] rows, float x, float y, float size, float r, float g, float b, float a, int cols) {
        float px = size / 8f;
        for (int row = 0; row < 8; row++) {
            int mask = rows[row];
            int col = 0;
            while (col < cols) {
                if ((mask & (128 >> col)) == 0) { col++; continue; }
                int start = col;
                while (col < cols && (mask & (128 >> col)) != 0) col++;
                GLUIRenderer.drawRect(x + start * px, y + row * px, (col - start) * px, px, r, g, b, a);
            }
        }
    }
}
