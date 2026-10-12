package com.happyuky7.openvoxelmoding.loader;

import net.openvoxel.engine.VoxelEngine;
import net.openvoxel.engine.input.InputHandler;
import net.openvoxel.engine.light.LightEngine;
import net.openvoxel.engine.rendering.Camera;
import net.openvoxel.engine.ui.DebugOverlay;
import net.openvoxel.engine.ui.GLUIRenderer;
import net.openvoxel.engine.world.World;
import net.openvoxel.engine.world.block.BlockState;
import net.openvoxel.engine.world.block.BlockType;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/** Minecraft-style F3 screen replacing the engine's DebugOverlay. */
final class DebugScreen {
    private DebugScreen() {}

    private static final List<String> LEFT = new ArrayList<>(), RIGHT = new ArrayList<>();
    private static long lastRefresh, lastFrame, fpsWindowStart;
    private static int frames, fps;
    private static String gpu, glVersion;
    private static boolean reported;

    static void reportOnce(Throwable error) {
        if (reported) return;
        reported = true;
        System.err.println("[HappyF3] disabled after error: " + error);
        error.printStackTrace();
    }

    static boolean visible() { return DebugOverlay.isVisible() || Settings.debugEnabled; }

    static void render(VoxelEngine engine) {
        long now = System.nanoTime();
        frames++;
        if (now - fpsWindowStart >= 1_000_000_000L) { fps = frames; frames = 0; fpsWindowStart = now; }
        lastFrame = now;
        if (!visible()) return;
        if (gpu == null) {
            try { gpu = GL11.glGetString(GL11.GL_RENDERER); glVersion = GL11.glGetString(GL11.GL_VERSION); } catch (Exception e) { gpu = "?"; glVersion = "?"; }
        }
        if (now - lastRefresh > 100_000_000L) { lastRefresh = now; refresh(engine); }
        int[] size = engine.getCamera() != null ? engine.getCamera().getAspect() : Mc.windowSize();
        GLUIRenderer.setup2DRendering(size[0], size[1]);
        try {
            float lineH = GLUIRenderer.getFontHeight() + 2f, y = 4f;
            for (String s : LEFT) { drawLine(s, 4f, y, false, size[0]); y += lineH; }
            y = 4f;
            for (String s : RIGHT) { drawLine(s, 0, y, true, size[0]); y += lineH; }
        } finally { GLUIRenderer.restore3DRendering(); }
    }

    private static void drawLine(String s, float x, float y, boolean right, int width) {
        if (s.isEmpty()) return;
        float w = GLUIRenderer.getTextWidth(s) + 4f, h = GLUIRenderer.getFontHeight() + 2f;
        if (right) x = width - w - 4f;
        GLUIRenderer.drawPanel(x, y, w, h, 0.25f, 0.25f, 0.25f, 0.5f);
        Mc.text(s, x + 2f, y + GLUIRenderer.getFontHeight() * 0.82f, 0.88f, 0.88f, 0.88f, 1f);
    }

    private static void refresh(VoxelEngine engine) {
        LEFT.clear(); RIGHT.clear();
        LEFT.add("OpenVoxel " + engine.getVersion() + " (HappyModding v8)");
        LEFT.add(fps + " fps  vsync: " + (Settings.vsync ? "on" : "off") + "  render distance: " + Settings.renderDistance + " chunks");
        World world = engine.getWorld();
        Camera cam = engine.getCamera();
        if (world != null && cam != null) {
            Vector3d p = cam.getPosition();
            int bx = (int) Math.floor(p.x), by = (int) Math.floor(p.y), bz = (int) Math.floor(p.z);
            LEFT.add("Chunks loaded: " + world.getChunks().size() + "  Seed: " + world.getSeed());
            LEFT.add("");
            LEFT.add(String.format("XYZ: %.3f / %.5f / %.3f", p.x, p.y, p.z));
            LEFT.add(String.format("Block: %d %d %d", bx, by, bz));
            LEFT.add(String.format("Chunk: %d %d %d in %d %d %d", Math.floorMod(bx, 16), Math.floorMod(by, 16), Math.floorMod(bz, 16), bx >> 4, by >> 4, bz >> 4));
            float yaw = ((cam.getYaw() % 360f) + 360f) % 360f;
            LEFT.add(String.format("Facing: %s (%s) (%.1f / %.1f)", cam.getFacing().name().toLowerCase(), towards(cam), yaw > 180 ? yaw - 360 : yaw, cam.getPitch()));
            LightEngine le = VoxelEngine.getLightEngine();
            if (le != null) {
                try { LEFT.add(String.format("Light: sky %d, block %d", le.getSkyLight(world, bx, by, bz), Math.round(le.sampleBlockLight01(world, bx, by, bz) * 15))); }
                catch (Exception ignored) { }
            }
            LEFT.add("Game mode: " + GameMode.label() + (GameMode.flying ? " (flying)" : "") + "  Health: " + (engine.getLocalPlayer() != null ? engine.getLocalPlayer().health : 0));
            LEFT.add("");
            InputHandler input = cam.getInputHandler();
            InputHandler.Hit hit = input != null ? input.getHoverHit() : null;
            if (hit != null) {
                long state = world.getState(hit.x, hit.y, hit.z);
                BlockType t = BlockType.fromId(BlockState.typeId(state));
                LEFT.add(String.format("Targeted Block: %d, %d, %d", hit.x, hit.y, hit.z));
                LEFT.add("openvoxel:" + t.name().toLowerCase() + "  state=0x" + Long.toHexString(state) + "  solid=" + t.isSolid());
            } else LEFT.add("Targeted Block: none");
            LEFT.add("Hand: " + (cam.getBlockInHand() != null && cam.getBlockInHand().getType() != null ? cam.getBlockInHand().getType().name().toLowerCase() : "empty"));
        }
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) >> 20, max = rt.maxMemory() >> 20;
        RIGHT.add("Java: " + System.getProperty("java.version") + " 64bit");
        RIGHT.add(String.format("Mem: %d%% %d/%dMB", max > 0 ? used * 100 / max : 0, used, max));
        RIGHT.add("Allocated: " + (rt.totalMemory() >> 20) + "MB");
        RIGHT.add("");
        RIGHT.add("CPU: " + rt.availableProcessors() + "x " + System.getProperty("os.arch"));
        RIGHT.add("");
        int[] size = Mc.windowSize();
        RIGHT.add("Display: " + size[0] + "x" + size[1]);
        RIGHT.add(gpu == null ? "" : gpu);
        RIGHT.add(glVersion == null ? "" : "OpenGL " + glVersion);
        RIGHT.add("");
        RIGHT.add("Mods: " + Agent.getLoadedModNames().size() + " loaded");
    }

    private static String towards(Camera cam) {
        switch (cam.getFacing()) {
            case NORTH: return "Towards negative Z";
            case SOUTH: return "Towards positive Z";
            case EAST: return "Towards positive X";
            default: return "Towards negative X";
        }
    }
}
