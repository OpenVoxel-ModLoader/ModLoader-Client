package com.happyuky7.openvoxelmoding.loader.cosmetics;

import com.happyuky7.openvoxelmoding.loader.core.Agent;
import com.happyuky7.openvoxelmoding.loader.core.Settings;
import com.happyuky7.openvoxelmoding.loader.ui.HandRenderer;

import net.openvoxel.engine.rendering.Texture;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Cape and elytra wings drawn on the local player in third person. Uses a Minecraft
 * cape texture (64x32: cape at 0,0 and elytra at 22,0) from config/cape.png or a
 * generated default. Local cosmetics only; the server model is not changed.
 */
public final class Capes {
    private Capes() {}

    private static Texture texture;
    private static boolean tried;

    static Path file() { return Agent.instanceDir().resolve("config").resolve("cape.png"); }

    public static void reload() { if (texture != null) texture.cleanup(); texture = null; tried = false; }

    private static Texture texture() {
        if (tried) return texture;
        tried = true;
        try {
            byte[] png = Files.isRegularFile(file()) ? Files.readAllBytes(file()) : defaultTexture();
            texture = new Texture(png);
        } catch (Exception error) { System.err.println("[HappyCapes] " + error); texture = null; }
        return texture;
    }

    /** Red cape with a lighter stripe and gray elytra wings, in the vanilla cape layout. */
    private static byte[] defaultTexture() throws Exception {
        BufferedImage img = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 17; y++) for (int x = 0; x < 22; x++) img.setRGB(x, y, (y > 1 && y < 16 && (x + y) % 7 == 0) ? 0xFFB83232 : 0xFF8A1E1E);
        for (int y = 0; y < 22; y++) for (int x = 22; x < 46; x++) img.setRGB(x, y, ((x - 22) / 3 + y / 3) % 2 == 0 ? 0xFF6E6E78 : 0xFF5A5A64);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /** Must be called with the model matrix already at the player's feet, rotated by body yaw. */
    public static void render(float walkAmount, float walkPhase, boolean flying, boolean crouching, float bright) {
        if (!Settings.cape && !Settings.elytra) return;
        Texture tex = texture();
        if (tex == null) return;
        tex.bind();
        GL11.glPushMatrix();
        GL11.glTranslatef(0f, 1.5f, 0.125f);            // top of the torso, back side (+z)
        if (crouching) GL11.glRotatef(-25f, 1, 0, 0);
        if (Settings.elytra) {
            float spread = flying ? 55f : 12f, tilt = flying ? 5f : 18f;
            float flap = flying ? (float) Math.sin(System.nanoTime() / 2.5e8) * 6f : 0f;
            GL11.glPushMatrix();
            GL11.glRotatef(-tilt, 1, 0, 0);
            GL11.glRotatef(-(spread + flap), 0, 0, 1);
            HandRenderer.drawBox(-10, -20, 0, 0, 0, 2, 22, 0, 0f, 1f, 64, 32, bright);
            GL11.glPopMatrix();
            GL11.glPushMatrix();
            GL11.glRotatef(-tilt, 1, 0, 0);
            GL11.glRotatef(spread + flap, 0, 0, 1);
            HandRenderer.drawBox(0, -20, 0, 10, 0, 2, 22, 0, 0f, 1f, 64, 32, bright);
            GL11.glPopMatrix();
        } else {
            float swing = (float) Math.abs(Math.sin(walkPhase)) * 18f * walkAmount;
            GL11.glRotatef(-(6f + swing + (flying ? 40f : 0f)), 1, 0, 0);
            HandRenderer.drawBox(-5, -16, 0, 5, 0, 1, 0, 0, 0f, 1f, 64, 32, bright);
        }
        GL11.glPopMatrix();
    }
}
