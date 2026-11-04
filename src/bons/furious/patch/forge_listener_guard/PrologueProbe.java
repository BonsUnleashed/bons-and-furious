package bons.furious.patch.forge_listener_guard;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventListener;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * forge_listener_prologue_guard: the independent check of a guard, used by shadow mode and the offline harness.
 *
 * A probe is the listener method's own instructions, copied into a static method `probe(...)Z` of a new class in the
 * listener's package (so every class, field and method it names resolves as in the original): each void RETURN
 * becomes "return true" and each instruction where the analysis stopped a path as "runs" gets "return false" in front
 * of it (after its label, so every jump to it returns false too). Code behind those returns is unreachable and ASM's
 * frame computation replaces it with NOP/ATHROW; try/catch blocks are dropped (a guarded method has none over the code
 * that remains). Run on an event, the probe follows the very path the listener would take and answers whether that path
 * is one of its early returns through the accepted instructions alone. A guard is right when it says "skip" only where
 * the probe says true (shadow mode counts the opposite as a mismatch).
 */
public final class PrologueProbe {
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private PrologueProbe() {}

    static MethodHandle probeFor(GuardTable.ListenerGuard lg) {
        if (!lg.probeTried) {
            synchronized (lg) {
                if (!lg.probeTried) {
                    try {
                        lg.probe = define(lg.impl.getDeclaringClass(), lg.code, lg.isStatic, lg.result);
                    } catch (Throwable t) {
                        lg.probe = null;
                        GuardedDispatch.LOGGER.debug("Bons and Furious: forge_listener_prologue_guard: no probe for {} ({})", lg.name, t.toString());
                    }
                    lg.probeTried = true;
                }
            }
        }
        return lg.probe;
    }

    static boolean run(MethodHandle p, GuardTable.ListenerGuard lg, IEventListener l, Event e) throws Throwable {
        if (lg.isStatic) return (boolean) p.invoke(e);
        Object instance = ListenerTargets.instance(l);
        return (boolean) p.invoke(instance, e);
    }

    /** Defines the probe class next to the listener's class and returns its probe method (static or (owner, event)). */
    public static MethodHandle define(Class<?> owner, MethodNode m, boolean isStatic, PrologueAnalyser.Result r) throws Throwable {
        String ownerName = Type.getInternalName(owner);
        String name = ownerName + "$BonsProbe" + COUNTER.incrementAndGet();
        byte[] bytes = bytes(name, ownerName, m, isStatic, r.runLeaves, owner.getClassLoader());
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(owner, MethodHandles.lookup());
        Class<?> probe = lookup.defineClass(bytes);
        Type[] args = Type.getArgumentTypes(m.desc);
        Class<?> eventType = Class.forName(args[0].getClassName(), false, owner.getClassLoader());
        MethodType mt = isStatic ? MethodType.methodType(boolean.class, eventType) : MethodType.methodType(boolean.class, owner, eventType);
        return lookup.findStatic(probe, "probe", mt).asType(isStatic ? MethodType.methodType(boolean.class, Object.class)
                : MethodType.methodType(boolean.class, Object.class, Object.class));
    }

    /** The probe class's bytes (public for the harness). */
    public static byte[] bytes(String name, String ownerName, MethodNode m, boolean isStatic, Set<Integer> runLeaves, ClassLoader loader) {
        ClassNode cn = new ClassNode();
        cn.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, name, null, "java/lang/Object", null);
        String desc = isStatic ? m.desc.replace(")V", ")Z") : "(L" + ownerName + ";" + m.desc.substring(1).replace(")V", ")Z");
        MethodNode p = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "probe", desc, null, null);
        AbstractInsnNode[] code = m.instructions.toArray();
        Map<LabelNode, LabelNode> labels = new HashMap<>();
        for (AbstractInsnNode n : code) if (n instanceof LabelNode l) labels.put(l, new LabelNode());
        InsnList out = new InsnList();
        for (int i = 0; i < code.length; i++) {
            AbstractInsnNode n = code[i];
            if (runLeaves.contains(i) && n.getOpcode() >= 0) {
                out.add(new InsnNode(Opcodes.ICONST_0));
                out.add(new InsnNode(Opcodes.IRETURN));
            }
            if (n.getOpcode() == Opcodes.RETURN) {
                out.add(new InsnNode(Opcodes.ICONST_1));
                out.add(new InsnNode(Opcodes.IRETURN));
                continue;
            }
            if (n.getType() == AbstractInsnNode.FRAME) continue;
            out.add(n.clone(labels));
        }
        p.instructions = out;
        p.maxLocals = m.maxLocals;
        p.maxStack = Math.max(m.maxStack, 1);
        cn.methods.add(p);
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String a, String b) {
                try {
                    Class<?> x = Class.forName(a.replace('/', '.'), false, loader), y = Class.forName(b.replace('/', '.'), false, loader);
                    if (x.isAssignableFrom(y)) return a;
                    if (y.isAssignableFrom(x)) return b;
                    if (x.isInterface() || y.isInterface()) return "java/lang/Object";
                    do {
                        x = x.getSuperclass();
                    } while (!x.isAssignableFrom(y));
                    return Type.getInternalName(x);
                } catch (Throwable t) {
                    return "java/lang/Object";
                }
            }
        };
        cn.accept(w);
        return w.toByteArray();
    }
}
