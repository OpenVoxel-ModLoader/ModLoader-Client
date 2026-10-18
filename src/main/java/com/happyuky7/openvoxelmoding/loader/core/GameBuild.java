package com.happyuky7.openvoxelmoding.loader.core;

import com.happyuky7.openvoxelmoding.loader.Lang;





import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which launcher build is running (every 0.0.8 jar reports "0.0.8-SNAPSHOT" as its version) and its
 * network protocol, read at runtime so it is right for whatever jar the launcher started.
 */
public final class GameBuild {
    private GameBuild() {}

    /** Protocol of the build ModLoader targets (0.0.8-Debug). */
    static final int EXPECTED_PROTOCOL = 9;
    private static String id;
    private static int protocol = -1;

    /** Launcher version id, e.g. {@code 0.0.8-Debug}, taken from {@code versions/<id>/} in the class path. */
    public static String id() {
        if (id == null) {
            String cp = System.getProperty("java.class.path", "") + ";" + System.getProperty("sun.java.command", "");
            Matcher m = Pattern.compile("versions[\\\\/]([^\\\\/;:]+)[\\\\/]").matcher(cp);
            if (m.find()) id = m.group(1);
            else {
                Matcher j = Pattern.compile("openvoxel-([0-9][^\\\\/;:]*?)\\.jar").matcher(cp);
                id = j.find() ? j.group(1) : "unknown";
            }
        }
        return id;
    }

    public static int protocol() {
        if (protocol < 0) {
            try { protocol = Class.forName("net.openvoxel.engine.net.Protocol").getField("VERSION").getInt(null); }
            catch (Throwable t) { protocol = 0; }
        }
        return protocol;
    }

    /** Human readable explanation for a connection error, or null to keep the game's text. */
    static String[] explain(String title, String message) {
        String m = message == null ? "" : message;
        String low = m.toLowerCase(Locale.ROOT);
        String original = "\n\n(" + m.replaceFirst("^kicked: ", "") + ")";
        if (low.contains("protocol mismatch") || low.contains("protocol"))
            return new String[]{Lang.t("kick.protocol.title"), (protocol() == EXPECTED_PROTOCOL
                    ? Lang.t("kick.protocol.server", id(), protocol())
                    : Lang.t("kick.protocol", id(), protocol())) + original};
        if (low.contains("invalid session") || low.contains("not authenticated") || low.contains("session"))
            return new String[]{title, Lang.t("kick.session") + original};
        if (low.contains("server full") || low.contains("server is full"))
            return new String[]{title, Lang.t("kick.full") + original};
        if (low.contains("connection refused"))
            return new String[]{title, Lang.t("kick.refused") + original};
        if (low.contains("timed out"))
            return new String[]{title, Lang.t("kick.timeout") + original};
        if (low.contains("unknownhost") || low.contains("unknown host") || low.contains("nodename nor servname"))
            return new String[]{title, Lang.t("kick.unknownHost") + original};
        return null;
    }
}
