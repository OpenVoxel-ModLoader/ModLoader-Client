package com.happyuky7.openvoxelmoding.loader;

import net.openvoxel.client.ClientMain;
import net.openvoxel.client.SessionInfo;
import net.openvoxel.engine.VoxelEngine;
import net.openvoxel.engine.character.ModelData;
import net.openvoxel.engine.character.PlayerRenderer;
import net.openvoxel.engine.rendering.Texture;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minecraft skin support: 64x32 / 64x64 / 128x128 PNGs on a two-layer Steve or Alex
 * model built through the engine's own ModelBuilder, applied to the local and remote
 * players, plus upload of the skin and a matching .bbmodel to open-voxel.net.
 */
final class Skins {
    private Skins() {}

    static volatile String status = "";
    /** The skin currently applied to the local player (used by previews and the first-person arm). */
    private static volatile BufferedImage current;
    private static Texture handTex;
    private static boolean handSlim;
    private static int handTexW = 64;
    private static long lastUpload;

    static Path localFile() { return Agent.instanceDir().resolve("config").resolve("skin.png"); }

    // ---- Image helpers -----------------------------------------------------------------

    static BufferedImage decode(byte[] png) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        if (img == null) throw new IOException("not a PNG image");
        BufferedImage argb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        argb.getGraphics().drawImage(img, 0, 0, null);
        return argb;
    }

    static byte[] encode(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    static boolean isMcFormat(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        return (w == 64 && (h == 32 || h == 64)) || (w == 128 && h == 128);
    }

    /** Converts legacy 64x32 skins to 64x64 by mirroring the right limbs into the left slots. */
    static BufferedImage normalize(BufferedImage img) {
        if (img.getWidth() != 64 || img.getHeight() != 32) return img;
        BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        out.getGraphics().drawImage(img, 0, 0, null);
        mirrorBox(img, 0, 16, out, 16, 48, 4, 12, 4);    // right leg -> left leg
        mirrorBox(img, 40, 16, out, 32, 48, 4, 12, 4);   // right arm -> left arm
        return out;
    }

    private static void mirrorBox(BufferedImage src, int u, int v, BufferedImage dst, int u2, int v2, int w, int h, int d) {
        copyFlipped(src, u + d, v, w, d, dst, u2 + d, v2);                 // top
        copyFlipped(src, u + d + w, v, w, d, dst, u2 + d + w, v2);         // bottom
        copyFlipped(src, u, v + d, d, h, dst, u2 + d + w, v2 + d);         // right side -> left slot
        copyFlipped(src, u + d, v + d, w, h, dst, u2 + d, v2 + d);         // front
        copyFlipped(src, u + d + w, v + d, d, h, dst, u2, v2 + d);         // left side -> right slot
        copyFlipped(src, u + 2 * d + w, v + d, w, h, dst, u2 + 2 * d + w, v2 + d); // back
    }

    private static void copyFlipped(BufferedImage src, int sx, int sy, int w, int h, BufferedImage dst, int dx, int dy) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) dst.setRGB(dx + (w - 1 - x), dy + y, src.getRGB(sx + x, sy + y));
    }

    static boolean slim(BufferedImage img) {
        if (Settings.skinModel.equals("steve")) return false;
        if (Settings.skinModel.equals("alex")) return true;
        int s = img.getWidth() / 64;
        if (s < 1 || img.getHeight() < 64 * s) return false;
        int sx = 54 * s + s / 2, sy = 20 * s + s / 2;
        return alpha(img, sx, sy) == 0 && alpha(img, sx + s, sy) == 0;
    }

    private static int alpha(BufferedImage img, int x, int y) {
        if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) return 0;
        return (img.getRGB(x, y) >>> 24) & 255;
    }

    static byte[] defaultSkin() throws IOException {
        try (InputStream in = ClassLoader.getSystemResourceAsStream("textures/player/debug_skin.png")) {
            if (in == null) throw new IOException("default skin missing");
            return in.readAllBytes();
        }
    }

    // ---- Model -------------------------------------------------------------------------

    /** Two-layer Steve/Alex model in the engine's coordinate convention (+x = right side). */
    static ModelData buildModel(int texW, int texH, boolean slim) throws Exception {
        Class<?> mb = Class.forName("net.openvoxel.engine.character.ModelBuilder");
        Constructor<?> ctor = mb.getDeclaredConstructor(int.class, int.class, float.class);
        Method begin = mb.getDeclaredMethod("beginPart", String.class, float.class, float.class, float.class);
        Method box = mb.getDeclaredMethod("box", float.class, float.class, float.class, float.class, float.class, float.class, float.class, float.class, float.class);
        Method build = mb.getDeclaredMethod("build");
        ctor.setAccessible(true); begin.setAccessible(true); box.setAccessible(true); build.setAccessible(true);
        float s = texW / 64f;
        Object b = ctor.newInstance(texW, texH, s);
        float aw = slim ? 3f : 4f;
        begin.invoke(b, "head", 0f, 1.5f, 0f);
        box.invoke(b, -4f, 24f, -4f, 4f, 32f, 4f, 0f, 0f, 0f);
        box.invoke(b, -4f, 24f, -4f, 4f, 32f, 4f, 32 * s, 0f, 0.5f);
        begin.invoke(b, "torso", 0f, 0.75f, 0f);
        box.invoke(b, -4f, 12f, -2f, 4f, 24f, 2f, 16 * s, 16 * s, 0f);
        box.invoke(b, -4f, 12f, -2f, 4f, 24f, 2f, 16 * s, 32 * s, 0.25f);
        begin.invoke(b, "arm_r", (4f + aw / 2f) / 16f, 1.4375f, 0f);
        box.invoke(b, 4f, 12f, -2f, 4f + aw, 24f, 2f, 40 * s, 16 * s, 0f);
        box.invoke(b, 4f, 12f, -2f, 4f + aw, 24f, 2f, 40 * s, 32 * s, 0.25f);
        begin.invoke(b, "arm_l", -(4f + aw / 2f) / 16f, 1.4375f, 0f);
        box.invoke(b, -4f - aw, 12f, -2f, -4f, 24f, 2f, 32 * s, 48 * s, 0f);
        box.invoke(b, -4f - aw, 12f, -2f, -4f, 24f, 2f, 48 * s, 48 * s, 0.25f);
        begin.invoke(b, "leg_r", 0.125f, 0.75f, 0f);
        box.invoke(b, 0f, 0f, -2f, 4f, 12f, 2f, 0f, 16 * s, 0f);
        box.invoke(b, 0f, 0f, -2f, 4f, 12f, 2f, 0f, 32 * s, 0.25f);
        begin.invoke(b, "leg_l", -0.125f, 0.75f, 0f);
        box.invoke(b, -4f, 0f, -2f, 0f, 12f, 2f, 16 * s, 48 * s, 0f);
        box.invoke(b, -4f, 0f, -2f, 0f, 12f, 2f, 0f, 48 * s, 0.25f);
        return (ModelData) build.invoke(b);
    }

    // ---- Apply -------------------------------------------------------------------------

    /** Hook target: returns true when the server appearance was handled here. */
    static boolean applyFromServer(byte[] skin, String modelJson, String uuid) throws Exception {
        if (uuid == null) return false;
        SessionInfo session = ClientMain.getSession();
        boolean remote = ClientMain.getNet() != null && ClientMain.getNet().getRemotePlayers().get(uuid) != null;
        boolean local = session != null && uuid.equalsIgnoreCase(session.uuid);   // the server may list us as a remote player too
        if (modelJson != null) {
            // The engine applies the custom .bbmodel itself; keep the texture for our previews and the first-person arm.
            if (local && skin != null) {
                try {
                    BufferedImage img = decode(skin);
                    if (isMcFormat(img)) { BufferedImage n = normalize(img); boolean slim = slim(n); ClientMain.runOnMainThread(() -> { try { current = n; setHand(n, slim); status = "Server model + skin applied"; } catch (Exception ignored) { } }); }
                } catch (Exception ignored) { }
            }
            return false;
        }
        if (!local && !remote) return false;
        if (local && Files.isRegularFile(localFile())) { reloadLocal(); return true; }
        BufferedImage img = skin != null ? decode(skin) : null;
        if (img == null || !isMcFormat(img)) {
            if (!local) return false;
            img = fallbackImage();                       // no skin on the server: launcher cache / default
            if (img == null) return false;
        }
        BufferedImage normalized = normalize(img);
        boolean slim = slim(normalized);
        ClientMain.runOnMainThread(() -> {
            try {
                if (local) applyLocal(normalized, slim, skin != null ? "server skin" : "fallback");
                if (remote) applyRemote(uuid, normalized, slim);
            } catch (Exception error) { System.err.println("[HappySkins] apply " + uuid + ": " + error); }
        });
        return true;
    }

    private static void applyLocal(BufferedImage img, boolean slim, String source) throws Exception {
        VoxelEngine engine = VoxelEngine.getEngine();
        current = img;
        setHand(img, slim);
        if (engine == null || engine.getRenderer() == null) { status = "Skin ready (" + source + ")"; return; }
        PlayerRenderer renderer = engine.getRenderer().getPlayerRenderer();
        renderer.setModel(buildModel(img.getWidth(), img.getHeight(), slim), new Texture(encode(img)), null);
        status = "Applied " + img.getWidth() + "x" + img.getHeight() + " " + (slim ? "Alex" : "Steve") + " skin (" + source + ")";
        System.out.println("[HappySkins] " + status);
    }

    private static void applyRemote(String uuid, BufferedImage img, boolean slim) throws Exception {
        Object player = ClientMain.getNet() != null ? ClientMain.getNet().getRemotePlayers().get(uuid) : null;
        if (player == null) return;
        Field rendererField = player.getClass().getDeclaredField("renderer");
        rendererField.setAccessible(true);
        PlayerRenderer renderer = (PlayerRenderer) rendererField.get(player);
        renderer.setModel(buildModel(img.getWidth(), img.getHeight(), slim), new Texture(encode(img)), null);
        Field loaded = player.getClass().getDeclaredField("appearanceLoaded");
        loaded.setAccessible(true);
        loaded.setBoolean(player, true);
        System.out.println("[HappySkins] remote " + uuid + ": " + (slim ? "Alex" : "Steve") + " skin");
    }

    /** Loads config/skin.png for the local player. Returns a status line for chat/UI. */
    static String reloadLocal() {
        try {
            Path file = localFile();
            if (!Files.isRegularFile(file)) return status = "No skin: put a PNG at " + file;
            BufferedImage img = decode(Files.readAllBytes(file));
            if (!isMcFormat(img)) return status = "skin.png must be 64x64, 64x32 or 128x128 (is " + img.getWidth() + "x" + img.getHeight() + ")";
            BufferedImage normalized = normalize(img);
            boolean slim = slim(normalized);
            ClientMain.runOnMainThread(() -> {
                try { applyLocal(normalized, slim, "skin.png"); }
                catch (Exception error) { status = "Skin failed: " + error.getMessage(); }
            });
            return status = "Loading skin.png (" + (slim ? "Alex" : "Steve") + ")...";
        } catch (Exception error) { return status = "Skin failed: " + error.getMessage(); }
    }

    static void onWorldEnter() {
        if (Files.isRegularFile(localFile())) { reloadLocal(); return; }
        // Apply the launcher's cached skin right away; the server skin/model replaces it when it arrives.
        BufferedImage fallback = fallbackImage();
        if (fallback == null) return;
        boolean slim = slim(fallback);
        ClientMain.runOnMainThread(() -> {
            try { applyLocal(fallback, slim, "launcher cache"); }
            catch (Exception error) { System.err.println("[HappySkins] fallback: " + error); }
        });
    }

    /** Local skin.png, else the newest Minecraft-layout PNG in Game/skins, else the engine's debug skin. */
    static BufferedImage fallbackImage() {
        try {
            if (Files.isRegularFile(localFile())) {
                BufferedImage img = decode(Files.readAllBytes(localFile()));
                if (isMcFormat(img)) return normalize(img);
            }
            Path cache = Paths.get(System.getProperty("user.dir"), "skins");
            if (Files.isDirectory(cache)) {
                Path newest = null;
                try (java.util.stream.Stream<Path> files = Files.list(cache)) {
                    for (Path p : (Iterable<Path>) files::iterator) {
                        if (!p.toString().toLowerCase().endsWith(".png")) continue;
                        if (newest == null || Files.getLastModifiedTime(p).compareTo(Files.getLastModifiedTime(newest)) > 0) newest = p;
                    }
                }
                if (newest != null) {
                    BufferedImage img = decode(Files.readAllBytes(newest));
                    if (isMcFormat(img)) return normalize(img);
                }
            }
            return normalize(decode(defaultSkin()));
        } catch (Exception error) { return null; }
    }

    // ---- First-person arm texture ------------------------------------------------------

    private static void setHand(BufferedImage img, boolean slim) throws IOException {
        Texture old = handTex;
        handTex = new Texture(encode(img));
        handSlim = slim;
        handTexW = img.getWidth();
        if (old != null) old.cleanup();
    }

    static Texture handTexture() {
        if (handTex == null) {
            try {
                BufferedImage img = current != null ? current : fallbackImage();
                if (img == null) return null;
                setHand(img, slim(img));
            } catch (Exception error) { System.err.println("[HappySkins] hand texture: " + error); return null; }
        }
        return handTex;
    }

    static boolean handSlim() { return handSlim; }
    static int handTexWidth() { return handTexW; }

    /** Current local skin image for 2D previews (inventory, skin screen). */
    static BufferedImage currentImage() {
        return current != null ? current : fallbackImage();
    }

    // ---- open-voxel.net uploads --------------------------------------------------------

    static String bbmodel(boolean slim) {
        float aw = slim ? 3f : 4f;
        StringBuilder el = new StringBuilder(), out = new StringBuilder();
        String[][] groups = {
            {"head", "0,24,0"}, {"body", "0,12,0"}, {"right_arm", (4 + aw / 2) + ",22,0"}, {"left_arm", (-(4 + aw / 2)) + ",22,0"},
            {"right_leg", "2,12,0"}, {"left_leg", "-2,12,0"}
        };
        float[][][] boxes = {
            {{-4, 24, -4, 4, 32, 4, 0, 0, 0}, {-4, 24, -4, 4, 32, 4, 32, 0, 0.5f}},
            {{-4, 12, -2, 4, 24, 2, 16, 16, 0}, {-4, 12, -2, 4, 24, 2, 16, 32, 0.25f}},
            {{4, 12, -2, 4 + aw, 24, 2, 40, 16, 0}, {4, 12, -2, 4 + aw, 24, 2, 40, 32, 0.25f}},
            {{-4 - aw, 12, -2, -4, 24, 2, 32, 48, 0}, {-4 - aw, 12, -2, -4, 24, 2, 48, 48, 0.25f}},
            {{0, 0, -2, 4, 12, 2, 0, 16, 0}, {0, 0, -2, 4, 12, 2, 0, 32, 0.25f}},
            {{-4, 0, -2, 0, 12, 2, 16, 48, 0}, {-4, 0, -2, 0, 12, 2, 0, 48, 0.25f}}
        };
        for (int g = 0; g < groups.length; g++) {
            StringBuilder children = new StringBuilder();
            for (int i = 0; i < 2; i++) {
                float[] c = boxes[g][i];
                String id = UUID.randomUUID().toString();
                if (el.length() > 0) el.append(',');
                el.append("{\"name\":\"").append(groups[g][0]).append(i == 1 ? "_layer" : "").append("\",\"box_uv\":true,\"type\":\"cube\",")
                  .append("\"from\":[").append(c[0]).append(',').append(c[1]).append(',').append(c[2]).append("],")
                  .append("\"to\":[").append(c[3]).append(',').append(c[4]).append(',').append(c[5]).append("],")
                  .append("\"uv_offset\":[").append((int) c[6]).append(',').append((int) c[7]).append("],")
                  .append("\"inflate\":").append(c[8]).append(",\"uuid\":\"").append(id).append("\"}");
                if (children.length() > 0) children.append(',');
                children.append('"').append(id).append('"');
            }
            if (out.length() > 0) out.append(',');
            out.append("{\"name\":\"").append(groups[g][0]).append("\",\"uuid\":\"").append(UUID.randomUUID()).append("\",\"origin\":[")
               .append(groups[g][1]).append("],\"children\":[").append(children).append("]}");
        }
        return "{\"meta\":{\"format_version\":\"4.5\",\"model_format\":\"free\",\"box_uv\":true},\"name\":\"minecraft_" + (slim ? "alex" : "steve")
             + "\",\"resolution\":{\"width\":64,\"height\":64},\"elements\":[" + el + "],\"outliner\":[" + out
             + "],\"textures\":[{\"name\":\"skin.png\",\"width\":64,\"height\":64}],\"animations\":[]}";
    }

    private static String token() {
        SessionInfo s = ClientMain.getSession();
        return s != null && s.isLoggedIn() ? s.token : null;
    }

    private static String authServer() {
        SessionInfo s = ClientMain.getSession();
        String base = s != null && s.authServer != null ? s.authServer : "https://open-voxel.net";
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    static String uploadSkin() {
        String token = token();
        if (token == null) return status = "Log in with the launcher (or Account screen) before uploading";
        if (System.currentTimeMillis() - lastUpload < 10_500) return status = "Upload cooldown: wait 10 seconds";
        try {
            BufferedImage img = currentImage();
            if (img == null) return status = "No skin to upload";
            lastUpload = System.currentTimeMillis();
            String body = http(authServer() + "/skins/upload", "POST", "image/png", encode(img), token);
            return status = "Skin uploaded: " + extract(body, "url", body);
        } catch (Exception error) { return status = "Skin upload failed: " + error.getMessage(); }
    }

    static String uploadModel() {
        String token = token();
        if (token == null) return status = "Log in with the launcher (or Account screen) before uploading";
        if (System.currentTimeMillis() - lastUpload < 10_500) return status = "Upload cooldown: wait 10 seconds";
        try {
            BufferedImage img = currentImage();
            boolean slim = img != null && slim(img);
            lastUpload = System.currentTimeMillis();
            String body = http(authServer() + "/models/upload", "POST", "application/json", bbmodel(slim).getBytes(StandardCharsets.UTF_8), token);
            return status = "Model uploaded (" + (slim ? "Alex" : "Steve") + ", " + extract(body, "cubes", "?") + " cubes)";
        } catch (Exception error) { return status = "Model upload failed: " + error.getMessage(); }
    }

    static String http(String url, String method, String contentType, byte[] body, String token) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            c.setRequestProperty("Content-Type", contentType);
            c.setDoOutput(true);
            try (OutputStream out = c.getOutputStream()) { out.write(body); }
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        c.disconnect();
        if (code >= 400) throw new IOException("HTTP " + code + " " + extract(text, "error", text));
        return text;
    }

    static String extract(String json, String key, String fallback) {
        if (json == null) return fallback;
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"?([^\",}]+)").matcher(json);
        return m.find() ? m.group(1) : fallback;
    }
}
