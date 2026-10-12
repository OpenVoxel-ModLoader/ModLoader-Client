package com.happyuky7.openvoxelmoding.loader;

/** Entry point implemented by client mods (declared in {@code openvoxel.mod.properties}). */
public interface ClientMod {
    /** Called at JVM start, before the game window exists. */
    default void onLoad(ModContext context) throws Exception {}
    /** Called once the engine is created; UI, world and renderer hooks are available. */
    default void onGameReady(ModContext context) throws Exception {}
    /** Called every rendered frame on the main thread (after the HUD). Keep it cheap. */
    default void onFrame(ModContext context) throws Exception {}
    default void onShutdown(ModContext context) throws Exception {}
}
