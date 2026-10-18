package com.happyuky7.openvoxelmoding.loader;

/**
 * Entry point of an OpenVoxel ModLoader mod.
 *
 * <p>A mod is a jar in {@code instance/mods} with an {@code openvoxel.mod.properties} file:
 * <pre>
 * id=mymod                       # lowercase id: assets/&lt;id&gt;/lang, config/&lt;id&gt;.properties
 * name=My Mod
 * version=1.0.0
 * main=com.example.mymod.MyMod   # a public class implementing ClientMod with a no-arg constructor
 * </pre>
 *
 * <p>The loader creates one instance per mod and calls the hooks below in this order. Every hook is
 * optional (default methods) and receives the shared {@link ModContext}. Exceptions are caught and
 * logged; they never stop the game or other mods.
 *
 * <pre>{@code
 * public final class MyMod implements ClientMod {
 *     @Override public void onLoad(ModContext ctx) {
 *         ctx.nativeBlock("mymod:ruby_block").name("Ruby Block").texture("assets/mymod/ruby_block.png").register();
 *         ctx.registerCommand("hello", "says hi", args -> ctx.chat("&aHello!"));
 *     }
 * }
 * }</pre>
 *
 * @see ModContext
 */
public interface ClientMod {

    /**
     * Called when the JVM starts, before the game window and the game's blocks exist.
     * This is the place to register {@linkplain ModContext#nativeBlock native blocks}, commands,
     * recipes and configuration. On a dedicated server (see {@link ModContext#isServer()}) only this
     * hook runs.
     *
     * @param context shared ModLoader services
     * @throws Exception anything; it is logged and the mod keeps running
     */
    default void onLoad(ModContext context) throws Exception {}

    /**
     * Called once the engine exists ({@link ModContext#engine()} is not null), before any world is
     * loaded. Register engine event listeners here.
     *
     * @param context shared ModLoader services
     * @throws Exception anything; it is logged
     */
    default void onGameReady(ModContext context) throws Exception {}

    /**
     * Called every rendered frame on the main (OpenGL) thread, after the game drew its HUD.
     * Draw overlays and poll input here; keep it cheap and never block (no network or disk I/O).
     *
     * @param context shared ModLoader services
     * @throws Exception anything; it is logged
     */
    default void onFrame(ModContext context) throws Exception {}

    /**
     * Called when the game closes. {@link ModConfig} files are saved automatically right before.
     *
     * @param context shared ModLoader services
     * @throws Exception anything; it is logged
     */
    default void onShutdown(ModContext context) throws Exception {}
}
