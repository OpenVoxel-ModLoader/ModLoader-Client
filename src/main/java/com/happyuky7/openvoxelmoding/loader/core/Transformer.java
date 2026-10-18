package com.happyuky7.openvoxelmoding.loader.core;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.*;

/**
 * Runtime bytecode wrapper. The original game JAR on disk is never modified; selected
 * methods are renamed to {@code name$orig} in memory and replaced with a call into
 * {@link Hooks}, which decides whether to call the original. Hook parameters keep the
 * original descriptor; instance methods get the receiver as a leading {@code Object}.
 */
final class Transformer implements ClassFileTransformer {
    private static final String HOOKS = "com/happyuky7/openvoxelmoding/loader/core/Hooks";
    private static final class Target { final String name, desc, hook; Target(String n, String d, String h) { name = n; desc = d; hook = h; } }
    private static final Map<String, List<Target>> TARGETS = new HashMap<>();

    private static void wrap(String owner, String name, String desc, String hook) {
        TARGETS.computeIfAbsent(owner, k -> new ArrayList<>()).add(new Target(name, desc, hook));
    }

    static {
        String ui = "net/openvoxel/engine/ui/GLUIRenderer";
        wrap(ui, "drawText", "(Ljava/lang/String;FFFFFF)V", "drawText");
        wrap(ui, "getTextWidth", "(Ljava/lang/String;)F", "getTextWidth");
        wrap(ui, "createDefaultFontData", "()Ljava/nio/ByteBuffer;", "fontData");
        wrap("net/openvoxel/engine/ui/widgets/TextField", "renderWidget", "()V", "textFieldRender");
        wrap("net/openvoxel/engine/ui/widgets/ChatScreen", "onKeyPress", "(I)V", "chatKey");
        wrap("net/openvoxel/engine/ui/ChatHud", "render", "(Lnet/openvoxel/engine/VoxelEngine;)V", "chatRender");
        wrap("net/openvoxel/engine/physics/PhysicsEngine", "tick", "(FZZ)V", "physicsTick");
        wrap("net/openvoxel/engine/physics/PhysicsEngine", "moveHorizontal", "(DDD)V", "moveHorizontal");
        wrap("net/openvoxel/engine/rendering/Renderer", "render", "(Lnet/openvoxel/engine/input/InputHandler;)V", "renderWorld");
        wrap("net/openvoxel/engine/rendering/Renderer", "setupShaders", "()V", "setupShaders");
        wrap("net/openvoxel/engine/rendering/Texture", "decode", "()V", "textureDecode");
        wrap("net/openvoxel/engine/ui/DebugOverlay", "render", "(Lnet/openvoxel/engine/VoxelEngine;)V", "debugRender");
        wrap("net/openvoxel/engine/ui/StatusHud", "render", "(Lnet/openvoxel/engine/VoxelEngine;)V", "statusRender");
        wrap("net/openvoxel/engine/VoxelEngine", "sendChat", "(Ljava/lang/String;)V", "sendChat");
        wrap("net/openvoxel/engine/VoxelEngine", "applyEdit", "(IIILnet/openvoxel/engine/world/AbstractBlock;)V", "applyEdit");
        wrap("net/openvoxel/engine/events/GameEventManager", "fire", "(Lnet/openvoxel/engine/events/GameEvent;)V", "fireEvent");
        wrap("net/openvoxel/engine/VoxelEngine", "applyServerHealth", "(FZ)V", "applyServerHealth");
        wrap("net/openvoxel/client/ClientMain", "registerJoinIntent", "(Ljava/lang/String;)Ljava/lang/String;", "registerJoinIntent");
        wrap("net/openvoxel/client/net/AppearanceLoader", "apply", "(Ljava/lang/String;[BLjava/lang/String;Ljava/lang/String;)V", "appearanceApply");
        wrap("net/openvoxel/client/MainMenuScreen", "build", "(FF)V", "mainMenuBuild");
        wrap("net/openvoxel/engine/ui/PauseMenu", "build", "(FF)V", "pauseMenuBuild");
        wrap("net/openvoxel/client/ErrorScreen", "build", "(FF)V", "errorScreenBuild");
        wrap("net/openvoxel/client/ClientMain", "startSingleplayer", "(Lnet/openvoxel/client/WorldSaves$Entry;)V", "startSingleplayer");
        wrap("net/openvoxel/client/ClientMain", "join", "(Ljava/lang/String;ILjava/lang/String;)V", "join");
        wrap("net/openvoxel/engine/ui/ChatHud", "addLine", "(Ljava/lang/String;)V", "chatLine");
        wrap("net/openvoxel/engine/world/block/BlockType", "getModel", "(J)Lnet/openvoxel/engine/world/block/model/BlockModel;", "blockModel");
        wrap("net/openvoxel/engine/world/block/BlockType", "getTextureForFace", "(IJ)Lnet/openvoxel/engine/rendering/Texture;", "blockTexture");
        wrap("net/openvoxel/engine/world/block/BlockType", "computePlacementState", "(Lnet/openvoxel/engine/world/block/BlockPlacement$PlacementContext;J)J", "placementState");
        wrap("net/openvoxel/client/ServerSelectScreen$ServerRowPanel", "renderWidget", "()V", "serverRowRender");
        wrap("net/openvoxel/engine/world/block/BlockPlacement", "canReplaceInto", "(Lnet/openvoxel/engine/world/block/BlockType;Lnet/openvoxel/engine/world/block/BlockType;J)Z", "canReplaceInto");
    }

    static final String BLOCK_TYPE = "net/openvoxel/engine/world/block/BlockType";
    private static final String SERVER_STATUS = "com/happyuky7/openvoxelmoding/loader/net/ServerStatus";
    private static final String PINGER = "net/openvoxel/client/ServerPinger", CLIENT_CONN = "net/openvoxel/server/GameServer$ClientConn";
    private static final String WRITER = "net/openvoxel/engine/net/Protocol$PayloadWriter";
    /** Dedicated server: only the native block registry is installed (no client hooks). */
    static volatile boolean serverMode;

    /** Classes whose GLFW.glfwGetKey calls go through Hooks.glfwGetKey (rebindable controls). */
    private static final Set<String> REDIRECT_KEYS = new HashSet<>(Arrays.asList("net/openvoxel/engine/input/InputHandler"));

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> redefined, ProtectionDomain domain, byte[] bytes) {
        if (serverMode) {
            if (!BLOCK_TYPE.equals(className) && !CLIENT_CONN.equals(className)) return null;
            try {
                ClassNode cn = new ClassNode();
                new ClassReader(bytes).accept(cn, 0);
                boolean ok = BLOCK_TYPE.equals(className) ? extendBlockType(cn) : patchStatusReply(cn);
                if (!ok) return null;
                ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                cn.accept(cw);
                System.out.println(BLOCK_TYPE.equals(className) ? "[ModLoader] BlockType open for native blocks (server)" : "[ModLoader] server list status: MOTD and max players");
                return cw.toByteArray();
            } catch (Throwable error) { System.err.println("[ModLoader] transform failed for " + className + ": " + error); return null; }
        }
        List<Target> targets = className == null ? null : TARGETS.get(className);
        // Enum constants with bodies (BlockType$1, $2...) override some hooked methods: wrap those overrides too.
        // Their renamed originals override BlockType's, so the hook's virtual call still reaches them.
        if (targets == null && className != null && className.matches("net/openvoxel/engine/world/block/BlockType\\$\\d+")) {
            List<Target> sub = new ArrayList<>();
            for (Target t : TARGETS.get("net/openvoxel/engine/world/block/BlockType")) if (t.name.equals("getTextureForFace") || t.name.equals("getModel")) sub.add(t);
            targets = sub;
        }
        boolean redirect = className != null && REDIRECT_KEYS.contains(className);
        boolean pinger = PINGER.equals(className);
        if (targets == null && !redirect && !pinger) return null;
        if (targets == null) targets = Collections.emptyList();
        try {
            ClassNode cn = new ClassNode();
            new ClassReader(bytes).accept(cn, 0);
            int done = 0;
            if (BLOCK_TYPE.equals(className) && extendBlockType(cn)) done++;
            if (pinger && patchPinger(cn)) done++;
            if (redirect) {
                for (MethodNode m : cn.methods) for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode) insn;
                        if (call.owner.equals("org/lwjgl/glfw/GLFW") && call.name.equals("glfwGetKey") && call.desc.equals("(JI)I")) {
                            call.owner = HOOKS; call.name = "glfwGetKey"; done++;
                        }
                    }
                }
            }
            for (MethodNode m : new ArrayList<>(cn.methods)) {
                for (Target t : targets) {
                    if (!m.name.equals(t.name) || !m.desc.equals(t.desc)) continue;
                    MethodNode copy = new MethodNode(Opcodes.ASM9, m.access, m.name + "$orig", m.desc, m.signature,
                            m.exceptions == null ? null : m.exceptions.toArray(new String[0]));
                    m.accept(copy);
                    cn.methods.add(copy);
                    rewrite(m, t);
                    done++;
                }
            }
            if (done == 0) return null;
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            System.out.println("[ModLoader] Hooked " + done + " method(s) in " + className);
            return cw.toByteArray();
        } catch (Throwable error) {
            System.err.println("[ModLoader] Transform failed for " + className + ": " + error);
            return null;
        }
    }

    /**
     * Native blocks: BlockType's constructors become package-private (so the generated subclass in the
     * same package can call them) and the array built by {@code $values()} in the static initializer is
     * passed through {@link NativeBlocks#extend}, which appends the registered mod blocks. Everything the
     * engine derives from {@code values()} afterwards (ID_LOOKUP, valueOf, EnumMaps) sees them.
     */
    private static boolean extendBlockType(ClassNode cn) {
        boolean patched = false;
        for (MethodNode m : cn.methods) {
            if (m.name.equals("<init>")) m.access &= ~Opcodes.ACC_PRIVATE;
            if (!m.name.equals("<clinit>")) continue;
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (insn instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) insn;
                    if (call.getOpcode() == Opcodes.INVOKESTATIC && call.owner.equals(BLOCK_TYPE) && call.name.equals("$values")) {
                        InsnList extra = new InsnList();
                        extra.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/happyuky7/openvoxelmoding/loader/NativeBlocks", "extend", "([Ljava/lang/Object;)[Ljava/lang/Object;", false));
                        extra.add(new TypeInsnNode(Opcodes.CHECKCAST, "[L" + BLOCK_TYPE + ";"));
                        m.instructions.insert(call, extra);
                        patched = true;
                    }
                }
            }
        }
        return patched;
    }

    /**
     * Server list (client): in {@code ServerPinger.probe(String, Result)} the status frame is read through
     * {@code ServerStatus.pingFrame(in, result)} so the MOTD a ModLoader server appends can be kept.
     */
    private static boolean patchPinger(ClassNode cn) {
        for (MethodNode m : cn.methods) {
            if (!m.name.equals("probe")) continue;
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) insn;
                if (call.owner.equals("net/openvoxel/engine/net/Protocol") && call.name.equals("readFrame") && call.desc.equals("(Ljava/io/DataInputStream;)[B")) {
                    m.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, 1));       // the Result argument
                    call.owner = SERVER_STATUS; call.name = "pingFrame"; call.desc = "(Ljava/io/DataInputStream;Ljava/lang/Object;)[B";
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Server list (server): the status reply {@code id(9).u32(online).u32(20)} becomes
     * {@code id(9).u32(online).u32(maxPlayers(20))} followed by {@code ServerStatus.extend(writer)} (MOTD).
     */
    private static boolean patchStatusReply(ClassNode cn) {
        for (MethodNode m : cn.methods) {
            if (!m.name.equals("run")) continue;
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (!(insn instanceof LdcInsnNode) || !Long.valueOf(20L).equals(((LdcInsnNode) insn).cst)) continue;
                AbstractInsnNode next = insn.getNext();
                if (!(next instanceof MethodInsnNode) || !((MethodInsnNode) next).owner.equals(WRITER) || !((MethodInsnNode) next).name.equals("u32")) continue;
                m.instructions.insert(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, SERVER_STATUS, "maxPlayers", "(J)J", false));
                m.instructions.insert(next, new MethodInsnNode(Opcodes.INVOKESTATIC, SERVER_STATUS, "extend", "(L" + WRITER + ";)L" + WRITER + ";", false));
                return true;
            }
        }
        return false;
    }

    /** Replaces the method body with {@code return Hooks.hook([this,] args...)}. */
    private static void rewrite(MethodNode m, Target t) {
        boolean isStatic = (m.access & Opcodes.ACC_STATIC) != 0;
        m.instructions.clear();
        m.tryCatchBlocks = new ArrayList<>();
        m.localVariables = null;
        m.visibleLocalVariableAnnotations = null;
        m.invisibleLocalVariableAnnotations = null;
        InsnList code = new InsnList();
        StringBuilder hookDesc = new StringBuilder("(");
        int slot = 0;
        if (!isStatic) { code.add(new VarInsnNode(Opcodes.ALOAD, 0)); hookDesc.append("Ljava/lang/Object;"); slot = 1; }
        for (Type arg : Type.getArgumentTypes(m.desc)) {
            code.add(new VarInsnNode(arg.getOpcode(Opcodes.ILOAD), slot));
            slot += arg.getSize();
            hookDesc.append(arg.getDescriptor());
        }
        Type ret = Type.getReturnType(m.desc);
        hookDesc.append(")").append(ret.getDescriptor());
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, t.hook, hookDesc.toString(), false));
        code.add(new InsnNode(ret.getOpcode(Opcodes.IRETURN)));
        m.instructions.add(code);
        m.maxStack = Math.max(slot, 1) + 2;
        m.maxLocals = slot;
    }
}
