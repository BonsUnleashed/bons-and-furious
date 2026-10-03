package bons.furious.guard;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/**
 * SHA-256 of a method's code: every real instruction with its operands, jump targets as instruction indices, and the
 * try/catch table. Line numbers, frames, local variable names and access flags are left out, so the value only
 * changes when the code itself changes. The build computes it from the tested mod versions; the mixin plugin
 * recomputes it before applying a patch.
 */
public final class Fingerprint {
    private Fingerprint() {}

    /** Explicit reviewed bytecode variants, e.g. NeoForm recompilation versus Mojang's production class. */
    public static boolean matches(String accepted, String actual) {
        for (String hash : accepted.split("\\|")) if (hash.equals(actual)) return true;
        return false;
    }

    public static String of(MethodNode m) {
        List<AbstractInsnNode> real = new ArrayList<>();
        Map<LabelNode, Integer> at = new HashMap<>();
        List<LabelNode> pending = new ArrayList<>();
        for (AbstractInsnNode n : m.instructions) {
            if (n instanceof LabelNode l) { pending.add(l); continue; }
            if (n.getOpcode() < 0) continue;
            for (LabelNode l : pending) at.put(l, real.size());
            pending.clear();
            real.add(n);
        }
        for (LabelNode l : pending) at.put(l, real.size());
        StringBuilder sb = new StringBuilder(real.size() * 24);
        for (AbstractInsnNode n : real) {
            sb.append(n.getOpcode());
            if (n instanceof IntInsnNode i) sb.append('|').append(i.operand);
            else if (n instanceof VarInsnNode v) sb.append('|').append(v.var);
            else if (n instanceof TypeInsnNode t) sb.append('|').append(t.desc);
            else if (n instanceof FieldInsnNode f) sb.append('|').append(f.owner).append('|').append(f.name).append('|').append(f.desc);
            else if (n instanceof MethodInsnNode mi) sb.append('|').append(mi.owner).append('|').append(mi.name).append('|').append(mi.desc).append('|').append(mi.itf);
            else if (n instanceof InvokeDynamicInsnNode d) {
                sb.append('|').append(d.name).append('|').append(d.desc).append('|').append(handle(d.bsm));
                for (Object a : d.bsmArgs) sb.append('|').append(constant(a));
            } else if (n instanceof JumpInsnNode j) sb.append('|').append(at.get(j.label));
            else if (n instanceof LdcInsnNode l) sb.append('|').append(constant(l.cst));
            else if (n instanceof IincInsnNode i) sb.append('|').append(i.var).append('|').append(i.incr);
            else if (n instanceof TableSwitchInsnNode t) {
                sb.append('|').append(t.min).append('|').append(t.max).append('|').append(at.get(t.dflt));
                for (LabelNode l : t.labels) sb.append('|').append(at.get(l));
            } else if (n instanceof LookupSwitchInsnNode t) {
                sb.append('|').append(at.get(t.dflt));
                for (int i = 0; i < t.keys.size(); i++) sb.append('|').append(t.keys.get(i)).append(':').append(at.get(t.labels.get(i)));
            } else if (n instanceof MultiANewArrayInsnNode a) sb.append('|').append(a.desc).append('|').append(a.dims);
            sb.append('\n');
        }
        for (TryCatchBlockNode t : m.tryCatchBlocks) {
            sb.append("catch|").append(at.get(t.start)).append('|').append(at.get(t.end)).append('|').append(at.get(t.handler)).append('|').append(t.type).append('\n');
        }
        return sha256(sb.toString());
    }

    /**
     * SHA-256 of a class's declared methods (name and descriptor, sorted). A guard entry with method "*" uses it to
     * notice a method a newer build ADDS, for example a subclass that starts overriding a method whose inherited
     * behaviour a patch relies on; method fingerprints alone cannot see a method that did not exist before.
     */
    public static String shape(ClassNode c) {
        List<String> names = new ArrayList<>();
        for (MethodNode m : c.methods) names.add(m.name + m.desc);
        java.util.Collections.sort(names);
        return sha256(String.join("\n", names));
    }

    private static String handle(Handle h) {
        return h.getTag() + ":" + h.getOwner() + "." + h.getName() + h.getDesc() + (h.isInterface() ? ":itf" : "");
    }

    private static String constant(Object c) {
        if (c instanceof Handle h) return "H" + handle(h);
        if (c instanceof Type t) return "T" + t.getDescriptor();
        if (c instanceof String s) return "S" + s;
        if (c instanceof Float f) return "F" + Float.floatToRawIntBits(f);
        if (c instanceof Double d) return "D" + Double.doubleToRawLongBits(d);
        if (c instanceof Long l) return "J" + l;
        if (c instanceof Integer i) return "I" + i;
        return c.getClass().getSimpleName() + ":" + c;
    }

    static String sha256(String text) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) sb.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
