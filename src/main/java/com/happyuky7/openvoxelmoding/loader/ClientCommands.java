package com.happyuky7.openvoxelmoding.loader;

import net.openvoxel.engine.ui.ChatHud;

import java.util.*;

/** Client-side chat commands handled before the message reaches the server. Mods can register their own. */
final class ClientCommands {
    private ClientCommands() {}

    static final class Command {
        final String name, description; final ModContext.CommandHandler handler;
        Command(String name, String description, ModContext.CommandHandler handler) { this.name = name; this.description = description; this.handler = handler; }
    }

    private static final Map<String, Command> COMMANDS = new LinkedHashMap<>();

    static void register(String name, String description, ModContext.CommandHandler handler) {
        COMMANDS.put(name.toLowerCase(), new Command(name.toLowerCase(), description, handler));
    }

    static Set<String> names() { return COMMANDS.keySet(); }

    static {
        register("gamemode", "<creative|survival> - switch game mode (double-tap space to fly)", args -> {
            if (args.length < 1) { ChatHud.addLine("&cUsage: /gamemode <creative|survival>"); return; }
            String m = args[0].toLowerCase();
            if (m.equals("c") || m.equals("creative") || m.equals("1")) GameMode.set("creative");
            else if (m.equals("s") || m.equals("survival") || m.equals("0")) GameMode.set("survival");
            else { ChatHud.addLine("&cUnknown game mode: " + args[0]); return; }
            ChatHud.addLine("&7Set own game mode to &e" + GameMode.label());
        });
        register("gm", "alias of /gamemode", args -> COMMANDS.get("gamemode").handler.run(args));
        register("fly", "toggle flight in creative", args -> {
            if (!Settings.isCreative()) ChatHud.addLine("&cFlight is only available in creative mode");
            else { GameMode.flying = !GameMode.flying; ChatHud.addLine("&7Flight " + (GameMode.flying ? "&aenabled" : "&cdisabled")); }
        });
        register("skin", "<reload|upload|model> - local skin.png / publish to open-voxel.net", args -> {
            String sub = args.length > 0 ? args[0].toLowerCase() : "reload";
            if (sub.equals("reload")) ChatHud.addLine("&7" + Skins.reloadLocal());
            else if (sub.equals("upload")) async(() -> ChatHud.addLine("&7" + Skins.uploadSkin()));
            else if (sub.equals("model")) async(() -> ChatHud.addLine("&7" + Skins.uploadModel()));
            else ChatHud.addLine("&cUsage: /skin <reload|upload|model>");
        });
        register("give", "<item|block> [count] - put an item in the selected hotbar slot", args -> {
            if (args.length < 1) { ChatHud.addLine("&cUsage: /give <item id or block name>"); return; }
            Items.Stack stack = null;
            Items.Item item = Items.byId(args[0].toLowerCase());
            if (item != null) stack = Items.Stack.of(item);
            else try { stack = Items.Stack.of(net.openvoxel.engine.world.block.BlockType.valueOf(args[0].toUpperCase())); } catch (Exception ignored) { }
            if (stack == null) { ChatHud.addLine("&cUnknown item: " + args[0]); return; }
            if (args.length > 1) try { stack.count = Math.max(1, Math.min(64, Integer.parseInt(args[1]))); } catch (NumberFormatException ignored) { }
            BuiltinUI.HOTBAR[BuiltinUI.selected] = stack;
            BuiltinUI.select(net.openvoxel.engine.VoxelEngine.getEngine(), BuiltinUI.selected);
            ChatHud.addLine("&7Gave &e" + stack.name());
        });
        register("lang", "<code> - switch language (en_us, es_es, ...)", args -> {
            if (args.length < 1) { ChatHud.addLine("&7Languages: &e" + String.join(", ", Lang.available().keySet())); return; }
            Settings.language = args[0];
            Lang.load(args[0]);
            Settings.save();
            ChatHud.addLine("&7Language: &e" + Lang.available().getOrDefault(args[0], args[0]));
        });
        register("music", "<play|next|stop|list|reload> - background music from client/instance/music/*.wav", args -> {
            String sub = args.length > 0 ? args[0].toLowerCase() : "list";
            switch (sub) {
                case "play": Music.start(); ChatHud.addLine(Music.list().isEmpty() ? "&cNo .wav files in " + Music.dir() : "&7Playing: &e" + Music.current()); break;
                case "next": Music.next(); ChatHud.addLine("&7Next track"); break;
                case "stop": Music.stop(); ChatHud.addLine("&7Music stopped"); break;
                case "reload": Music.reload(); BuiltinUI.sound().rescan(); ChatHud.addLine("&7Reloaded " + Music.list().size() + " track(s) and the sound pack"); break;
                default: ChatHud.addLine("&7Tracks (" + Music.list().size() + "): &e" + String.join(", ", Music.list()) + (Music.isPlaying() ? " &7now: &a" + Music.current() : ""));
            }
        });
        register("chelp", "list client commands", args -> help());
    }

    private static void help() {
        ChatHud.addLine("&e--- ModLoader client commands ---");
        for (Command c : COMMANDS.values()) ChatHud.addLine("&a/" + c.name + " &7" + c.description);
        ChatHud.addLine("&7Colors: &0&&0 &1&&1 &2&&2 &3&&3 &4&&4 &5&&5 &6&&6 &7&&7 &8&&8 &9&&9 &a&&a &b&&b &c&&c &d&&d &e&&e &f&&f &#ff8800&&#rrggbb");
        ChatHud.addLine("&7Chat: Tab completes, Up/Down history, wheel or PageUp/PageDown scrolls");
    }

    /** Returns true when the message was fully handled client-side. */
    static boolean handle(String message) {
        String[] a = message.substring(1).trim().split("\\s+");
        if (a.length == 0 || a[0].isEmpty()) return false;
        String name = a[0].toLowerCase();
        String[] args = Arrays.copyOfRange(a, 1, a.length);
        if (name.equals("help")) { help(); return false; } // the server adds its own list
        Command c = COMMANDS.get(name);
        if (c == null) return false;
        try { c.handler.run(args); } catch (Throwable error) { ChatHud.addLine("&cCommand failed: " + error.getMessage()); }
        return true;
    }

    private static void async(Runnable task) {
        Thread t = new Thread(task, "ModLoader-command");
        t.setDaemon(true);
        t.start();
    }
}
