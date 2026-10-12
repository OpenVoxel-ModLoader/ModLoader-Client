package com.happyuky7.openvoxelmoding.loader;

import net.openvoxel.engine.VoxelEngine;
import net.openvoxel.engine.light.LightEngine;
import net.openvoxel.engine.rendering.Camera;
import net.openvoxel.engine.rendering.Renderer;
import net.openvoxel.engine.rendering.Texture;
import net.openvoxel.engine.ui.UIManager;
import net.openvoxel.engine.world.World;
import net.openvoxel.engine.world.block.BlockState;
import net.openvoxel.engine.world.block.BlockType;
import net.openvoxel.engine.world.block.model.BlockModel;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/** First-person held block / item / arm, and the held item + cape on the third-person model. */
final class HandRenderer {
    private HandRenderer() {}

    private static final FloatBuffer MATRIX = MemoryUtil.memAllocFloat(16);
    private static final float[] FACE_SHADE = {1.0f, 0.5f, 0.6f, 0.6f, 0.8f, 0.8f};
    private static float phase, amount;
    private static double lastX = Double.NaN, lastZ;
    private static long lastNs, swingStart;
    private static boolean leftWas, rightWas, reported;
    private static Field bodyYawField, walkAmountField, walkPhaseField;

    static void reportOnce(Throwable error) {
        if (reported) return;
        reported = true;
        System.err.println("[HappyHand] disabled after error: " + error);
        error.printStackTrace();
    }

    /** Called from the frame hook: walk bobbing and swing detection. */
    static void tick(VoxelEngine e) {
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        if (!e.isWorldActive() || e.getCamera() == null) { lastX = Double.NaN; amount = 0; return; }
        Vector3d p = e.getCamera().getPosition();
        double dist = Double.isNaN(lastX) ? 0 : Math.hypot(p.x - lastX, p.z - lastZ);
        lastX = p.x; lastZ = p.z;
        float speed = dt > 0 ? (float) (dist / dt) : 0;
        float target = Math.min(1f, speed / 4.5f);
        amount += (target - amount) * Math.min(1f, dt * 8f);
        phase += (float) dist * 2.2f;
        if (UIManager.get().hasActiveGUIs()) return;
        long window = UIManager.get().getWindow();
        boolean left = GLFW.glfwGetMouseButton(window, 0) == GLFW.GLFW_PRESS;
        boolean right = GLFW.glfwGetMouseButton(window, 1) == GLFW.GLFW_PRESS;
        if ((left && !leftWas) || (right && !rightWas)) swingStart = now;
        leftWas = left; rightWas = right;
    }

    private static float swingProgress() {
        if (swingStart == 0) return -1;
        float t = (System.nanoTime() - swingStart) / 0.26e9f;
        return t >= 1f ? -1 : t;
    }

    static void render(Renderer renderer) {
        VoxelEngine e = VoxelEngine.getEngine();
        if (e == null || !e.isWorldActive()) return;
        Camera cam = e.getCamera();
        if (cam == null || UIManager.get().hasActiveGUIs()) return;
        renderDrops(renderer, e, cam);
        if (e.getLocalPlayer() != null && e.getLocalPlayer().dead) return;
        if (cam.isThirdPerson()) renderThirdPerson(renderer, e, cam);
        else renderFirstPerson(e, renderer, cam);
    }

    private static void renderDrops(Renderer renderer, VoxelEngine e, Camera cam) {
        if (Drops.count() == 0 && Tiles.all().isEmpty()) return;
        GlState state = new GlState();
        loadMatrices(cam, true);
        state.save();
        try { Tiles.render(renderer, e, cam); Drops.render(renderer, e, cam); }
        finally { state.restore(); popMatrices(); }
    }

    static float brightnessAt(VoxelEngine e, Renderer renderer, double x, double y, double z) {
        try {
            World w = e.getWorld();
            LightEngine le = VoxelEngine.getLightEngine();
            if (w == null || le == null) return 1f;
            int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
            return Math.max(Math.max(le.sampleSkyLight01(w, bx, by, bz, renderer.getDayFactor()), le.sampleBlockLight01(w, bx, by, bz)), 0.15f);
        } catch (Exception ignored) { return 1f; }
    }

    private static class GlState {
        boolean cull, blend, depth, alpha, tex2d; int depthFunc;
        void save() {
            cull = GL11.glIsEnabled(GL11.GL_CULL_FACE); blend = GL11.glIsEnabled(GL11.GL_BLEND); depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST); tex2d = GL11.glIsEnabled(GL11.GL_TEXTURE_2D); depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1f);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }
        void restore() {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GL11.glDepthFunc(depthFunc);
            if (!depth) GL11.glDisable(GL11.GL_DEPTH_TEST);
            if (cull) GL11.glEnable(GL11.GL_CULL_FACE);
            if (blend) GL11.glEnable(GL11.GL_BLEND);
            if (!alpha) GL11.glDisable(GL11.GL_ALPHA_TEST);
            if (!tex2d) GL11.glDisable(GL11.GL_TEXTURE_2D);
        }
    }

    private static void loadMatrices(Camera cam, boolean withView) {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        MATRIX.clear();
        cam.getProjectionMatrix().get(MATRIX);
        GL11.glLoadMatrixf(MATRIX);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        if (withView) { MATRIX.clear(); cam.getViewMatrix().get(MATRIX); GL11.glLoadMatrixf(MATRIX); }
        else GL11.glLoadIdentity();
    }

    private static void popMatrices() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
    }

    private static void renderFirstPerson(VoxelEngine e, Renderer renderer, Camera cam) {
        Items.Stack held = BuiltinUI.held();
        float bright = brightness(e, renderer, cam);
        GlState state = new GlState();
        loadMatrices(cam, false);
        state.save();
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        try {
            GL11.glTranslatef((float) Math.sin(phase) * 0.045f * amount, -(float) Math.abs(Math.cos(phase)) * 0.045f * amount, 0);
            float s = swingProgress();
            if (s >= 0) {
                float f = (float) Math.sin(Math.sqrt(s) * Math.PI);
                GL11.glTranslatef(-f * 0.28f, (float) Math.sin(Math.sqrt(s) * Math.PI * 2) * 0.18f, -f * 0.22f);
                GL11.glRotatef(-f * 22f, 1, 0, 0);
                GL11.glRotatef(f * 18f, 0, 0, 1);
            }
            if (held != null && held.isBlock()) {
                GL11.glTranslatef(0.60f, -0.56f, -0.92f);
                GL11.glRotatef(10f, 1, 0, 0);
                GL11.glRotatef(45f, 0, 1, 0);
                GL11.glScalef(0.42f, 0.42f, 0.42f);
                GL11.glTranslatef(-0.5f, -0.5f, -0.5f);
                drawBlock(held.block, BlockState.make(held.block.getId()), bright);
            } else if (held != null) {
                float eat = Survival.eating;
                if (eat > 0f) {
                    // bring the food to the mouth with a nibble jitter
                    float f = Math.min(1f, eat * 3f);
                    float jitter = (float) Math.sin(System.nanoTime() / 2.5e7) * 0.02f * f;
                    GL11.glTranslatef(-0.32f * f + jitter, 0.22f * f, 0.28f * f);
                    GL11.glRotatef(-35f * f, 0, 1, 0);
                    GL11.glRotatef(20f * f, 1, 0, 0);
                }
                GL11.glTranslatef(0.62f, -0.48f, -0.95f);
                GL11.glRotatef(-25f, 0, 1, 0);
                GL11.glRotatef(-12f, 0, 0, 1);
                drawItem(held.item, 0.62f, bright);
            } else drawArm(bright);
        } finally { state.restore(); popMatrices(); }
    }

    /** Held item in the right hand plus cape/elytra, matching the engine's third-person player model. */
    private static void renderThirdPerson(Renderer renderer, VoxelEngine e, Camera cam) {
        Items.Stack held = BuiltinUI.held();
        float bodyYaw = 0, walkAmount = 0, walkPhase = 0;
        try {
            if (bodyYawField == null) {
                bodyYawField = Renderer.class.getDeclaredField("playerBodyYaw"); bodyYawField.setAccessible(true);
                walkAmountField = Renderer.class.getDeclaredField("walkAmount"); walkAmountField.setAccessible(true);
                walkPhaseField = Renderer.class.getDeclaredField("walkPhase"); walkPhaseField.setAccessible(true);
            }
            bodyYaw = bodyYawField.getFloat(renderer);
            walkAmount = walkAmountField.getFloat(renderer);
            walkPhase = walkPhaseField.getFloat(renderer);
        } catch (Exception ignored) { }
        float bright = brightness(e, renderer, cam);
        GlState state = new GlState();
        loadMatrices(cam, true);
        state.save();
        try {
            GL11.glTranslatef(0f, -1.6f, 0f);               // REL_PLAYER_POS: feet relative to the camera
            GL11.glRotatef(bodyYaw, 0, 1, 0);
            boolean crouch = GameMode.sneaking(e);
            Capes.render(walkAmount, walkPhase, GameMode.flying, crouch, bright);
            if (held != null) {
                float swing = (float) Math.sin(walkPhase) * 0.8f * walkAmount;
                float angle = -swing;
                float s = swingProgress();
                if (s >= 0) angle = 1.9f * (float) Math.sin(Math.PI * s);
                GL11.glTranslatef(0.375f, 1.4375f, 0f);         // right arm pivot
                GL11.glRotatef((float) Math.toDegrees(angle), 1, 0, 0);
                GL11.glTranslatef(0f, -0.62f, -0.1f);           // hand
                if (held.isBlock()) {
                    GL11.glScalef(0.32f, 0.32f, 0.32f);
                    GL11.glTranslatef(-0.5f, -0.5f, -0.5f);
                    drawBlock(held.block, BlockState.make(held.block.getId()), bright);
                } else {
                    GL11.glRotatef(-90f, 1, 0, 0);
                    GL11.glRotatef(90f, 0, 1, 0);
                    GL11.glTranslatef(-0.2f, -0.1f, 0f);
                    drawItem(held.item, 0.5f, bright);
                }
            }
        } finally { state.restore(); popMatrices(); }
    }

    private static float brightness(VoxelEngine e, Renderer renderer, Camera cam) {
        try {
            World w = e.getWorld();
            LightEngine le = VoxelEngine.getLightEngine();
            if (w == null || le == null) return 1f;
            Vector3d p = cam.getPosition();
            int bx = (int) Math.floor(p.x), by = (int) Math.floor(p.y + 1.2), bz = (int) Math.floor(p.z);
            float sky = le.sampleSkyLight01(w, bx, by, bz, renderer.getDayFactor());
            float block = le.sampleBlockLight01(w, bx, by, bz);
            return Math.max(Math.max(sky, block), 0.15f);
        } catch (Exception ignored) { return 1f; }
    }

    /** Draws the block's real quads in a unit cube at the origin. */
    static void drawBlock(BlockType type, long state, float bright) {
        float[][] quads;
        Texture[] quadTextures = null;
        BlockModel model = type.getModel(state);
        if (model != null) { quads = model.quads; quadTextures = model.textures; }
        else quads = type.getShape().quads(state);
        if (quads == null || quads.length == 0) return;
        for (int qi = 0; qi < quads.length; qi++) {
            float[] q = quads[qi];
            if (q.length < 30) continue;
            Texture tex = quadTextures != null && qi < quadTextures.length ? quadTextures[qi] : type.getTextureForFace(qi < 6 ? qi : 2, state);
            if (tex == null) tex = type.getTextureForFace(0, state);
            if (tex == null) continue;
            float shade = (qi < FACE_SHADE.length ? FACE_SHADE[qi] : 0.72f) * bright;
            tex.bind();
            GL11.glColor4f(shade, shade, shade, 1f);
            GL11.glBegin(GL11.GL_TRIANGLES);
            for (int v = 0; v < 6; v++) {
                int o = v * 5;
                GL11.glTexCoord2f(q[o + 3], q[o + 4]);
                GL11.glVertex3f(q[o], q[o + 1], q[o + 2]);
            }
            GL11.glEnd();
        }
    }

    /** Flat sprite quad (size x size) centred at the origin, facing +z. */
    static void drawItem(Items.Item item, float size, float bright) {
        Texture tex = item.texture();
        if (tex == null) return;
        tex.bind();
        GL11.glColor4f(bright, bright, bright, 1f);
        float h = size / 2f;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 1); GL11.glVertex3f(-h, h, 0);
        GL11.glTexCoord2f(1, 1); GL11.glVertex3f(h, h, 0);
        GL11.glTexCoord2f(1, 0); GL11.glVertex3f(h, -h, 0);
        GL11.glTexCoord2f(0, 0); GL11.glVertex3f(-h, -h, 0);
        GL11.glEnd();
    }

    private static void drawArm(float bright) {
        Texture skin = Skins.handTexture();
        if (skin == null) return;
        boolean slim = Skins.handSlim();
        float texW = Skins.handTexWidth(), scale = texW / 64f;
        float half = slim ? 1.5f : 2f;
        GL11.glTranslatef(0.58f, -0.40f, -0.95f);
        GL11.glRotatef(32f, 0, 0, 1);
        GL11.glRotatef(35f, 0, 1, 0);
        GL11.glRotatef(22f, 1, 0, 0);
        skin.bind();
        drawBox(-half, -12, -2, half, 0, 2, 40 * scale, 16 * scale, 0f, scale, texW, texW, bright);
        drawBox(-half, -12, -2, half, 0, 2, 40 * scale, 32 * scale, 0.25f, scale, texW, texW, bright);
    }

    /** Immediate-mode copy of the engine's ModelBuilder box layout (standard skin UV map). */
    static void drawBox(float x0, float y0, float z0, float x1, float y1, float z1, float u, float v, float inflate, float pxPerUnit, float texW, float texH, float bright) {
        List<float[]> tris = new ArrayList<>();
        box(tris, x0, y0, z0, x1, y1, z1, u, v, inflate, pxPerUnit, texW, texH);
        GL11.glBegin(GL11.GL_TRIANGLES);
        for (float[] t : tris) {
            float c = t[5] * bright;
            GL11.glColor4f(c, c, c, 1f);
            GL11.glTexCoord2f(t[3], t[4]);
            GL11.glVertex3f(t[0], t[1], t[2]);
        }
        GL11.glEnd();
    }

    private static void box(List<float[]> out, float x0, float y0, float z0, float x1, float y1, float z1, float uvU, float uvV, float inflate, float scale, float texW, float texH) {
        float w = (x1 - x0) * scale, h = (y1 - y0) * scale, d = (z1 - z0) * scale;
        x0 -= inflate; y0 -= inflate; z0 -= inflate; x1 += inflate; y1 += inflate; z1 += inflate;
        float ax0 = x0 / 16f, ay0 = y0 / 16f, az0 = z0 / 16f, ax1 = x1 / 16f, ay1 = y1 / 16f, az1 = z1 / 16f;
        face(out, ax0, ay1, az0, ax1, ay1, az1, uvU + d, uvV, w, d, 0, 1.0f, texW, texH);
        face(out, ax0, ay0, az0, ax1, ay0, az1, uvU + d + w, uvV, w, d, 0, 0.5f, texW, texH);
        face(out, ax0, ay0, az0, ax1, ay1, az0, uvU + d, uvV + d, w, h, 1, 0.6f, texW, texH);
        face(out, ax0, ay0, az1, ax1, ay1, az1, uvU + 2f * d + w, uvV + d, w, h, 1, 0.6f, texW, texH);
        face(out, ax0, ay0, az0, ax0, ay1, az1, uvU, uvV + d, d, h, 2, 0.8f, texW, texH);
        face(out, ax1, ay0, az0, ax1, ay1, az1, uvU + d + w, uvV + d, d, h, 2, 0.8f, texW, texH);
    }

    private static void face(List<float[]> out, float x0, float y0, float z0, float x1, float y1, float z1, float u, float vv, float w, float h, int axis, float shade, float texW, float texH) {
        float u0 = u / texW, u1 = (u + w) / texW, v0 = (texH - vv) / texH, v1 = (texH - vv - h) / texH;
        if (axis == 2) { float t = u0; u0 = u1; u1 = t; }
        float[][] c;
        if (axis == 0) c = new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}};
        else if (axis == 1) c = new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}};
        else c = new float[][]{{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}};
        vert(out, c[3], u0, v0, shade); vert(out, c[0], u0, v1, shade); vert(out, c[1], u1, v1, shade);
        vert(out, c[3], u0, v0, shade); vert(out, c[1], u1, v1, shade); vert(out, c[2], u1, v0, shade);
    }

    private static void vert(List<float[]> out, float[] p, float u, float v, float shade) { out.add(new float[]{p[0], p[1], p[2], u, v, shade}); }
}
