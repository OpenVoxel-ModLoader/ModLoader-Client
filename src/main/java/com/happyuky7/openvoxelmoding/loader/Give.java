package com.happyuky7.openvoxelmoding.loader;

import net.openvoxel.engine.VoxelEngine;
import net.openvoxel.engine.ui.ChatHud;
import net.openvoxel.engine.world.block.BlockType;

/**
 * {@code /give [player] <item> [count]} like Minecraft: items go into the inventory (stacking with
 * what you have), whatever does not fit is dropped at your feet, and the held item is never replaced.
 * On a ModLoader server the server checks {@code openvoxel.command.give} and can give to other players;
 * in singleplayer it always works; on a server without ModLoader only in creative.
 */
final class Give {
    private Give() {}

    static final int MAX = 64 * 36;

    static void command(String[] args) {
        VoxelEngine e = VoxelEngine.getEngine();
        if (args.length < 1) { ChatHud.addLine("&c" + Lang.t("give.usage")); return; }
        if (Net.managed && e != null) { Net.sendRaw(e, "/give " + String.join(" ", args)); return; }   // the server decides
        // local: "/give <item> [count]" or "/give <me> <item> [count]"
        int i = 0;
        if (args.length >= 2 && resolve(args[0]) == null && resolve(args[1]) != null) i = 1;
        if (Net.multiplayer() && !Settings.isCreative()) { ChatHud.addLine("&c" + Lang.t("give.noperm")); return; }
        int count = 1;
        if (args.length > i + 1) try { count = Integer.parseInt(args[i + 1]); } catch (NumberFormatException ex) { ChatHud.addLine("&c" + Lang.t("give.count", args[i + 1])); return; }
        give(args[i], count, true);
    }

    /** Resolves an item id, engine block name or native block id to a stack of 1, or null. */
    static Items.Stack resolve(String id) {
        if (id == null) return null;
        String low = id.toLowerCase();
        Items.Item item = Items.byId(low);
        if (item != null) return Items.Stack.of(item);
        BlockType nb = NativeBlocks.type(low);
        if (nb != null) return Items.Stack.of(nb);
        String name = low.startsWith("minecraft:") || low.startsWith("openvoxel:") ? low.substring(low.indexOf(':') + 1) : low;
        try { BlockType t = BlockType.valueOf(name.toUpperCase()); return t == BlockType.AIR || t.hiddenFromInventory() ? null : Items.Stack.of(t); } catch (Exception ex) { return null; }
    }

    /** Gives {@code count} items: fills stacks / free slots, drops the rest. Returns false if the id is unknown. */
    static boolean give(String id, int count, boolean feedback) {
        Items.Stack proto = resolve(id);
        if (proto == null) { if (feedback) ChatHud.addLine("&c" + Lang.t("give.unknown", id)); return false; }
        count = Math.max(1, Math.min(MAX, count));
        int left = count, dropped = 0;
        VoxelEngine e = VoxelEngine.getEngine();
        while (left > 0) {
            int n = Math.min(64, left);
            Items.Stack s = copy(proto, n);
            int before = s.count;
            if (!add(s)) {                       // inventory full: the rest goes on the ground
                if (e != null) Drops.throwFromPlayer(e, s);
                dropped += s.count;
            }
            left -= before;
        }
        if (e != null) BuiltinUI.select(e, BuiltinUI.selected);
        BuiltinUI.sound().play("pickup");
        if (feedback) ChatHud.addLine("&7" + Lang.t("give.done", count, proto.name()) + (dropped > 0 ? " &8(" + Lang.t("give.dropped", dropped) + ")" : ""));
        return true;
    }

    private static Items.Stack copy(Items.Stack proto, int n) { return proto.isBlock() ? Items.Stack.of(proto.block, n) : Items.Stack.of(proto.item, n); }

    /** Merges into existing stacks (max 64), then empty slots (hotbar first). Leaves the remainder in {@code s}. */
    private static boolean add(Items.Stack s) {
        Items.Stack[][] areas = {BuiltinUI.HOTBAR, BuiltinUI.STORAGE};
        for (Items.Stack[] area : areas) for (Items.Stack x : area) {
            if (x == null || !x.same(s) || x.count >= 64) continue;
            int move = Math.min(64 - x.count, s.count);
            x.count += move; s.count -= move;
            if (s.count <= 0) return true;
        }
        for (Items.Stack[] area : areas) for (int i = 0; i < area.length; i++) if (area[i] == null) { area[i] = s; return true; }
        return false;
    }
}
