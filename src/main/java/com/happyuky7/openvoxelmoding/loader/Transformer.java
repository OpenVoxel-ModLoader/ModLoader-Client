package com.happyuky7.openvoxelmoding.loader;

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
    private static final String HOOKS = "com/happyuky7/openvoxelmoding/loader/Hooks";
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
        wrap("net/openvoxel/client/ClientMain", "startSingleplayer", "(Lnet/openvoxel/client/WorldSaves$Entry;)V", "startSingleplayer");
        wrap("net/openvoxel/client/ClientMain", "join", "(Ljava/lang/String;ILjava/lang/String;)V", "join");
        wrap("net/openvoxel/engine/ui/ChatHud", "addLine", "(Ljava/lang/String;)V", "chatLine");
        wrap("net/openvoxel/engine/world/block/BlockType", "getModel", "(J)Lnet/openvoxel/engine/world/block/model/BlockModel;", "blockModel");
        wrap("net/openvoxel/engine/world/block/BlockType", "computePlacementState", "(Lnet/openvoxel/engine/world/block/BlockPlacement$PlacementContext;J)J", "placementState");
        wrap("net/openvoxel/engine/world/block/BlockPlacement", "canReplaceInto", "(Lnet/openvoxel/engine/world/block/BlockType;Lnet/openvoxel/engine/world/block/BlockType;J)Z", "canReplaceInto");
    }

    /** Classes whose GLFW.glfwGetKey calls go through Hooks.glfwGetKey (rebindable controls). */
    private static final Set<String> REDIRECT_KEYS = new HashSet<>(Arrays.asList("net/openvoxel/engine/input/InputHandler"));

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> redefined, ProtectionDomain domain, byte[] bytes) {
        List<Target> targets = className == null ? null : TARGETS.get(className);
        boolean redirect = className != null && REDIRECT_KEYS.contains(className);
        if (targets == null && !redirect) return null;
        if (targets == null) targets = Collections.emptyList();
        try {
            ClassNode cn = new ClassNode();
            new ClassReader(bytes).accept(cn, 0);
            int done = 0;
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
