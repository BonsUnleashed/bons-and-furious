package bons.furious.patch.modernfix_capability_guards;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Bons and Furious switch modernfix_capability_guards (ModernFix 5.27.77 and 5.27.89, whose capability classes are
 * byte-identical, on Forge 47.4.16 / 47.4.28; both sides): the proof that lets ModernFix's dispatcher generator skip a
 * capability provider. Our own design, no ModernFix code: ModernFix's analyzer only names the capabilities it believes a
 * provider can answer; this class decides whether that claim is safe to act on.
 *
 * <p>What ModernFix does with a claim. With bytecode analysis on, CapabilityProviderDispatcherGenerator builds a dispatcher
 * that calls a provider whose analysis is KnownCapabilities (at most five) only when the asked capability is one of those
 * capabilities (an identity test against the static field values, read once when the dispatcher class is generated), and
 * never calls a provider whose analysis is AlwaysEmpty. Every other provider is called as at ModernFix's GA level, in the
 * same order.
 *
 * <p>What has to be true for that to change nothing. For every capability object c outside the claimed set K (null
 * included) and every side (any Direction or null), the provider's getCapability(c, side), as it really runs, must:
 * return the LazyOptional.empty() singleton (the generated dispatcher, like ModernFix's GA dispatcher, then moves on
 * without asking it anything else: GeneratedDispatcher.nonEmptyOrNull tests identity with that singleton first); write
 * nothing; call nothing that could; throw nothing. Then skipping the call is invisible: same answer, same objects, same
 * order, same exceptions. That is what {@link #proveBody} checks, path by path, over the method's bytecode.
 *
 * <p>The body checker is a small abstract interpreter over the instructions of getCapability. It follows every path that
 * a capability outside K can take: a comparison of the capability with a static capability field is decided by the field's
 * value (identity, after aliases); a comparison with anything else, or of the side, takes both edges. It accepts only these
 * instructions on such paths: local loads and stores, GETFIELD on this (cannot throw), GETSTATIC of a static final
 * Capability field (see the field rules below) or of a Direction constant, ACONST_NULL, DUP, POP, CHECKCAST to LazyOptional
 * of a value that is a LazyOptional or null, reference comparisons and null tests, GOTO, ARETURN, and these calls whose
 * effects are known exactly from Forge's code: LazyOptional.empty() (the singleton), LazyOptional.of(supplier) (null gives
 * the singleton, otherwise a new optional), LazyOptional.cast() on a value known to be non-null (returns itself),
 * Capability.orEmpty(cap, inst) on a static capability field (returns the singleton when cap is a different object, without
 * touching inst), and an invokedynamic LambdaMetafactory call (a new lambda object). Anything else on such a path (another
 * call, a field write, an exception handler, a dereference of a value that may be null, a loop beyond the step budget)
 * refuses the provider, and the provider is then called for every capability, as without this switch. Each path must end
 * in ARETURN of the singleton.
 *
 * <p>Field rules, for every GETSTATIC of a Capability field the body reads: the field must be found the way ModernFix's
 * generator finds it (declared in the named class or one of its direct interfaces), be static and final, be non-null, and
 * reading it must not run code that would otherwise run at another moment: the named class is the provider's own class or
 * one of its superclasses (initialized, since a provider object exists), or its static initializer is one of the pure
 * shapes {@link #pureStaticInit} accepts (Forge capability tokens and CapabilityManager.get, constants, stores to its own
 * fields). ModernFix's generator reads the same fields when it builds the dispatcher class, earlier than the provider's
 * first getCapability call would have; this rule makes that earlier read change nothing either.
 *
 * <p>The mixin rule (refusing classes another mod transforms) and the class-file source are the caller's
 * ({@link CapabilityGuards}); this class only judges the bytecode it is given.
 */
public final class ProviderProof {
    static final String CAPABILITY = "net/minecraftforge/common/capabilities/Capability";
    static final String CAPABILITY_DESC = "L" + CAPABILITY + ";";
    static final String LAZY_OPTIONAL = "net/minecraftforge/common/util/LazyOptional";
    static final String LAZY_OPTIONAL_DESC = "L" + LAZY_OPTIONAL + ";";
    static final String DIRECTION = "net/minecraft/core/Direction";
    static final String DIRECTION_DESC = "L" + DIRECTION + ";";
    static final String GET_CAPABILITY_DESC = "(" + CAPABILITY_DESC + DIRECTION_DESC + ")" + LAZY_OPTIONAL_DESC;
    static final String TOKEN = "net/minecraftforge/common/capabilities/CapabilityToken";
    static final String MANAGER = "net/minecraftforge/common/capabilities/CapabilityManager";
    static final Set<String> DIRECTIONS = Set.of("f_122424_", "f_122425_", "f_122426_", "f_122427_", "f_122428_", "f_122429_",
            "DOWN", "UP", "NORTH", "SOUTH", "WEST", "EAST");
    /** Paths x instructions explored per method before the body is refused (real bodies need a few dozen). */
    static final int STEP_BUDGET = 20_000;

    private ProviderProof() {
    }

    /** How the checker learns about static Capability fields. */
    public interface Fields {
        /**
         * The value of the static field {@code owner.name}, or null when it does not satisfy the field rules (the reason is
         * then put in {@code why[0]}). Values are compared by identity, so aliases of one capability are the same object.
         */
        Object capability(String owner, String name, String[] why);
    }

    /** The result for one body: null = proven; otherwise the reason it is refused. */
    public static String proveBody(ClassNode owner, MethodNode method, Set<Object> known, Fields fields) {
        if (method == null) return "getCapability is not in the class file";
        if ((method.access & Opcodes.ACC_STATIC) != 0 || (method.access & Opcodes.ACC_ABSTRACT) != 0) return "getCapability is static or abstract";
        if (!GET_CAPABILITY_DESC.equals(method.desc)) return "unexpected descriptor " + method.desc;
        if (method.tryCatchBlocks != null && !method.tryCatchBlocks.isEmpty()) return "has an exception handler";
        InsnList insns = method.instructions;
        if (insns == null || insns.size() == 0) return "has no code";
        Interpreter it = new Interpreter(owner, insns, known, fields);
        return it.run();
    }

    // ------------------------------------------------------------------------------------------------------------- values

    /** Abstract values of the interpreter. Immutable; equal kinds with equal payloads are equal. */
    static final class V {
        final int kind;
        final Object payload;   // REF: the capability object; DIR: the constant's name; FIELD: owner.name:desc

        private V(int kind, Object payload) {
            this.kind = kind;
            this.payload = payload;
        }

        static final int THIS = 0, CAP = 1, SIDE = 2, NULL = 3, EMPTY = 4, FRESH_OPT = 5, LAMBDA = 6, FIELD = 7, REF = 8, DIR = 9, OTHER = 10,
                FIELD_NN = 11;   // a final field of this class that every constructor sets to a non-null value
        static final V V_THIS = new V(THIS, null), V_CAP = new V(CAP, null), V_SIDE = new V(SIDE, null), V_NULL = new V(NULL, null),
                V_EMPTY = new V(EMPTY, null), V_FRESH_OPT = new V(FRESH_OPT, null), V_LAMBDA = new V(LAMBDA, null), V_OTHER = new V(OTHER, null);

        static V ref(Object capability) {
            return new V(REF, capability);
        }

        static V dir(String name) {
            return new V(DIR, name);
        }

        static V field(String id, boolean nonNull) {
            return new V(nonNull ? FIELD_NN : FIELD, id);
        }

        /** True when the value cannot be null. */
        boolean nonNull() {
            return kind == THIS || kind == EMPTY || kind == FRESH_OPT || kind == LAMBDA || kind == REF || kind == DIR || kind == FIELD_NN;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof V v) || v.kind != kind) return false;
            return kind == REF ? v.payload == payload : Objects.equals(v.payload, payload);
        }

        @Override
        public int hashCode() {
            return kind * 31 + (kind == REF ? System.identityHashCode(payload) : Objects.hashCode(payload));
        }

        @Override
        public String toString() {
            return switch (kind) {
                case THIS -> "this"; case CAP -> "cap"; case SIDE -> "side"; case NULL -> "null"; case EMPTY -> "EMPTY";
                case FRESH_OPT -> "new LazyOptional"; case LAMBDA -> "lambda"; case FIELD -> "field " + payload; case FIELD_NN -> "final field " + payload;
                case REF -> "capability field"; case DIR -> "Direction." + payload; default -> "value";
            };
        }
    }

    /**
     * One state: locals, stack and what this path knows about the asked capability: either it IS one object (capIs, a
     * capability outside K, or NULL_CAP for null), or it is none of the objects in capIsNot (K and every capability field
     * this path compared it with and found different), and possibly known non-null.
     */
    static final class State {
        static final Object NULL_CAP = new Object();
        final V[] locals;
        final V[] stack;
        final int sp;
        final Object capIs;
        final Set<Object> capIsNot;   // identity set, shared copy-on-write
        final boolean capNonNull;

        State(V[] locals, V[] stack, int sp, Object capIs, Set<Object> capIsNot, boolean capNonNull) {
            this.locals = locals;
            this.stack = stack;
            this.sp = sp;
            this.capIs = capIs;
            this.capIsNot = capIsNot;
            this.capNonNull = capNonNull;
        }

        State withStack(V[] stack, int sp) {
            return new State(locals, stack, sp, capIs, capIsNot, capNonNull);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof State s)) return false;
            return sp == s.sp && capIs == s.capIs && capNonNull == s.capNonNull && Arrays.equals(locals, s.locals)
                    && Arrays.equals(Arrays.copyOf(stack, sp), Arrays.copyOf(s.stack, s.sp)) && sameSet(capIsNot, s.capIsNot);
        }

        @Override
        public int hashCode() {
            int h = Arrays.hashCode(locals) * 31 + sp;
            for (int i = 0; i < sp; i++) h = h * 31 + Objects.hashCode(stack[i]);
            h = h * 31 + System.identityHashCode(capIs);
            for (Object o : capIsNot) h += System.identityHashCode(o);
            return h * 31 + (capNonNull ? 1 : 0);
        }

        private static boolean sameSet(Set<Object> a, Set<Object> b) {
            if (a == b) return true;
            if (a.size() != b.size()) return false;
            for (Object o : a) if (!b.contains(o)) return false;
            return true;
        }
    }

    // -------------------------------------------------------------------------------------------------------- interpreter

    static final class Interpreter {
        final ClassNode owner;
        final InsnList insns;
        final Set<Object> known;
        final Fields fields;
        final Map<String, Object> resolved = new java.util.HashMap<>();   // owner.name -> capability object (or the MISSING marker)
        static final Object MISSING = new Object();
        String refused;

        Interpreter(ClassNode owner, InsnList insns, Set<Object> known, Fields fields) {
            this.owner = owner;
            this.insns = insns;
            this.known = known;
            this.fields = fields;
        }

        String run() {
            int maxLocals = 3;
            for (AbstractInsnNode n : insns) if (n instanceof VarInsnNode v) maxLocals = Math.max(maxLocals, v.var + 1);
            V[] locals = new V[maxLocals];
            Arrays.fill(locals, V.V_OTHER);
            locals[0] = V.V_THIS;
            locals[1] = V.V_CAP;
            locals[2] = V.V_SIDE;
            Set<Object> notK = Collections.newSetFromMap(new IdentityHashMap<>());
            notK.addAll(known);
            ArrayDeque<Object[]> work = new ArrayDeque<>();
            Set<Object> seen = new HashSet<>();
            State start = new State(locals, new V[8], 0, null, notK, false);
            work.add(new Object[] {0, start});
            int steps = 0;
            while (!work.isEmpty()) {
                Object[] item = work.poll();
                int at = (Integer) item[0];
                State s = (State) item[1];
                if (!seen.add(List.of(at, s))) continue;
                while (true) {
                    if (++steps > STEP_BUDGET) return "too many paths";
                    if (at < 0 || at >= insns.size()) return "falls off the end of the method";
                    AbstractInsnNode n = insns.get(at);
                    int op = n.getOpcode();
                    if (op < 0) { at++; continue; }   // labels, line numbers, frames
                    Object[] next = step(n, s, at, work);
                    if (refused != null) return refused;
                    if (next == null) break;          // ARETURN (checked) or both edges queued
                    at = (Integer) next[0];
                    s = (State) next[1];
                }
            }
            return null;
        }

        private Object[] refuse(String why) {
            if (refused == null) refused = why;
            return null;
        }

        private V[] push(State s, V v) {
            V[] st = Arrays.copyOf(s.stack, Math.max(s.stack.length, s.sp + 1));
            st[s.sp] = v;
            return st;
        }

        private Object[] go(int at, State s) {
            return new Object[] {at, s};
        }

        /** One instruction; returns the next (index, state) or null when this path ended or forked. */
        private Object[] step(AbstractInsnNode n, State s, int at, ArrayDeque<Object[]> work) {
            int op = n.getOpcode();
            switch (op) {
                case Opcodes.NOP:
                    return go(at + 1, s);
                case Opcodes.ACONST_NULL:
                    return go(at + 1, s.withStack(push(s, V.V_NULL), s.sp + 1));
                case Opcodes.ALOAD: {
                    V v = s.locals[((VarInsnNode) n).var];
                    return go(at + 1, s.withStack(push(s, v), s.sp + 1));
                }
                case Opcodes.ASTORE: {
                    if (s.sp < 1) return refuse("stack underflow");
                    V[] locals = s.locals.clone();
                    locals[((VarInsnNode) n).var] = s.stack[s.sp - 1];
                    return go(at + 1, new State(locals, s.stack, s.sp - 1, s.capIs, s.capIsNot, s.capNonNull));
                }
                case Opcodes.DUP: {
                    if (s.sp < 1) return refuse("stack underflow");
                    return go(at + 1, s.withStack(push(s, s.stack[s.sp - 1]), s.sp + 1));
                }
                case Opcodes.POP: {
                    if (s.sp < 1) return refuse("stack underflow");
                    return go(at + 1, s.withStack(s.stack, s.sp - 1));
                }
                case Opcodes.GETFIELD: {
                    FieldInsnNode f = (FieldInsnNode) n;
                    if (s.sp < 1 || s.stack[s.sp - 1].kind != V.THIS) return refuse("reads a field of an object that may be null (" + f.name + ")");
                    V[] st = s.stack.clone();
                    st[s.sp - 1] = V.field(f.owner + "." + f.name + ":" + f.desc, nonNullField(f));
                    return go(at + 1, s.withStack(st, s.sp));
                }
                case Opcodes.GETSTATIC: {
                    FieldInsnNode f = (FieldInsnNode) n;
                    V v;
                    if (f.desc.equals(CAPABILITY_DESC)) {
                        Object c = capability(f.owner, f.name);
                        if (c == null) return null;
                        v = V.ref(c);
                    } else if (f.owner.equals(DIRECTION) && f.desc.equals(DIRECTION_DESC) && DIRECTIONS.contains(f.name)) {
                        v = V.dir(f.name);
                    } else {
                        return refuse("reads static field " + f.owner + "." + f.name);
                    }
                    return go(at + 1, s.withStack(push(s, v), s.sp + 1));
                }
                case Opcodes.CHECKCAST: {
                    TypeInsnNode t = (TypeInsnNode) n;
                    if (s.sp < 1) return refuse("stack underflow");
                    V v = s.stack[s.sp - 1];
                    boolean optional = v.kind == V.EMPTY || v.kind == V.FRESH_OPT || v.kind == V.NULL
                            || (v.kind == V.FIELD || v.kind == V.FIELD_NN) && ((String) v.payload).endsWith(":" + LAZY_OPTIONAL_DESC);
                    if (!t.desc.equals(LAZY_OPTIONAL) || !optional) return refuse("casts " + v + " to " + t.desc);
                    return go(at + 1, s);
                }
                case Opcodes.INVOKESTATIC:
                case Opcodes.INVOKEVIRTUAL:
                case Opcodes.INVOKESPECIAL:
                case Opcodes.INVOKEINTERFACE:
                    return call((MethodInsnNode) n, s, at);
                case Opcodes.INVOKEDYNAMIC: {
                    InvokeDynamicInsnNode d = (InvokeDynamicInsnNode) n;
                    Handle bsm = d.bsm;
                    if (!bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")
                            || !(bsm.getName().equals("metafactory") || bsm.getName().equals("altMetafactory")))
                        return refuse("invokedynamic other than a lambda (" + bsm.getOwner() + "." + bsm.getName() + ")");
                    int args = Type.getArgumentTypes(d.desc).length;
                    for (Type a : Type.getArgumentTypes(d.desc)) if (a.getSize() != 1) return refuse("lambda captures a wide value");
                    if (s.sp < args) return refuse("stack underflow");
                    V[] st = Arrays.copyOf(s.stack, Math.max(s.stack.length, s.sp - args + 1));
                    st[s.sp - args] = V.V_LAMBDA;
                    return go(at + 1, s.withStack(st, s.sp - args + 1));
                }
                case Opcodes.IF_ACMPEQ:
                case Opcodes.IF_ACMPNE:
                    return compare((JumpInsnNode) n, s, at, work);
                case Opcodes.IFNULL:
                case Opcodes.IFNONNULL:
                    return nullTest((JumpInsnNode) n, s, at, work);
                case Opcodes.GOTO:
                    return go(insns.indexOf(((JumpInsnNode) n).label), s);
                case Opcodes.ARETURN: {
                    if (s.sp < 1) return refuse("stack underflow");
                    V v = s.stack[s.sp - 1];
                    if (v.kind != V.EMPTY) return refuse("may answer " + v + " for a capability it is not called for");
                    return null;
                }
                default:
                    return refuse("instruction " + op);
            }
        }

        final Map<String, Boolean> nonNullFields = new java.util.HashMap<>();

        /** this.f read in getCapability: non-null when f is a final field of this class that every constructor sets non-null. */
        private boolean nonNullField(FieldInsnNode f) {
            if (!f.owner.equals(owner.name)) return false;
            return nonNullFields.computeIfAbsent(f.name + ":" + f.desc, k -> finalNonNull(owner, f.name, f.desc) == null);
        }

        private Object capability(String fieldOwner, String name) {
            String id = fieldOwner + "." + name;
            Object c = resolved.get(id);
            if (c == null) {
                String[] why = new String[1];
                c = fields.capability(fieldOwner, name, why);
                if (c == null) {
                    resolved.put(id, MISSING);
                    refuse("capability field " + fieldOwner.replace('/', '.') + "." + name + ": " + (why[0] == null ? "not usable" : why[0]));
                    return null;
                }
                resolved.put(id, c);
            } else if (c == MISSING) {
                return null;
            }
            return c;
        }

        private Object[] call(MethodInsnNode m, State s, int at) {
            String sig = m.owner + "." + m.name + m.desc;
            if (m.getOpcode() == Opcodes.INVOKESTATIC && sig.equals(LAZY_OPTIONAL + ".empty()" + LAZY_OPTIONAL_DESC))
                return go(at + 1, s.withStack(push(s, V.V_EMPTY), s.sp + 1));
            if (m.getOpcode() == Opcodes.INVOKESTATIC && sig.equals(LAZY_OPTIONAL + ".of(Lnet/minecraftforge/common/util/NonNullSupplier;)" + LAZY_OPTIONAL_DESC)) {
                if (s.sp < 1) return refuse("stack underflow");
                V a = s.stack[s.sp - 1];
                V[] st = s.stack.clone();
                if (a.kind == V.NULL) st[s.sp - 1] = V.V_EMPTY;
                else if (a.nonNull()) st[s.sp - 1] = V.V_FRESH_OPT;
                else return refuse("LazyOptional.of with a supplier that may be null");
                return go(at + 1, s.withStack(st, s.sp));
            }
            if (m.getOpcode() == Opcodes.INVOKEVIRTUAL && sig.equals(LAZY_OPTIONAL + ".cast()" + LAZY_OPTIONAL_DESC)) {
                if (s.sp < 1) return refuse("stack underflow");
                V r = s.stack[s.sp - 1];
                if (r.kind == V.EMPTY || r.kind == V.FRESH_OPT) return go(at + 1, s);
                // LazyOptional.cast() returns the object itself; a final LazyOptional field set non-null by every constructor
                if (r.kind == V.FIELD_NN && ((String) r.payload).endsWith(":" + LAZY_OPTIONAL_DESC)) return go(at + 1, s);
                return refuse("calls cast() on " + r + ", which may be null");
            }
            if (m.getOpcode() == Opcodes.INVOKEVIRTUAL && sig.equals(CAPABILITY + ".orEmpty(" + CAPABILITY_DESC + LAZY_OPTIONAL_DESC + ")" + LAZY_OPTIONAL_DESC)) {
                if (s.sp < 3) return refuse("stack underflow");
                V recv = s.stack[s.sp - 3], asked = s.stack[s.sp - 2];
                if (recv.kind != V.REF) return refuse("orEmpty on " + recv);
                Object c = recv.payload;
                V result;
                if (asked.kind == V.CAP) {
                    if (s.capIs != null) result = s.capIs == c ? null : V.V_EMPTY;   // capIs is never in K
                    else result = s.capIsNot.contains(c) ? V.V_EMPTY : null;
                } else if (asked.kind == V.REF) {
                    result = asked.payload == c ? null : V.V_EMPTY;
                } else if (asked.kind == V.NULL) {
                    result = V.V_EMPTY;   // this == null is false: Capability.orEmpty returns the singleton
                } else {
                    return refuse("orEmpty asked about " + asked);
                }
                if (result == null) return refuse("may answer through orEmpty for a capability it is not called for");
                V[] st = s.stack.clone();
                st[s.sp - 3] = result;
                return go(at + 1, s.withStack(st, s.sp - 2));
            }
            return refuse("calls " + m.owner.replace('/', '.') + "." + m.name);
        }

        private Object[] fork(ArrayDeque<Object[]> work, int target, State taken, State fall, int at) {
            if (taken != null) work.add(new Object[] {target, taken});
            if (fall != null) return go(at + 1, fall);
            return null;
        }

        /** IF_ACMPEQ / IF_ACMPNE. */
        private Object[] compare(JumpInsnNode j, State s, int at, ArrayDeque<Object[]> work) {
            if (s.sp < 2) return refuse("stack underflow");
            V a = s.stack[s.sp - 2], b = s.stack[s.sp - 1];
            State base = s.withStack(s.stack, s.sp - 2);
            int target = insns.indexOf(j.label);
            boolean jumpIfEqual = j.getOpcode() == Opcodes.IF_ACMPEQ;
            // the asked capability against a capability field
            if (a.kind == V.CAP && b.kind == V.REF || a.kind == V.REF && b.kind == V.CAP) {
                Object c = a.kind == V.REF ? a.payload : b.payload;
                State eq = null, ne = null;
                if (s.capIs != null) {
                    if (s.capIs == c) eq = base; else ne = base;
                } else if (s.capIsNot.contains(c)) {
                    ne = base;
                } else {
                    eq = new State(base.locals, base.stack, base.sp, c, base.capIsNot, true);
                    Set<Object> more = Collections.newSetFromMap(new IdentityHashMap<>());
                    more.addAll(base.capIsNot);
                    more.add(c);
                    ne = new State(base.locals, base.stack, base.sp, null, more, base.capNonNull);
                }
                return jumpIfEqual ? fork(work, target, eq, ne, at) : fork(work, target, ne, eq, at);
            }
            // two values whose identity is known
            Boolean same = knownSame(a, b, s);
            if (same != null) {
                boolean jump = same == jumpIfEqual;
                return jump ? go(target, base) : go(at + 1, base);
            }
            // anything else is a pure comparison: both edges
            return fork(work, target, base, base, at);
        }

        private Boolean knownSame(V a, V b, State s) {
            if (a.kind == V.REF && b.kind == V.REF) return a.payload == b.payload;
            if (a.kind == V.DIR && b.kind == V.DIR) return dirName(a).equals(dirName(b));
            if (a.kind == V.NULL && b.kind == V.NULL) return true;
            if (a.kind == V.NULL && b.nonNull() || b.kind == V.NULL && a.nonNull()) return false;
            if (a.kind == V.CAP && b.kind == V.CAP || a.kind == V.SIDE && b.kind == V.SIDE || a.kind == V.THIS && b.kind == V.THIS) return true;
            if (a.kind == V.CAP && b.kind == V.NULL || a.kind == V.NULL && b.kind == V.CAP) {
                if (s.capIs == State.NULL_CAP) return true;
                if (s.capIs != null || s.capNonNull) return false;
            }
            return null;
        }

        private static String dirName(V v) {
            String n = (String) v.payload;
            return switch (n) {
                case "f_122424_" -> "DOWN"; case "f_122425_" -> "UP"; case "f_122426_" -> "NORTH"; case "f_122427_" -> "SOUTH";
                case "f_122428_" -> "WEST"; case "f_122429_" -> "EAST"; default -> n;
            };
        }

        /** IFNULL / IFNONNULL. */
        private Object[] nullTest(JumpInsnNode j, State s, int at, ArrayDeque<Object[]> work) {
            if (s.sp < 1) return refuse("stack underflow");
            V v = s.stack[s.sp - 1];
            State base = s.withStack(s.stack, s.sp - 1);
            int target = insns.indexOf(j.label);
            boolean jumpIfNull = j.getOpcode() == Opcodes.IFNULL;
            Boolean isNull = null;
            if (v.kind == V.NULL) isNull = true;
            else if (v.nonNull()) isNull = false;
            else if (v.kind == V.CAP) {
                if (s.capIs == State.NULL_CAP) isNull = true;
                else if (s.capIs != null || s.capNonNull) isNull = false;
            }
            if (isNull != null) return isNull == jumpIfNull ? go(target, base) : go(at + 1, base);
            State whenNull = base, whenNotNull = base;
            if (v.kind == V.CAP) {
                whenNull = new State(base.locals, base.stack, base.sp, State.NULL_CAP, base.capIsNot, false);
                whenNotNull = new State(base.locals, base.stack, base.sp, null, base.capIsNot, true);
            }
            return jumpIfNull ? fork(work, target, whenNull, whenNotNull, at) : fork(work, target, whenNotNull, whenNull, at);
        }
    }

    // ------------------------------------------------------------------------------------------------- final fields

    /**
     * Null when {@code d.name} is a final instance field that every constructor of {@code d} sets to a value that cannot be
     * null before {@code this} can reach any code (so no getCapability call, which needs a constructed provider, sees it
     * unset): d's superclass is java.lang.Object, every constructor is straight-line code without exception handlers that
     * does not delegate to another constructor of d, and until it stores the field it passes {@code this} (or a lambda or
     * LazyOptional capturing it) to nothing but Object's constructor, LazyOptional.of and stores into d's own fields; the
     * stored value is a new object, a lambda, LazyOptional.of(non-null) or LazyOptional.empty(). Otherwise the reason.
     */
    static String finalNonNull(ClassNode d, String name, String desc) {
        FieldNode field = null;
        if (d.fields != null) for (FieldNode f : d.fields) if (f.name.equals(name) && f.desc.equals(desc)) field = f;
        if (field == null) return "field not declared in the class";
        if ((field.access & Opcodes.ACC_FINAL) == 0 || (field.access & Opcodes.ACC_STATIC) != 0) return "field is not a final instance field";
        if (!"java/lang/Object".equals(d.superName)) return "superclass constructor runs first";
        int ctors = 0;
        for (MethodNode m : d.methods) {
            if (!m.name.equals("<init>")) continue;
            ctors++;
            String why = constructorSets(d, m, name, desc);
            if (why != null) return why;
        }
        return ctors == 0 ? "no constructor" : null;
    }

    private static final int C_OTHER = 0, C_THIS = 1, C_NONNULL = 2, C_TAINTED = 3, C_UNINIT = 4;

    private static String constructorSets(ClassNode d, MethodNode m, String name, String desc) {
        if (m.tryCatchBlocks != null && !m.tryCatchBlocks.isEmpty()) return "constructor with exception handlers";
        int[] locals = new int[Math.max(m.maxLocals, Type.getArgumentsAndReturnSizes(m.desc) >> 2) + 4];
        locals[0] = C_THIS;
        ArrayDeque<Integer> st = new ArrayDeque<>();   // top = first
        boolean set = false;
        for (AbstractInsnNode n : m.instructions) {
            int op = n.getOpcode();
            if (op < 0) continue;
            if (n instanceof JumpInsnNode || op == Opcodes.TABLESWITCH || op == Opcodes.LOOKUPSWITCH) return "constructor with branches";
            switch (op) {
                case Opcodes.ALOAD -> st.push(locals[((VarInsnNode) n).var]);
                case Opcodes.ILOAD, Opcodes.LLOAD, Opcodes.FLOAD, Opcodes.DLOAD, Opcodes.ACONST_NULL, Opcodes.ICONST_M1, Opcodes.ICONST_0,
                        Opcodes.ICONST_1, Opcodes.ICONST_2, Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5, Opcodes.LCONST_0,
                        Opcodes.LCONST_1, Opcodes.FCONST_0, Opcodes.FCONST_1, Opcodes.FCONST_2, Opcodes.DCONST_0, Opcodes.DCONST_1,
                        Opcodes.BIPUSH, Opcodes.SIPUSH -> st.push(C_OTHER);
                case Opcodes.LDC -> st.push(C_NONNULL);
                case Opcodes.ASTORE -> locals[((VarInsnNode) n).var] = pop(st);
                case Opcodes.ISTORE, Opcodes.LSTORE, Opcodes.FSTORE, Opcodes.DSTORE -> pop(st);
                case Opcodes.DUP -> { int v = pop(st); st.push(v); st.push(v); }
                case Opcodes.POP -> pop(st);
                case Opcodes.NEW -> st.push(C_UNINIT);
                case Opcodes.CHECKCAST -> { }
                case Opcodes.GETSTATIC -> st.push(C_OTHER);
                case Opcodes.GETFIELD -> { pop(st); st.push(C_OTHER); }
                case Opcodes.PUTSTATIC -> { if (escapes(pop(st)) && !set) return "constructor stores this in a static field"; }
                case Opcodes.PUTFIELD -> {
                    FieldInsnNode f = (FieldInsnNode) n;
                    int value = pop(st), recv = pop(st);
                    if (recv != C_THIS) { if (escapes(value) && !set) return "constructor stores this in another object"; break; }
                    if (f.owner.equals(d.name) && f.name.equals(name) && f.desc.equals(desc)) {
                        if (set) return "field set twice";
                        if (value != C_NONNULL && value != C_TAINTED) return "field set to a value that may be null";
                        set = true;
                    }
                }
                case Opcodes.INVOKEDYNAMIC -> {
                    InvokeDynamicInsnNode i = (InvokeDynamicInsnNode) n;
                    if (!i.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")) return "constructor invokedynamic";
                    boolean captures = false;
                    for (int k = Type.getArgumentTypes(i.desc).length; k > 0; k--) captures |= escapes(pop(st));
                    st.push(captures ? C_TAINTED : C_NONNULL);
                }
                case Opcodes.INVOKESPECIAL, Opcodes.INVOKESTATIC, Opcodes.INVOKEVIRTUAL, Opcodes.INVOKEINTERFACE -> {
                    MethodInsnNode mi = (MethodInsnNode) n;
                    Type[] args = Type.getArgumentTypes(mi.desc);
                    int[] vals = new int[args.length];
                    for (int k = args.length - 1; k >= 0; k--) vals[k] = pop(st);
                    int recv = op == Opcodes.INVOKESTATIC ? C_OTHER : pop(st);
                    String sig = mi.owner + "." + mi.name + mi.desc;
                    if (op == Opcodes.INVOKESPECIAL && mi.name.equals("<init>")) {
                        if (recv == C_THIS) {
                            if (mi.owner.equals(d.name)) return "constructor delegates to another constructor";
                            if (!sig.equals("java/lang/Object.<init>()V")) return "constructor calls " + mi.owner + ".<init>";
                            break;
                        }
                        for (int v : vals) if (escapes(v) && !set) return "constructor passes this to " + mi.owner + ".<init>";
                        // the NEW object below it is now constructed: replace the C_UNINIT copy left by DUP
                        if (!st.isEmpty() && st.peek() == C_UNINIT) { st.pop(); st.push(C_NONNULL); }
                        break;
                    }
                    if (sig.equals(LAZY_OPTIONAL + ".of(Lnet/minecraftforge/common/util/NonNullSupplier;)" + LAZY_OPTIONAL_DESC)) {
                        st.push(vals[0] == C_TAINTED ? C_TAINTED : C_NONNULL);   // of(null) is EMPTY: also non-null
                        break;
                    }
                    if (sig.equals(LAZY_OPTIONAL + ".empty()" + LAZY_OPTIONAL_DESC)) { st.push(C_NONNULL); break; }
                    if (!set) {
                        if (escapes(recv)) return "constructor calls a method on this before setting the field";
                        for (int v : vals) if (escapes(v)) return "constructor passes this to " + mi.owner + "." + mi.name;
                    }
                    if (Type.getReturnType(mi.desc).getSort() != Type.VOID) st.push(C_OTHER);
                }
                case Opcodes.RETURN -> {
                    if (!set) return "a constructor does not set the field";
                }
                default -> {
                    return "constructor instruction " + op;
                }
            }
        }
        return set ? null : "a constructor does not set the field";
    }

    private static boolean escapes(int v) {
        return v == C_THIS || v == C_TAINTED;
    }

    private static int pop(ArrayDeque<Integer> st) {
        return st.isEmpty() ? C_OTHER : st.pop();
    }

    // ---------------------------------------------------------------------------------------------- static initializers

    /** The class files the checks may need (the class itself and capability-token subclasses), by internal name. */
    public interface ClassFiles {
        ClassNode read(String internalName);

        /**
         * True when the class is known to be initialized already (run time: it is the class of a constructed mod object,
         * FML's @Mod instance of a loaded mod). Default: unknown.
         */
        default boolean initialized(String internalName) {
            return false;
        }
    }

    static final String RESOURCE_LOCATION = "net/minecraft/resources/ResourceLocation";
    static final String RL_FACTORY_DESC = "(Ljava/lang/String;)L" + RESOURCE_LOCATION + ";";
    /** Library collections whose no-argument (or initial-capacity) constructor only allocates. */
    static final Set<String> EMPTY_COLLECTIONS = Set.of("java/util/HashMap", "java/util/LinkedHashMap", "java/util/ArrayList",
            "java/util/HashSet", "java/util/LinkedHashSet", "java/util/IdentityHashMap", "java/util/WeakHashMap", "java/util/TreeMap",
            "java/util/concurrent/ConcurrentHashMap", "java/util/concurrent/CopyOnWriteArrayList",
            "it/unimi/dsi/fastutil/objects/Object2ObjectOpenHashMap", "it/unimi/dsi/fastutil/objects/Object2IntOpenHashMap",
            "it/unimi/dsi/fastutil/objects/ObjectOpenHashSet", "it/unimi/dsi/fastutil/objects/ObjectArrayList",
            "it/unimi/dsi/fastutil/objects/Reference2ObjectOpenHashMap", "it/unimi/dsi/fastutil/objects/ReferenceOpenHashSet",
            "it/unimi/dsi/fastutil/ints/Int2ObjectOpenHashMap", "it/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap");

    /**
     * Null when initializing {@code holder} runs nothing whose effect depends on, or could be seen at, the moment it runs,
     * otherwise the reason. The class's superclass is java.lang.Object (or it is an interface), and its static initializer
     * (if any) is straight-line code that only:
     * <ul>
     *   <li>creates Forge capability tokens (CapabilityToken subclasses whose constructor only calls CapabilityToken's empty
     *       one, without fields or static initializer) and asks CapabilityManager.get for them (the same Capability object for
     *       the same type name whenever asked);</li>
     *   <li>creates ResourceLocations from constant strings that vanilla's rules accept (namespace [a-z0-9_.-], path
     *       [a-z0-9_.-/]), directly or through a static factory {@code X.m(String)} whose body is exactly
     *       {@code return new ResourceLocation("<constant namespace>", arg);} on a class X that is initialized already (the
     *       holder itself, or a constructed @Mod class at run time) or whose own initializer passes this check (one level);</li>
     *   <li>creates an empty JDK or fastutil collection ({@link #EMPTY_COLLECTIONS}, no-argument or int-capacity constructor);</li>
     *   <li>loads constants, reads and writes the holder's own static fields.</li>
     * </ul>
     * Equal objects result whenever such an initializer runs, and nothing outside the holder changes, except that a
     * ResourceLocation constructor hook such as AllTheLeaks' string interning (a pool keyed by value) may pick a different,
     * equal String instance as its canonical one.
     */
    public static String pureStaticInit(ClassNode holder, ClassFiles files) {
        return pureStaticInit(holder, files, 0);
    }

    private static final Object S_OTHER = new Object(), S_TOKEN_NEW = new Object(), S_TOKEN = new Object(), S_RL_NEW = new Object(),
            S_COLL_NEW = new Object(), S_OBJ = new Object(), S_CAP = new Object();

    private static String pureStaticInit(ClassNode holder, ClassFiles files, int depth) {
        if (holder == null) return "class file not found";
        if (!"java/lang/Object".equals(holder.superName)) return "its superclass " + holder.superName + " would be initialized too";
        MethodNode clinit = null;
        for (MethodNode m : holder.methods) if (m.name.equals("<clinit>")) clinit = m;
        if (clinit == null) return null;
        if (clinit.tryCatchBlocks != null && !clinit.tryCatchBlocks.isEmpty()) return "static initializer has an exception handler";
        Set<String> tokensChecked = new HashSet<>();
        ArrayDeque<Object> st = new ArrayDeque<>();
        for (AbstractInsnNode n : clinit.instructions) {
            int op = n.getOpcode();
            if (op < 0) continue;
            if (n instanceof JumpInsnNode || op == Opcodes.TABLESWITCH || op == Opcodes.LOOKUPSWITCH) return "static initializer with branches";
            switch (op) {
                case Opcodes.ACONST_NULL, Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2, Opcodes.ICONST_3,
                        Opcodes.ICONST_4, Opcodes.ICONST_5, Opcodes.LCONST_0, Opcodes.LCONST_1, Opcodes.FCONST_0, Opcodes.FCONST_1,
                        Opcodes.FCONST_2, Opcodes.DCONST_0, Opcodes.DCONST_1, Opcodes.BIPUSH, Opcodes.SIPUSH -> st.push(S_OTHER);
                case Opcodes.RETURN -> { }
                case Opcodes.DUP -> { Object v = st.isEmpty() ? S_OTHER : st.peek(); st.push(v); }
                case Opcodes.POP -> spop(st);
                case Opcodes.LDC -> {
                    Object c = ((LdcInsnNode) n).cst;
                    if (c instanceof String s) st.push(s);
                    else if (c instanceof Number) st.push(S_OTHER);
                    else return "static initializer loads constant " + c;
                }
                case Opcodes.PUTSTATIC -> {
                    if (!((FieldInsnNode) n).owner.equals(holder.name)) return "static initializer writes " + ((FieldInsnNode) n).owner + "." + ((FieldInsnNode) n).name;
                    spop(st);
                }
                case Opcodes.GETSTATIC -> {
                    if (!((FieldInsnNode) n).owner.equals(holder.name)) return "static initializer reads " + ((FieldInsnNode) n).owner + "." + ((FieldInsnNode) n).name;
                    st.push(S_OTHER);
                }
                case Opcodes.NEW -> {
                    String t = ((TypeInsnNode) n).desc;
                    if (t.equals(RESOURCE_LOCATION)) { st.push(S_RL_NEW); break; }
                    if (EMPTY_COLLECTIONS.contains(t)) { st.push(S_COLL_NEW); break; }
                    String why = tokensChecked.contains(t) ? null : pureToken(t, files);
                    if (why != null) return "static initializer creates " + t + " (" + why + ")";
                    tokensChecked.add(t);
                    st.push(S_TOKEN_NEW);
                }
                case Opcodes.INVOKESPECIAL -> {
                    MethodInsnNode m = (MethodInsnNode) n;
                    if (!m.name.equals("<init>")) return "static initializer calls " + m.owner + "." + m.name;
                    if (m.owner.equals(RESOURCE_LOCATION)) {
                        String why;
                        if (m.desc.equals("(Ljava/lang/String;Ljava/lang/String;)V")) {
                            Object path = spop(st), ns = spop(st);
                            why = ns instanceof String a && path instanceof String b ? validLocation(a, b) : "a ResourceLocation from values that are not constants";
                        } else if (m.desc.equals("(Ljava/lang/String;)V")) {
                            Object s = spop(st);
                            why = s instanceof String a ? validLocation(a) : "a ResourceLocation from a value that is not a constant";
                        } else {
                            why = "ResourceLocation constructor " + m.desc;
                        }
                        if (why != null) return "static initializer builds " + why;
                        if (spop(st) != S_RL_NEW) return "unexpected ResourceLocation construction";
                        replaceTop(st, S_RL_NEW, S_OBJ);
                    } else if (EMPTY_COLLECTIONS.contains(m.owner)) {
                        if (m.desc.equals("(I)V")) spop(st);
                        else if (!m.desc.equals("()V")) return "static initializer calls " + m.owner + ".<init>" + m.desc;
                        if (spop(st) != S_COLL_NEW) return "unexpected collection construction";
                        replaceTop(st, S_COLL_NEW, S_OBJ);
                    } else {
                        if (!m.desc.equals("()V") || !tokensChecked.contains(m.owner)) return "static initializer calls " + m.owner + "." + m.name;
                        if (spop(st) != S_TOKEN_NEW) return "unexpected token construction";
                        replaceTop(st, S_TOKEN_NEW, S_TOKEN);
                    }
                }
                case Opcodes.INVOKESTATIC -> {
                    MethodInsnNode m = (MethodInsnNode) n;
                    if (m.owner.equals(MANAGER) && m.name.equals("get") && m.desc.equals("(L" + TOKEN + ";)" + CAPABILITY_DESC)) {
                        if (spop(st) != S_TOKEN) return "CapabilityManager.get with something other than a new token";
                        st.push(S_CAP);
                        break;
                    }
                    if (m.desc.equals(RL_FACTORY_DESC)) {
                        Object arg = spop(st);
                        if (!(arg instanceof String path)) return "static initializer calls " + m.owner + "." + m.name + " with a value that is not a constant";
                        String why = factoryLocation(m.owner, m.name, path, holder, files, depth);
                        if (why != null) return "static initializer calls " + m.owner + "." + m.name + ": " + why;
                        st.push(S_OBJ);
                        break;
                    }
                    return "static initializer calls " + m.owner + "." + m.name;
                }
                default -> {
                    return "static initializer instruction " + op;
                }
            }
        }
        return null;
    }

    private static Object spop(ArrayDeque<Object> st) {
        return st.isEmpty() ? S_OTHER : st.pop();
    }

    /** After a constructor call on a DUPed new object: the copy left on the stack is now the constructed object. */
    private static void replaceTop(ArrayDeque<Object> st, Object from, Object to) {
        if (!st.isEmpty() && st.peek() == from) {
            st.pop();
            st.push(to);
        }
    }

    /** {@code X.m(path)} where m is exactly {@code return new ResourceLocation("<namespace>", path);} and X is init-safe. */
    private static String factoryLocation(String owner, String name, String path, ClassNode holder, ClassFiles files, int depth) {
        ClassNode x = owner.equals(holder.name) ? holder : files.read(owner);
        if (x == null) return "class file not found";
        MethodNode f = null;
        for (MethodNode m : x.methods) if (m.name.equals(name) && m.desc.equals(RL_FACTORY_DESC)) f = m;
        if (f == null || (f.access & Opcodes.ACC_STATIC) == 0) return "not a static method of " + owner;
        if (f.tryCatchBlocks != null && !f.tryCatchBlocks.isEmpty()) return "has an exception handler";
        List<AbstractInsnNode> code = new ArrayList<>();
        for (AbstractInsnNode n : f.instructions) if (n.getOpcode() >= 0) code.add(n);
        if (code.size() != 6 || code.get(0).getOpcode() != Opcodes.NEW || !((TypeInsnNode) code.get(0)).desc.equals(RESOURCE_LOCATION)
                || code.get(1).getOpcode() != Opcodes.DUP || code.get(2).getOpcode() != Opcodes.LDC || !(((LdcInsnNode) code.get(2)).cst instanceof String ns)
                || code.get(3).getOpcode() != Opcodes.ALOAD || ((VarInsnNode) code.get(3)).var != 0
                || code.get(4).getOpcode() != Opcodes.INVOKESPECIAL || !((MethodInsnNode) code.get(4)).owner.equals(RESOURCE_LOCATION)
                || !((MethodInsnNode) code.get(4)).name.equals("<init>") || !((MethodInsnNode) code.get(4)).desc.equals("(Ljava/lang/String;Ljava/lang/String;)V")
                || code.get(5).getOpcode() != Opcodes.ARETURN)
            return "its body is not 'return new ResourceLocation(<constant>, arg)'";
        String bad = validLocation(ns, path);
        if (bad != null) return bad;
        if (owner.equals(holder.name) || files.initialized(owner)) return null;
        if (depth >= 1) return "class " + owner + " may not be initialized yet";
        String pure = pureStaticInit(x, files, depth + 1);
        return pure == null ? null : "class " + owner + " may not be initialized yet (" + pure + ")";
    }

    /** Null when vanilla's ResourceLocation(namespace, path) accepts both constants; the problem otherwise. */
    static String validLocation(String namespace, String path) {
        for (int i = 0; i < namespace.length(); i++) {
            char c = namespace.charAt(i);
            if (!(c == '_' || c == '-' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '.')) return "an invalid namespace '" + namespace + "'";
        }
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (!(c == '_' || c == '-' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '/' || c == '.')) return "an invalid path '" + path + "'";
        }
        return null;
    }

    /** ResourceLocation(String): vanilla's decompose on ':' (no colon = minecraft), then both parts as above. */
    static String validLocation(String location) {
        String ns = "minecraft", path = location;
        int i = location.indexOf(':');
        if (i >= 0) {
            path = location.substring(i + 1);
            if (i >= 1) ns = location.substring(0, i);
        }
        return validLocation(ns, path);
    }

    /** A CapabilityToken subclass whose construction runs nothing but CapabilityToken's (empty) constructor. */
    private static String pureToken(String internalName, ClassFiles files) {
        ClassNode t = files.read(internalName);
        if (t == null) return "class file not found";
        if (!TOKEN.equals(t.superName)) return "not a capability token";
        if (t.fields != null) for (FieldNode f : t.fields) if ((f.access & Opcodes.ACC_STATIC) == 0) return "token with fields";
        for (MethodNode m : t.methods) {
            if (m.name.equals("<clinit>")) return "token with a static initializer";
            if (!m.name.equals("<init>")) continue;
            if (!m.desc.equals("()V")) return "token constructor " + m.desc;
            List<Integer> ops = new ArrayList<>();
            for (AbstractInsnNode n : m.instructions) if (n.getOpcode() >= 0) ops.add(n.getOpcode());
            if (!ops.equals(List.of(Opcodes.ALOAD, Opcodes.INVOKESPECIAL, Opcodes.RETURN))) return "token constructor does more";
            for (AbstractInsnNode n : m.instructions) {
                if (n instanceof MethodInsnNode mi && !(mi.owner.equals(TOKEN) && mi.name.equals("<init>") && mi.desc.equals("()V")))
                    return "token constructor calls " + mi.owner + "." + mi.name;
                if (n instanceof VarInsnNode v && v.var != 0) return "token constructor reads a local";
            }
        }
        return null;
    }
}
