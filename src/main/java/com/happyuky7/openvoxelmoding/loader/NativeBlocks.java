package com.happyuky7.openvoxelmoding.loader;

import net.openvoxel.engine.rendering.Texture;
import net.openvoxel.engine.world.block.BlockShape;
import net.openvoxel.engine.world.block.BlockType;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;

/**
 * Native blocks: real engine block types added by mods, like Fabric/Forge block registration.
 *
 * <p>The game keeps its blocks in the {@code BlockType} enum. When that enum initializes, ModLoader
 * appends one constant per registered block (see {@code Transformer#extendBlockType}). Each constant is
 * an instance of a subclass generated here ({@code BlockType$ML}) whose properties (solid, light,
 * opacity, render layer, gravity) come from the mod's definition, so the engine treats it exactly like
 * its own blocks: meshing, lighting, collision, mining, saving, networking and inventory icons.
 *
 * <p>Blocks must be registered before the game loads its blocks, i.e. in {@link ClientMod#onLoad}.
 * The numeric type id is derived from the namespaced id, so a client and a server with the same mods
 * agree on it without any sync. The dedicated server needs the same mod jar and the ModLoader agent.
 */
public final class NativeBlocks {
    private NativeBlocks() {}

    /** A registered block definition. Built with {@link Builder}. */
    public static final class Def {
        final String id;
        String name;
        byte[] top, side, bottom;
        boolean solid = true, occludes = true, translucent, gravity, hidden;
        int light, opacity = 15;
        float hardness = 1.5f;
        int tool;                 // 0 none, 1 pickaxe, 2 shovel, 3 axe
        boolean requiresTool;
        int numericId = -1;
        volatile Object type;     // the BlockType constant once the enum is initialized

        Def(String id) { this.id = id; }

        public String id() { return id; }
        public String name() { return name != null ? name : id; }
        /** The engine {@code BlockType} constant, or null before the game initializes its blocks. */
        public Object blockType() { return type; }
        public int numericId() { return numericId; }
    }

    /** Fluent definition of a native block; finish with {@link #register()}. */
    public static final class Builder {
        private final Def def;
        private final ClassLoader loader;
        Builder(String id, ClassLoader loader) { this.def = new Def(id); this.loader = loader; }

        /** Display name (used when the lang files have no {@code block.<namespace>.<path>} key). */
        public Builder name(String name) { def.name = name; return this; }
        /** One 16x16 PNG (path inside the mod jar) for every face. */
        public Builder texture(String path) { def.top = read(path); def.side = def.top; def.bottom = def.top; return this; }
        /** Separate top / side / bottom textures (bottom may be null = top). */
        public Builder textures(String top, String side, String bottom) {
            def.top = read(top); def.side = side != null ? read(side) : def.top; def.bottom = bottom != null ? read(bottom) : def.top; return this;
        }
        /** Collides with entities (default true). */
        public Builder solid(boolean solid) { def.solid = solid; return this; }
        /** Light level emitted, 0..15 (torch = 14). */
        public Builder light(int level) { def.light = Math.max(0, Math.min(15, level)); return this; }
        /** Translucent like glass: rendered in the translucent pass, lets light through. */
        public Builder translucent() { def.translucent = true; def.occludes = false; def.opacity = 0; return this; }
        /** How much light it absorbs, 0..15 (stone = 15, glass = 0). */
        public Builder opacity(int value) { def.opacity = Math.max(0, Math.min(15, value)); def.occludes = def.opacity >= 15; return this; }
        /** Falls like sand or gravel. */
        public Builder gravity() { def.gravity = true; return this; }
        /** Seconds to break by hand-ish base (stone = 1.5, dirt = 0.5, 0 = instant, negative = unbreakable). */
        public Builder hardness(float seconds) { def.hardness = seconds; return this; }
        /** Best tool: "pickaxe", "shovel", "axe" or null. With {@code required} it only drops when mined with it. */
        public Builder tool(String tool, boolean required) {
            def.tool = tool == null ? 0 : tool.equals("pickaxe") ? 1 : tool.equals("shovel") ? 2 : tool.equals("axe") ? 3 : 0;
            def.requiresTool = required; return this;
        }
        /** Hide from the creative inventory. */
        public Builder hidden() { def.hidden = true; return this; }

        /** Registers the block. Returns the definition, or null if the id is invalid/taken or it is too late. */
        public Def register() { return NativeBlocks.register(def); }

        private byte[] read(String path) {
            if (path == null) return null;
            String p = path.startsWith("/") ? path.substring(1) : path;
            try (java.io.InputStream in = loader.getResourceAsStream(p)) {
                if (in == null) { System.err.println("[ModLoader] native block " + def.id + ": texture not found: " + path); return null; }
                return in.readAllBytes();
            } catch (java.io.IOException e) { return null; }
        }
    }

    private static final Map<String, Def> DEFS = new LinkedHashMap<>();
    /** PNG bytes of every native texture: the engine reloads block textures from their file path, which these do not have. */
    private static final Map<Texture, byte[]> PNGS = Collections.synchronizedMap(new IdentityHashMap<>());

    static byte[] pngOf(Object texture) { return PNGS.get(texture); }

    private static Texture texture(byte[] png) { Texture t = new Texture(png); PNGS.put(t, png); return t; }
    private static final Map<Object, Def> BY_TYPE = new IdentityHashMap<>();
    private static volatile boolean frozen;

    public static Builder builder(String id, ClassLoader loader) { return new Builder(id, loader); }

    static synchronized Def register(Def def) {
        if (frozen) { System.err.println("[ModLoader] native block " + def.id + " registered too late: register blocks in onLoad()"); return null; }
        if (!validId(def.id)) { System.err.println("[ModLoader] native block: invalid id '" + def.id + "' (use namespace:path, lowercase)"); return null; }
        if (DEFS.containsKey(def.id)) { System.err.println("[ModLoader] native block already registered: " + def.id); return null; }
        if (def.top == null) { System.err.println("[ModLoader] native block " + def.id + " has no texture"); return null; }
        DEFS.put(def.id, def);
        return def;
    }

    /** namespace:path, lowercase (kept free of game classes so registering never loads BlockType early). */
    static boolean validId(String id) { return id != null && id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"); }

    /** True once the game has loaded its blocks (native registration is closed). */
    public static boolean isFrozen() { return frozen; }

    public static Collection<Def> all() { return Collections.unmodifiableCollection(DEFS.values()); }
    public static Def get(String id) { return DEFS.get(id); }
    static Def of(Object type) { return type == null ? null : BY_TYPE.get(type); }

    /** The BlockType for a namespaced id, or null. */
    public static BlockType type(String id) { Def d = DEFS.get(id); return d == null ? null : (BlockType) d.type; }

    /** Display name for a native block type, or null for vanilla blocks. */
    static String label(Object type) {
        Def d = of(type);
        if (d == null) return null;
        String key = "block." + d.id.replace(':', '.');
        String t = Lang.t(key);
        return t.equals(key) ? d.name() : t;
    }

    // ---- called by the generated BlockType$ML methods ----------------------------------

    public static boolean isSolid(Object t) { Def d = of(t); return d == null || d.solid; }
    public static boolean occludesLight(Object t) { Def d = of(t); return d == null || d.occludes; }
    public static int lightEmission(Object t) { Def d = of(t); return d == null ? 0 : d.light; }
    public static int lightOpacity(Object t) { Def d = of(t); return d == null ? 15 : d.opacity; }
    public static boolean isFallingBlock(Object t) { Def d = of(t); return d != null && d.gravity; }
    public static boolean hiddenFromInventory(Object t) { Def d = of(t); return d != null && d.hidden; }
    public static Object renderLayer(Object t) { Def d = of(t); return d != null && d.translucent ? BlockType.RenderLayer.TRANSLUCENT : BlockType.RenderLayer.OPAQUE; }

    // ---- enum extension (runs inside BlockType's static initializer) ---------------------

    private static final String BT = "net/openvoxel/engine/world/block/BlockType";
    private static final String TEX = "Lnet/openvoxel/engine/rendering/Texture;";
    private static final String CTOR = "(Ljava/lang/String;II" + TEX + TEX + TEX + "Lnet/openvoxel/engine/world/block/BlockShape;)V";

    /** Appends the registered blocks to the enum's constant array. */
    public static Object[] extend(Object[] base) {
        frozen = true;
        if (DEFS.isEmpty()) return base;
        try {
            Class<?> blockType = base.getClass().getComponentType();
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(blockType, MethodHandles.lookup());
            Class<?> sub = lookup.defineClass(subclassBytes());
            MethodHandle ctor = lookup.findConstructor(sub, MethodType.methodType(void.class, String.class, int.class, int.class, Texture.class, Texture.class, Texture.class, BlockShape.class));
            Set<Integer> used = new HashSet<>();
            for (Object o : base) used.add(((BlockType) o).getId());
            List<Def> defs = new ArrayList<>(DEFS.values());
            defs.sort(Comparator.comparing(d -> d.id));
            Object[] out = Arrays.copyOf(base, base.length + defs.size(), base.getClass().asSubclass(Object[].class));
            int ordinal = base.length;
            for (Def d : defs) {
                d.numericId = allocate(d.id, used);
                Texture top = texture(d.top), side = texture(d.side != null ? d.side : d.top), bottom = texture(d.bottom != null ? d.bottom : d.top);
                String name = "ML_" + d.id.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
                Object type = ctor.invoke(name, ordinal, d.numericId, top, bottom, side, BlockShape.CUBE);
                d.type = type;
                BY_TYPE.put(type, d);
                out[ordinal++] = type;
                System.out.println("[ModLoader] native block " + d.id + " -> type id " + d.numericId);
            }
            return out;
        } catch (Throwable error) {
            System.err.println("[ModLoader] native blocks could not be added: " + error);
            error.printStackTrace();
            return base;
        }
    }

    /** Deterministic id in 1024..4095 from the namespaced id (linear probing on clashes, sorted order). */
    private static int allocate(String id, Set<Integer> used) {
        int h = 0x811C9DC5;
        for (int i = 0; i < id.length(); i++) { h ^= id.charAt(i); h *= 0x01000193; }
        int n = 1024 + Math.floorMod(h, 3072);
        while (used.contains(n)) n = n >= 4095 ? 1024 : n + 1;
        used.add(n);
        return n;
    }

    /** Bytecode of {@code final class BlockType$ML extends BlockType} delegating its properties to this class. */
    private static byte[] subclassBytes() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String name = BT + "$ML";
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_ENUM, name, null, BT, null);
        MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", CTOR, null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0); init.visitVarInsn(Opcodes.ALOAD, 1); init.visitVarInsn(Opcodes.ILOAD, 2); init.visitVarInsn(Opcodes.ILOAD, 3);
        init.visitVarInsn(Opcodes.ALOAD, 4); init.visitVarInsn(Opcodes.ALOAD, 5); init.visitVarInsn(Opcodes.ALOAD, 6); init.visitVarInsn(Opcodes.ALOAD, 7);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, BT, "<init>", CTOR, false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        String self = "com/happyuky7/openvoxelmoding/loader/NativeBlocks";
        String[][] props = {
            {"isSolid", "()Z", "(Ljava/lang/Object;)Z"}, {"occludesLight", "()Z", "(Ljava/lang/Object;)Z"},
            {"lightEmission", "()I", "(Ljava/lang/Object;)I"}, {"lightOpacity", "()I", "(Ljava/lang/Object;)I"},
            {"isFallingBlock", "()Z", "(Ljava/lang/Object;)Z"}, {"hiddenFromInventory", "()Z", "(Ljava/lang/Object;)Z"}};
        for (String[] p : props) {
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, p[0], p[1], null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, self, p[0], p[2], false);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        String layer = "L" + BT + "$RenderLayer;";
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "renderLayer", "()" + layer, null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, self, "renderLayer", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
        mv.visitTypeInsn(Opcodes.CHECKCAST, BT + "$RenderLayer");
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }
}
