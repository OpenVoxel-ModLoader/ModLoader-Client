package com.happyuky7.openvoxelmoding.loader.audio;

import com.happyuky7.openvoxelmoding.loader.core.Agent;
import com.happyuky7.openvoxelmoding.loader.core.Settings;

import javax.sound.sampled.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Background music from {@code client/instance/music/*.wav}: shuffled playlist, pauses
 * between tracks, volume from Settings.musicVolume. Commands: /music play|next|stop|list|reload.
 */
public final class Music {
    private Music() {}

    private static final List<Path> TRACKS = new ArrayList<>();
    private static volatile boolean playing, skip;
    private static volatile String current = "";
    private static Thread thread;
    private static int index;

    public static Path dir() { return Agent.instanceDir().resolve("music"); }

    public static List<String> list() { List<String> out = new ArrayList<>(); for (Path p : TRACKS) out.add(p.getFileName().toString()); return out; }
    public static String current() { return current; }
    public static boolean isPlaying() { return playing; }

    public static void reload() {
        TRACKS.clear();
        try { Files.createDirectories(dir()); } catch (Exception ignored) { }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir(), "*.wav")) { for (Path f : files) TRACKS.add(f); }
        catch (Exception error) { System.err.println("[ModLoader] music: " + error); }
        Collections.shuffle(TRACKS);
        index = 0;
    }

    public static void start() {
        if (TRACKS.isEmpty()) reload();
        if (TRACKS.isEmpty() || playing) return;
        playing = true;
        thread = new Thread(Music::loop, "ModLoader-Music");
        thread.setDaemon(true);
        thread.start();
    }

    public static void stop() { playing = false; skip = true; current = ""; }
    public static void next() { skip = true; if (!playing) start(); }

    private static void loop() {
        while (playing) {
            if (TRACKS.isEmpty()) { playing = false; break; }
            Path track = TRACKS.get(index % TRACKS.size());
            index++;
            current = track.getFileName().toString();
            skip = false;
            try (AudioInputStream in = AudioSystem.getAudioInputStream(track.toFile())) {
                AudioFormat f = in.getFormat();
                SourceDataLine line = AudioSystem.getSourceDataLine(f);
                line.open(f);
                line.start();
                byte[] buf = new byte[8192];
                int n;
                boolean pcm16 = f.getEncoding() == AudioFormat.Encoding.PCM_SIGNED && f.getSampleSizeInBits() == 16;
                while (playing && !skip && (n = in.read(buf)) > 0) {
                    float vol = Settings.sounds ? Settings.musicVolume : 0f;
                    if (pcm16) scale16(buf, n, vol, f.isBigEndian());
                    line.write(buf, 0, n);
                }
                if (skip) line.flush(); else line.drain();
                line.close();
            } catch (Exception error) { System.err.println("[ModLoader] music " + current + ": " + error.getMessage()); }
            current = "";
            // Minecraft-like pause between tracks.
            for (int i = 0; i < 300 && playing && !skip; i++) try { Thread.sleep(100); } catch (InterruptedException ignored) { }
        }
    }

    /** Scales 16-bit PCM samples in place. */
    static void scale16(byte[] buf, int n, float volume, boolean bigEndian) {
        for (int i = 0; i + 1 < n; i += 2) {
            int lo = bigEndian ? i + 1 : i, hi = bigEndian ? i : i + 1;
            short s = (short) ((buf[hi] << 8) | (buf[lo] & 0xFF));
            s = (short) Math.max(-32768, Math.min(32767, Math.round(s * volume)));
            buf[lo] = (byte) s;
            buf[hi] = (byte) (s >> 8);
        }
    }
}
