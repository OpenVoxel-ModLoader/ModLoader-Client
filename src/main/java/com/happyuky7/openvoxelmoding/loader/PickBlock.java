package com.happyuky7.openvoxelmoding.loader;

import net.openvoxel.engine.VoxelEngine;
import net.openvoxel.engine.input.InputHandler;
import net.openvoxel.engine.world.block.BlockState;
import net.openvoxel.engine.world.block.BlockType;

/**
 * Middle click ("pick block") with Minecraft rules, replacing the engine's version that simply
 * overwrote the held slot:
 * <ul>
 *   <li>already in the hotbar: that slot gets selected;</li>
 *   <li>survival: taken from the inventory into the hotbar (swap), never created from nothing;</li>
 *   <li>creative: a full stack goes into an empty hotbar slot, or into the selected one after
 *       moving what was there to the inventory, so nothing is lost.</li>
 * </ul>
 * ModLoader blocks (chest, crafting table, mod blocks) pick their own item, not the base block.
 */
final class PickBlock {
    private PickBlock() {}

    static void pick(VoxelEngine e) {
        if (e == null || !e.isWorldActive() || e.getCamera() == null || e.getCamera().getInputHandler() == null) return;
        InputHandler.Hit hit = e.getCamera().getInputHandler().pickBlockFromCamera();
        if (hit == null) return;
        Items.Stack want = target(e, hit);
        if (want == null) return;
        Items.Stack[] bar = BuiltinUI.HOTBAR, inv = BuiltinUI.STORAGE;

        for (int i = 0; i < 9; i++) if (bar[i] != null && bar[i].same(want)) { BuiltinUI.select(e, i); return; }

        int slot = -1;
        if (bar[BuiltinUI.selected] == null) slot = BuiltinUI.selected;
        else for (int i = 0; i < 9; i++) if (bar[i] == null) { slot = i; break; }

        if (!Settings.isCreative()) {
            for (int i = 0; i < inv.length; i++) {
                if (inv[i] == null || !inv[i].same(want)) continue;
                int to = slot >= 0 ? slot : BuiltinUI.selected;
                Items.Stack old = bar[to];
                bar[to] = inv[i];
                inv[i] = old;                       // swap, like Minecraft
                BuiltinUI.select(e, to);
                return;
            }
            return;                                 // not owned: nothing to pick in survival
        }

        if (slot < 0) {
            slot = BuiltinUI.selected;
            Items.Stack old = bar[slot];
            if (old != null && !storeInInventory(old)) return;   // inventory full: keep the hotbar as is
        }
        bar[slot] = want.fresh();
        BuiltinUI.select(e, slot);
    }

    private static boolean storeInInventory(Items.Stack s) {
        Items.Stack[] inv = BuiltinUI.STORAGE;
        for (Items.Stack x : inv) if (x != null && x.same(s) && x.count + s.count <= 64) { x.count += s.count; return true; }
        for (int i = 0; i < inv.length; i++) if (inv[i] == null) { inv[i] = s; return true; }
        return false;
    }

    private static Items.Stack target(VoxelEngine e, InputHandler.Hit hit) {
        Tiles.Tile tile = Tiles.at(hit.x, hit.y, hit.z);
        if (tile != null) return tile.def.item != null ? Items.Stack.of(tile.def.item) : null;
        try {
            BlockType type = BlockType.fromId(BlockState.typeId(e.getWorld().getState(hit.x, hit.y, hit.z)));
            if (type == null || type == BlockType.AIR) return null;
            BlockType result = type.pickResult();
            if (result == null || result == BlockType.AIR || result.hiddenFromInventory()) return null;
            return Items.Stack.of(result);
        } catch (Exception error) { return null; }
    }
}
