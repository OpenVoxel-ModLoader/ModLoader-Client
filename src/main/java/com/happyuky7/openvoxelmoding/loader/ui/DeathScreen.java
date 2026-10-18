package com.happyuky7.openvoxelmoding.loader.ui;

import com.happyuky7.openvoxelmoding.loader.Lang;

import com.happyuky7.openvoxelmoding.loader.core.Settings;
import com.happyuky7.openvoxelmoding.loader.gameplay.Survival;

import net.openvoxel.engine.VoxelEngine;
import net.openvoxel.engine.entity.PlayerEntity;
import net.openvoxel.engine.gui.GUI;
import net.openvoxel.engine.ui.GLUIRenderer;
import net.openvoxel.engine.ui.UIManager;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;

/**
 * "You died!" screen with Respawn / Title buttons. Opening it pauses mouse look. Respawning asks the
 * server (for deaths it knows about) and also restores the player locally, because survival damage
 * from ModLoader (falls, mobs) only exists on this client.
 */
public final class DeathScreen extends GUI {
    public static volatile String cause = "generic";
    private static Field spawnField;
    private final VoxelEngine engine;
    private final long openedNs = System.nanoTime();
    private int width, height;
    private static final float BW = 300, BH = 40;

    DeathScreen(VoxelEngine engine) { this.engine = engine; }

    @Override public void onOpen() {
        int[] s = Mc.windowSize(); width = s[0]; height = s[1];
        if (engine.getCamera() != null && engine.getCamera().getInputHandler() != null) engine.getCamera().getInputHandler().paused = true;
    }

    @Override public void onTick(long ticks) { int[] s = Mc.windowSize(); width = s[0]; height = s[1]; }

    private boolean ready() { return System.nanoTime() - openedNs > 1_000_000_000L; }   // like Minecraft: buttons enable after a moment
    private float respawnY() { return height * 0.55f; }
    private float titleY() { return respawnY() + BH + 10; }
    private float bx() { return (width - BW) / 2f; }

    @Override public void render() {
        GLUIRenderer.setup2DRendering(width, height);
        try {
            GLUIRenderer.drawGradientV(0, 0, width, height, 0.35f, 0.02f, 0.02f, 0.55f, 0.12f, 0f, 0f, 0.75f);
            String title = Lang.t("death.title");
            GL11.glPushMatrix();
            GL11.glTranslatef(width / 2f, height * 0.30f, 0);
            GL11.glScalef(2.6f, 2.6f, 1f);
            Mc.centered(title, 0, 0, new float[]{1f, 1f, 1f});
            GL11.glPopMatrix();
            PlayerEntity p = engine.getLocalPlayer();
            String name = p != null && p.username != null && !p.username.isEmpty() ? p.username : "Player";
            Mc.centered(Lang.t("death." + cause, name), width / 2f, height * 0.30f + 48, Mc.TEXT);
            if (!Settings.isCreative() && Survival.droppedOnDeath > 0) Mc.centered(Lang.t("death.dropped", Survival.droppedOnDeath), width / 2f, height * 0.30f + 74, Mc.TEXT_DIM);
            float[] m = Mc.mouse();
            boolean ok = ready();
            Mc.button(bx(), respawnY(), BW, BH, Lang.t("death.respawn"), ok && GLUIRenderer.isPointInRect(m[0], m[1], bx(), respawnY(), BW, BH), ok);
            Mc.button(bx(), titleY(), BW, BH, Lang.t("death.titleScreen"), ok && GLUIRenderer.isPointInRect(m[0], m[1], bx(), titleY(), BW, BH), ok);
        } finally { GLUIRenderer.restore3DRendering(); }
    }

    @Override public void onMouseClick(int mx, int my, int button) {
        if (button != 0 || !ready()) return;
        if (GLUIRenderer.isPointInRect(mx, my, bx(), respawnY(), BW, BH)) respawn();
        else if (GLUIRenderer.isPointInRect(mx, my, bx(), titleY(), BW, BH)) toTitle();
    }

    @Override public void onKeyPress(int key) {
        if ((key == GLFW.GLFW_KEY_R || key == GLFW.GLFW_KEY_ENTER) && ready()) respawn();
        // Escape does nothing: you have to choose, like Minecraft.
    }

    private void respawn() {
        BuiltinUI.sound().play("click");
        try { engine.respawnLocalPlayer(); } catch (Throwable ignored) { }      // server-side deaths
        PlayerEntity p = engine.getLocalPlayer();
        if (p != null) { p.health = p.maxHealth; p.dead = false; }
        Survival.reset();
        Vector3d spawn = spawnPoint();
        if (spawn != null && engine.getCamera() != null) engine.applyServerSpawn(spawn.x, spawn.y, spawn.z, false);
        close();
    }

    private void toTitle() {
        BuiltinUI.sound().play("click");
        PlayerEntity p = engine.getLocalPlayer();
        if (p != null) { p.health = p.maxHealth; p.dead = false; }
        Survival.reset();
        close();
        Runnable h = engine.getExitToMenuHandler();
        if (h != null) h.run(); else UIManager.get().closeAllGUIs();
    }

    private void close() {
        UIManager.get().closeTopGUI();
    }

    @Override public void onClose() {
        if (engine.getCamera() != null && engine.getCamera().getInputHandler() != null && !UIManager.get().hasActiveGUIs()) engine.getCamera().getInputHandler().paused = false;
    }

    private Vector3d spawnPoint() {
        try {
            if (spawnField == null) { spawnField = VoxelEngine.class.getDeclaredField("spawnPoint"); spawnField.setAccessible(true); }
            return new Vector3d((Vector3d) spawnField.get(engine));
        } catch (Exception e) { return null; }
    }
}
