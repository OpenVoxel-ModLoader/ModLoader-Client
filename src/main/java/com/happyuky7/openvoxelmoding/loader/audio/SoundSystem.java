package com.happyuky7.openvoxelmoding.loader.audio;

import com.happyuky7.openvoxelmoding.loader.core.Agent;
import com.happyuky7.openvoxelmoding.loader.core.Settings;
import com.happyuky7.openvoxelmoding.loader.gameplay.GameMode;

import net.openvoxel.engine.VoxelEngine;
import net.openvoxel.engine.events.player.ClickEvent;
import org.joml.Vector3d;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sound effects. Each event name ({@code step, break, place, pickup, eat, hurt, click, pop})
 * plays {@code client/instance/sounds/<name>.wav} (or {@code <name>1.wav}, {@code <name>2.wav}...
 * chosen at random) when present, otherwise a synthesized fallback. WAV only (Java Sound).
 */
public final class SoundSystem {
    private final ExecutorService audio = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "ModLoader-Audio"); t.setDaemon(true); return t; });
    private final Map<String, byte[][]> packs = new HashMap<>();
    private final Random random = new Random();
    private Vector3d last;
    private long lastStep;
    private boolean scanned;

    static Path dir() { return Agent.instanceDir().resolve("sounds"); }

    public void install(VoxelEngine e) {
        e.getEventManager().register(ClickEvent.class, x -> {
            if (x.type == ClickEvent.ClickType.MIDDLE) play("click");
            else if (x.type == ClickEvent.ClickType.RIGHT && !Settings.isCreative()) return; // placement sound comes from the edit hook
            else if (x.type == ClickEvent.ClickType.RIGHT) play("place");
            else if (Settings.isCreative()) play("break");
        });
    }

    public void tick(VoxelEngine e) {
        if (!Settings.sounds || !e.isWorldActive() || e.getCamera() == null) return;
        Vector3d p = e.getCamera().getPosition();
        long n = System.nanoTime();
        boolean grounded = e.getPhysics() != null && e.getPhysics().isOnGround() && !GameMode.flying;
        if (last != null && grounded && Math.hypot(p.x - last.x, p.z - last.z) > .065 && n - lastStep > 380_000_000L) {
            play("step");
            lastStep = n;
        }
        last = new Vector3d(p);
    }

    /** Rescans the sounds folder (called when entering a world and by /music reload). */
    public void rescan() {
        packs.clear();
        scanned = true;
        Path d = dir();
        if (!Files.isDirectory(d)) return;
        Map<String, java.util.List<byte[]>> found = new HashMap<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(d, "*.wav")) {
            for (Path f : files) {
                String name = f.getFileName().toString().replaceFirst("\\.wav$", "").replaceAll("\\d+$", "").toLowerCase();
                found.computeIfAbsent(name, k -> new java.util.ArrayList<>()).add(Files.readAllBytes(f));
            }
        } catch (Exception error) { System.err.println("[ModLoader] sounds: " + error); }
        for (Map.Entry<String, java.util.List<byte[]>> en : found.entrySet()) packs.put(en.getKey(), en.getValue().toArray(new byte[0][]));
        if (!packs.isEmpty()) System.out.println("[ModLoader] sound pack: " + packs.keySet());
    }

    public void play(String name) {
        if (!Settings.sounds || Settings.volume <= 0) return;
        if (!scanned) rescan();
        byte[][] variants = packs.get(name);
        if (variants != null && variants.length > 0) { playWav(variants[random.nextInt(variants.length)], Settings.volume); return; }
        switch (name) {
            case "step": play(58 + random.nextInt(40), .05); break;
            case "break": noise(0.12, 0.9f, 240); break;
            case "place": play(190, .04); break;
            case "pickup": sweep(300, 900, 0.08); break;
            case "pop": sweep(500, 1100, 0.05); break;
            case "eat": noise(0.06, 0.6f, 900); break;
            case "hurt": play(42, .14); break;
            case "click": play(205, .035); break;
            default: play(120, .05);
        }
    }

    private void playWav(byte[] data, float volume) {
        audio.submit(() -> {
            try (AudioInputStream in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data))) {
                AudioFormat f = in.getFormat();
                SourceDataLine line = AudioSystem.getSourceDataLine(f);
                line.open(f);
                line.start();
                byte[] buf = new byte[4096];
                int n;
                boolean pcm16 = f.getEncoding() == AudioFormat.Encoding.PCM_SIGNED && f.getSampleSizeInBits() == 16;
                while ((n = in.read(buf)) > 0) {
                    if (pcm16) Music.scale16(buf, n, volume, f.isBigEndian());
                    line.write(buf, 0, n);
                }
                line.drain();
                line.close();
            } catch (Exception error) { System.err.println("[ModLoader] wav: " + error.getMessage()); }
        });
    }

    public void play(double hz, double seconds) {
        float vol = Settings.volume;
        synth(seconds, (i, samples) -> {
            double attack = Math.min(1, i / (22050 * .008));
            double release = Math.pow(1 - i / (double) samples, 2);
            double noise = (random.nextDouble() * 2 - 1) * .18;
            double wave = Math.sin(2 * Math.PI * hz * i / 22050.0) * .25;
            return 5200 * vol * attack * release * (noise + wave);
        });
    }

    private void noise(double seconds, float amount, double hz) {
        float vol = Settings.volume;
        synth(seconds, (i, samples) -> {
            double release = Math.pow(1 - i / (double) samples, 1.5);
            double noise = (random.nextDouble() * 2 - 1) * amount;
            double wave = Math.sin(2 * Math.PI * hz * i / 22050.0) * (1 - amount) * 0.4;
            return 4800 * vol * release * (noise + wave);
        });
    }

    private void sweep(double from, double to, double seconds) {
        float vol = Settings.volume;
        synth(seconds, (i, samples) -> {
            double t = i / (double) samples, hz = from + (to - from) * t;
            double release = 1 - t;
            return 4200 * vol * release * Math.sin(2 * Math.PI * hz * i / 22050.0) * 0.4;
        });
    }

    private interface Wave { double sample(int i, int samples); }

    private void synth(double seconds, Wave wave) {
        audio.submit(() -> {
            try {
                AudioFormat f = new AudioFormat(22050, 16, 1, true, false);
                int samples = (int) (22050 * seconds);
                byte[] b = new byte[samples * 2];
                for (int i = 0; i < samples; i++) {
                    short v = (short) Math.max(-32767, Math.min(32767, wave.sample(i, samples)));
                    b[i * 2] = (byte) v;
                    b[i * 2 + 1] = (byte) (v >> 8);
                }
                SourceDataLine l = AudioSystem.getSourceDataLine(f);
                l.open(f, Math.min(b.length, 4096));
                l.start();
                l.write(b, 0, b.length);
                l.drain();
                l.close();
            } catch (Exception ignored) { }
        });
    }
}
