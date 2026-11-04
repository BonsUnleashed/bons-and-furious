package bons.furious.patch.mcreator_pv_c49;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.service.MixinService;

/**
 * mcreator_shared_default_player_variables (since 1.0.49): the per-site rewrite, run by SharedDefaultsPlugin.preApply on
 * every class a carrier mixin targets, before Mixin applies the (empty) carrier.
 *
 * A site is rewritten only when the instructions are exactly NEW PV; DUP; INVOKESPECIAL PV.<init>()V; INVOKEVIRTUAL
 * net/minecraftforge/common/util/LazyOptional.orElse(Ljava/lang/Object;)Ljava/lang/Object;; CHECKCAST PV; GETFIELD PV.f
 * (labels, line numbers between them allowed, but no label any jump, switch or exception handler targets and no frame),
 * f is a SAFE field of PV and the class file is version 51 or newer. A SAFE field is one PV's no-argument constructor
 * (super() of java.lang.Object, then only "ALOAD 0; value; PUTFIELD PV.x" groups and RETURN, no exception table) leaves
 * holding a constant push (ACONST_NULL, ICONST/LCONST/FCONST/DCONST, BIPUSH, SIPUSH, LDC of a number or string) by its
 * last write, or never writes. NEW, DUP and INVOKESPECIAL become one INVOKEDYNAMIC "pv_f" ()PV whose bootstrap is
 * SharedDefaults.bootstrap; everything else in the class is left as it is. The same rule chose the carriers' target
 * classes at build time (tools/mixedproof/McreatorPvC49Census.py).
 */
public final class PvRewrite {
    static final String LAZY = "net/minecraftforge/common/util/LazyOptional";
    static final Handle BSM = new Handle(Opcodes.H_INVOKESTATIC, "bons/furious/patch/mcreator_pv_c49/SharedDefaults", "bootstrap",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
    /** PV internal name -> its SAFE fields (empty set when its constructor is not of the accepted shape). */
    private static final Map<String, Set<String>> SAFE = new ConcurrentHashMap<>();

    private PvRewrite() {
    }

    /** The safe fields of the PV class as Mixin's bytecode provider reads it; null when the class cannot be read. */
    public static Set<String> safeFields(String pv) {
        Set<String> known = SAFE.get(pv);
        if (known != null) return known;
        ClassNode node;
        try {
            node = MixinService.getService().getBytecodeProvider().getClassNode(pv.replace('/', '.'));
        } catch (Throwable t) {
            return null;
        }
        Set<String> safe = analyse(node);
        SAFE.put(pv, safe);
        return safe;
    }

    /** The SAFE fields of a PlayerVariables class (see the class comment); empty when its constructor has another shape. */
    public static Set<String> analyse(ClassNode pv) {
        Set<String> none = Set.of();
        if (!"java/lang/Object".equals(pv.superName)) return none;
        MethodNode init = null;
        for (MethodNode m : pv.methods) if (m.name.equals("<init>") && m.desc.equals("()V")) init = m;
        if (init == null || (init.tryCatchBlocks != null && !init.tryCatchBlocks.isEmpty())) return none;
        java.util.List<AbstractInsnNode> real = new java.util.ArrayList<>();
        for (AbstractInsnNode n : init.instructions) if (n.getOpcode() >= 0) real.add(n);
        if (real.size() < 3 || real.get(0).getOpcode() != Opcodes.ALOAD || ((VarInsnNode) real.get(0)).var != 0) return none;
        if (!(real.get(1) instanceof MethodInsnNode sup) || sup.getOpcode() != Opcodes.INVOKESPECIAL || !sup.owner.equals("java/lang/Object")
                || !sup.name.equals("<init>") || !sup.desc.equals("()V")) return none;
        java.util.Map<String, Boolean> last = new java.util.HashMap<>();
        int k = 2;
        while (k < real.size()) {
            AbstractInsnNode n = real.get(k);
            if (n.getOpcode() == Opcodes.RETURN) {
                if (k != real.size() - 1) return none;
                break;
            }
            if (n.getOpcode() != Opcodes.ALOAD || ((VarInsnNode) n).var != 0) return none;
            int j = k + 1;
            while (j < real.size() && real.get(j).getOpcode() != Opcodes.PUTFIELD) j++;
            if (j >= real.size()) return none;
            FieldInsnNode put = (FieldInsnNode) real.get(j);
            if (!put.owner.equals(pv.name)) return none;
            boolean constant = j == k + 2 && isConstant(real.get(k + 1));
            if (!constant) {
                for (int q = k + 1; q < j; q++) {
                    int op = real.get(q).getOpcode();
                    if (op == Opcodes.ALOAD && ((VarInsnNode) real.get(q)).var == 0 || op == Opcodes.PUTFIELD || op == Opcodes.PUTSTATIC) return none;
                }
            }
            last.put(put.name, constant);
            k = j + 1;
        }
        Set<String> safe = new HashSet<>();
        for (FieldNode f : pv.fields) {
            if ((f.access & Opcodes.ACC_STATIC) != 0) continue;
            if (last.getOrDefault(f.name, Boolean.TRUE)) safe.add(f.name);
        }
        return safe;
    }

    static boolean isConstant(AbstractInsnNode n) {
        int op = n.getOpcode();
        if (n instanceof InsnNode) return op >= Opcodes.ACONST_NULL && op <= Opcodes.DCONST_1;
        if (n instanceof IntInsnNode) return op == Opcodes.BIPUSH || op == Opcodes.SIPUSH;
        if (n instanceof LdcInsnNode l) return l.cst instanceof Integer || l.cst instanceof Float || l.cst instanceof Long || l.cst instanceof Double || l.cst instanceof String;
        return false;
    }

    /** The next real instruction after n, failing (null) on a frame or on a label in the targets. */
    private static AbstractInsnNode next(AbstractInsnNode n, Set<LabelNode> targets) {
        for (AbstractInsnNode x = n.getNext(); x != null; x = x.getNext()) {
            if (x instanceof FrameNode) return null;
            if (x instanceof LabelNode l) {
                if (targets.contains(l)) return null;
                continue;
            }
            if (x.getOpcode() >= 0) return x;
        }
        return null;
    }

    private static Set<LabelNode> targets(MethodNode m) {
        Set<LabelNode> out = new HashSet<>();
        for (AbstractInsnNode n : m.instructions) {
            if (n instanceof JumpInsnNode j) out.add(j.label);
            else if (n instanceof TableSwitchInsnNode t) {
                out.add(t.dflt);
                out.addAll(t.labels);
            } else if (n instanceof LookupSwitchInsnNode t) {
                out.add(t.dflt);
                out.addAll(t.labels);
            }
        }
        if (m.tryCatchBlocks != null) for (TryCatchBlockNode t : m.tryCatchBlocks) {
            out.add(t.start);
            out.add(t.end);
            out.add(t.handler);
        }
        return out;
    }

    /** Rewrites the accepted sites of the PV class in the target class; returns {accepted, kept}. */
    public static int[] rewrite(ClassNode target, String pv, Set<String> safe) {
        int accepted = 0, kept = 0;
        if ((target.version & 0xFFFF) < Opcodes.V1_7) return new int[] {0, -1};
        for (MethodNode m : target.methods) {
            Set<LabelNode> targets = null;
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                if (n.getOpcode() != Opcodes.NEW || !((TypeInsnNode) n).desc.equals(pv)) continue;
                if (targets == null) targets = targets(m);
                AbstractInsnNode dup = next(n, targets);
                AbstractInsnNode init = dup == null ? null : next(dup, targets);
                AbstractInsnNode orElse = init == null ? null : next(init, targets);
                AbstractInsnNode cast = orElse == null ? null : next(orElse, targets);
                AbstractInsnNode get = cast == null ? null : next(cast, targets);
                if (get == null || dup.getOpcode() != Opcodes.DUP
                        || !(init instanceof MethodInsnNode c) || c.getOpcode() != Opcodes.INVOKESPECIAL || !c.owner.equals(pv) || !c.name.equals("<init>") || !c.desc.equals("()V")
                        || !(orElse instanceof MethodInsnNode o) || o.getOpcode() != Opcodes.INVOKEVIRTUAL || !o.owner.equals(LAZY) || !o.name.equals("orElse")
                        || !o.desc.equals("(Ljava/lang/Object;)Ljava/lang/Object;")
                        || cast.getOpcode() != Opcodes.CHECKCAST || !((TypeInsnNode) cast).desc.equals(pv)
                        || !(get instanceof FieldInsnNode f) || f.getOpcode() != Opcodes.GETFIELD || !f.owner.equals(pv) || !safe.contains(f.name)) {
                    kept++;
                    continue;
                }
                InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode("pv_" + f.name, "()L" + pv + ";", BSM);
                m.instructions.set(init, indy);
                m.instructions.remove(dup);
                m.instructions.remove(n);
                n = indy;
                accepted++;
            }
        }
        return new int[] {accepted, kept};
    }
}
