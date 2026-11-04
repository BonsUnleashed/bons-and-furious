package bons.furious.patch.forge_listener_guard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * forge_listener_prologue_guard (Forge 47.4.28, eventbus 6.2.33; both sides): the one-time bytecode analysis of a
 * @SubscribeEvent method's opening instructions. Idea: Collections Of Optimizations (vanilla.guardedEvents), idea text
 * only; the analysis and every rule below are ours.
 *
 * The method is executed symbolically from its first instruction, both ways at every branch, over a deliberately small
 * set of instructions that read and never write: loads and stores of locals, DUP/POP, null and the int constants 0..5,
 * GETFIELD of a final field of the event that the {@link Resolver} names (TickEvent.phase/side/type,
 * LevelTickEvent.level, PlayerTickEvent.player) and of Level.isClientSide on that level, getters of the event whose
 * bytecode the resolver has verified to return a final field (LivingEvent.getEntity, RenderLevelStageEvent.getStage),
 * GETSTATIC of the enum/stage constants those facts are compared with (TickEvent$Phase, TickEvent$Type, LogicalSide,
 * RenderLevelStageEvent$Stage: classes that are initialised whenever such an event exists), LogicalSide.isClient /
 * isServer (bytecode verified), INSTANCEOF of the entity / player, and the branches IFNULL, IFNONNULL, IF_ACMPEQ,
 * IF_ACMPNE, IFEQ, IFNE, GOTO (forward only). A path that reaches the void RETURN through those instructions alone is an
 * early return: it has no effect but the reads. Every other instruction ends its path as "runs" (the listener is called).
 *
 * The result is a list of clauses, one per early-return path: the conditions (atoms) that path took. A listener may be
 * skipped for an event exactly when one clause holds on it: the path the method takes on that event state is then that
 * early-return path. Atoms are evaluated on the same final fields the method reads, so their values cannot change while
 * the bus runs earlier listeners. A read that would throw in the method (a null level before isClientSide, a null side
 * before isClient) makes the atom fail, so the listener is called and throws as before.
 *
 * Never guarded (no clause): synchronized, native or abstract methods, try/catch blocks over the analysed instructions
 * (an exception there would go elsewhere), backward jumps, more than 512 paths or 4096 analysed steps.
 */
public final class PrologueAnalyser {
    /** Facts: values read from the event, all final fields (see the class comment). */
    public static final int F_PHASE = 1, F_SIDE = 2, F_TYPE = 3, F_LEVEL = 4, F_STAGE = 5, F_LIVING = 6, F_PLAYER = 7;
    static final String[] FACT_NAMES = {"-", "phase", "side", "type", "level", "stage", "entity", "player"};
    /** Atom kinds. EQ: fact == constant (identity); EQNN: the same but a null fact fails (a call on it would throw);
     *  NULL: fact == null; LEVEL_CLIENT: level != null and level.isClientSide (a null level fails);
     *  INST: the subject fact is an instance of one of the named classes (null is no instance). */
    public static final int A_EQ = 1, A_EQNN = 2, A_NULL = 3, A_LEVEL_CLIENT = 4, A_INST = 5;
    static final int MAX_PATHS = 512, MAX_STEPS = 4096, MAX_CLAUSES = 16;

    /** What the analysis may ask about classes; in game: the loaded classes (GuardTable), in the harness: the same. */
    public interface Resolver {
        /** The fact a GETFIELD owner.name:desc reads on the posted event (event class `eventClass`), or 0. */
        int eventField(String owner, String name, String desc);

        /** The fact a call owner.name:desc with no arguments returns on the posted event (verified getter), or 0. */
        int eventGetter(int opcode, String owner, String name, String desc);

        /** True when GETFIELD owner.name:desc on the level fact reads Level.isClientSide (a final boolean). */
        boolean levelClientField(String owner, String name, String desc);

        /** The id ("internal/Owner.NAME") of an accepted GETSTATIC constant, or null. */
        String constant(String owner, String name, String desc);

        /** LogicalSide.isClient() = 1, LogicalSide.isServer() = -1 (both verified), anything else 0. */
        int sideMethod(int opcode, String owner, String name, String desc);

        /** Whether the posted event class is an instance of the internal name (it is known), null if it cannot be told. */
        Boolean eventInstanceOf(String internalName);
    }

    /** One condition of a clause. For INST, names = the instance-of class names (dotted binary names). */
    public static final class Atom {
        public final int kind, fact;
        public final String constant;
        public final String[] names;
        public final boolean expect;

        Atom(int kind, int fact, String constant, String[] names, boolean expect) {
            this.kind = kind;
            this.fact = fact;
            this.constant = constant;
            this.names = names;
            this.expect = expect;
        }

        Atom with(boolean e) {
            return new Atom(kind, fact, constant, names, e);
        }

        String key() {
            return kind + "|" + fact + "|" + constant + "|" + (names == null ? "" : String.join(",", names));
        }

        @Override
        public String toString() {
            String f = FACT_NAMES[fact];
            String c = constant == null ? "" : constant.substring(constant.lastIndexOf('.') + 1);
            return switch (kind) {
                case A_EQ -> f + (expect ? " == " : " != ") + c;
                case A_EQNN -> f + (expect ? " == " : " != ") + c + " (non-null)";
                case A_NULL -> f + (expect ? " == null" : " != null");
                case A_LEVEL_CLIENT -> (expect ? "" : "!") + "level.isClientSide";
                case A_INST -> f + (expect ? " instanceof " : " instanceof none of ") + Arrays.toString(names);
                default -> "?";
            };
        }
    }

    /** The conditions of one early-return path (all must hold). Empty = the method always returns at once. */
    public static final class Clause {
        public final Atom[] atoms;

        Clause(Atom[] atoms) {
            this.atoms = atoms;
        }

        @Override
        public String toString() {
            return atoms.length == 0 ? "always" : String.join(" && ", Arrays.stream(atoms).map(Atom::toString).toList());
        }
    }

    /** Result: the clauses (empty = no guard), the instruction indices where a path stopped as "runs", and why not. */
    public static final class Result {
        public final List<Clause> clauses;
        public final Set<Integer> runLeaves;
        public final Set<Integer> returnLeaves;
        public final String rejected;
        public final int paths;
        public final String form;

        Result(List<Clause> clauses, Set<Integer> runLeaves, Set<Integer> returnLeaves, String rejected, int paths, String form) {
            this.clauses = clauses;
            this.runLeaves = runLeaves;
            this.returnLeaves = returnLeaves;
            this.rejected = rejected;
            this.paths = paths;
            this.form = form;
        }

        public boolean guarded() {
            return rejected == null && !clauses.isEmpty();
        }
    }

    private static final int K_UNKNOWN = 0, K_EVENT = 1, K_THIS = 2, K_NULL = 3, K_INT = 4, K_FACT = 5, K_CONST = 6, K_COND = 7;

    /** A symbolic value: an event fact, a constant, a boolean condition (cond with trueMeans = expect when true), ... */
    private static final class Val {
        final int kind, fact, intValue;
        final String constant;
        final Atom cond;
        final boolean trueMeans;

        Val(int kind, int fact, int intValue, String constant, Atom cond, boolean trueMeans) {
            this.kind = kind;
            this.fact = fact;
            this.intValue = intValue;
            this.constant = constant;
            this.cond = cond;
            this.trueMeans = trueMeans;
        }
    }

    private static final Val UNKNOWN = new Val(K_UNKNOWN, 0, 0, null, null, false), EVENT = new Val(K_EVENT, 0, 0, null, null, false),
            THIS = new Val(K_THIS, 0, 0, null, null, false), NULL = new Val(K_NULL, 0, 0, null, null, false);

    private static Val intVal(int v) {
        return new Val(K_INT, 0, v, null, null, false);
    }

    private static Val fact(int f) {
        return new Val(K_FACT, f, 0, null, null, false);
    }

    private static Val constant(String id) {
        return new Val(K_CONST, 0, 0, id, null, false);
    }

    private static Val cond(Atom a, boolean trueMeans) {
        return new Val(K_COND, 0, 0, null, a, trueMeans);
    }

    private static final class State {
        int pc, steps;
        final ArrayList<Val> stack;
        final Val[] locals;
        final ArrayList<Atom> atoms;
        final ArrayList<Integer> visited;

        State(int pc, ArrayList<Val> stack, Val[] locals, ArrayList<Atom> atoms, ArrayList<Integer> visited, int steps) {
            this.pc = pc;
            this.stack = stack;
            this.locals = locals;
            this.atoms = atoms;
            this.visited = visited;
            this.steps = steps;
        }

        State fork(int newPc, Atom add) {
            ArrayList<Atom> a = new ArrayList<>(atoms);
            if (add != null) a.add(add);
            return new State(newPc, new ArrayList<>(stack), locals.clone(), a, new ArrayList<>(visited), steps);
        }
    }

    private PrologueAnalyser() {}

    /**
     * Analyses one listener method. isStatic: local 0 is the event, else local 0 is the listener object and local 1 the
     * event. Pure: reads only the method node and asks the resolver.
     */
    public static Result analyse(MethodNode m, boolean isStatic, Resolver r) {
        if ((m.access & (Opcodes.ACC_SYNCHRONIZED | Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT)) != 0)
            return reject("synchronized, native or abstract");
        if (!m.desc.endsWith(")V") || Type.getArgumentTypes(m.desc).length != 1) return reject("not a one-argument void method");
        AbstractInsnNode[] code = m.instructions.toArray();
        if (code.length == 0) return reject("no code");
        java.util.Map<LabelNode, Integer> labelAt = new java.util.HashMap<>();
        for (int i = 0; i < code.length; i++) if (code[i] instanceof LabelNode l) labelAt.put(l, i);
        Val[] locals = new Val[Math.max(m.maxLocals, 2) + 1];
        Arrays.fill(locals, UNKNOWN);
        if (isStatic) {
            locals[0] = EVENT;
        } else {
            locals[0] = THIS;
            locals[1] = EVENT;
        }
        ArrayList<State> work = new ArrayList<>();
        work.add(new State(0, new ArrayList<>(), locals, new ArrayList<>(), new ArrayList<>(), 0));
        List<List<Atom>> returns = new ArrayList<>();
        Set<Integer> runLeaves = new TreeSet<>(), returnLeaves = new TreeSet<>();
        Set<Integer> returnPathInsns = new HashSet<>();
        Set<String> runReasons = new LinkedHashSet<>();
        int paths = 0, steps = 0;
        while (!work.isEmpty()) {
            State s = work.remove(work.size() - 1);
            if (++paths > MAX_PATHS) return reject("more than " + MAX_PATHS + " paths");
            pathLoop:
            while (true) {
                if (s.pc >= code.length) { runLeaves.add(code.length - 1); runReasons.add("falls off the code"); break; }
                if (++steps > MAX_STEPS) return reject("more than " + MAX_STEPS + " analysed steps");
                AbstractInsnNode n = code[s.pc];
                int op = n.getOpcode();
                if (op < 0) { s.pc++; continue; }
                s.visited.add(s.pc);
                switch (op) {
                    case Opcodes.RETURN -> {
                        returns.add(s.atoms);
                        returnLeaves.add(s.pc);
                        returnPathInsns.addAll(s.visited);
                        break pathLoop;
                    }
                    case Opcodes.ALOAD -> {
                        int v = ((VarInsnNode) n).var;
                        s.stack.add(v < s.locals.length ? s.locals[v] : UNKNOWN);
                    }
                    case Opcodes.ASTORE -> {
                        int v = ((VarInsnNode) n).var;
                        Val x = pop(s);
                        if (x == null || v >= s.locals.length) { run(runLeaves, runReasons, s, "store outside the frame"); break pathLoop; }
                        s.locals[v] = x;
                    }
                    case Opcodes.DUP -> {
                        if (s.stack.isEmpty()) { run(runLeaves, runReasons, s, "stack underflow"); break pathLoop; }
                        s.stack.add(s.stack.get(s.stack.size() - 1));
                    }
                    case Opcodes.POP -> {
                        if (pop(s) == null) { run(runLeaves, runReasons, s, "stack underflow"); break pathLoop; }
                    }
                    case Opcodes.ACONST_NULL -> s.stack.add(NULL);
                    case Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2, Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5 ->
                            s.stack.add(intVal(op - Opcodes.ICONST_0));
                    case Opcodes.GETFIELD -> {
                        FieldInsnNode f = (FieldInsnNode) n;
                        Val base = pop(s);
                        if (base == null) { run(runLeaves, runReasons, s, "stack underflow"); break pathLoop; }
                        if (base.kind == K_EVENT) {
                            int fact = r.eventField(f.owner, f.name, f.desc);
                            if (fact == 0) { run(runLeaves, runReasons, s, "reads event field " + f.name); break pathLoop; }
                            s.stack.add(fact(fact));
                        } else if (base.kind == K_FACT && base.fact == F_LEVEL && r.levelClientField(f.owner, f.name, f.desc)) {
                            s.stack.add(cond(new Atom(A_LEVEL_CLIENT, F_LEVEL, null, null, true), true));
                        } else {
                            run(runLeaves, runReasons, s, "reads field " + f.owner.substring(f.owner.lastIndexOf('/') + 1) + "." + f.name);
                            break pathLoop;
                        }
                    }
                    case Opcodes.GETSTATIC -> {
                        FieldInsnNode f = (FieldInsnNode) n;
                        String id = r.constant(f.owner, f.name, f.desc);
                        if (id == null) { run(runLeaves, runReasons, s, "reads static " + f.owner.substring(f.owner.lastIndexOf('/') + 1) + "." + f.name); break pathLoop; }
                        s.stack.add(constant(id));
                    }
                    case Opcodes.INVOKEVIRTUAL, Opcodes.INVOKEINTERFACE -> {
                        MethodInsnNode mi = (MethodInsnNode) n;
                        if (Type.getArgumentTypes(mi.desc).length != 0 || s.stack.isEmpty()) {
                            run(runLeaves, runReasons, s, "calls " + mi.name);
                            break pathLoop;
                        }
                        Val recv = pop(s);
                        if (recv.kind == K_EVENT) {
                            int fact = r.eventGetter(op, mi.owner, mi.name, mi.desc);
                            if (fact == 0) { run(runLeaves, runReasons, s, "calls event method " + mi.name); break pathLoop; }
                            s.stack.add(fact(fact));
                        } else if (recv.kind == K_FACT && recv.fact == F_SIDE) {
                            int which = r.sideMethod(op, mi.owner, mi.name, mi.desc);
                            if (which == 0) { run(runLeaves, runReasons, s, "calls LogicalSide." + mi.name); break pathLoop; }
                            // isClient() = (side == CLIENT), isServer() = !isClient(); either throws on a null side (EQNN fails then)
                            s.stack.add(cond(new Atom(A_EQNN, F_SIDE, "net/minecraftforge/fml/LogicalSide.CLIENT", null, true), which > 0));
                        } else {
                            run(runLeaves, runReasons, s, "calls " + mi.name);
                            break pathLoop;
                        }
                    }
                    case Opcodes.INSTANCEOF -> {
                        String t = ((TypeInsnNode) n).desc;
                        Val v = pop(s);
                        if (v == null) { run(runLeaves, runReasons, s, "stack underflow"); break pathLoop; }
                        if (t.startsWith("[")) { run(runLeaves, runReasons, s, "array instanceof"); break pathLoop; }
                        if (v.kind == K_NULL) s.stack.add(intVal(0));
                        else if (v.kind == K_EVENT) {
                            Boolean b = r.eventInstanceOf(t);
                            if (b == null) { run(runLeaves, runReasons, s, "event instanceof (unknown)"); break pathLoop; }
                            s.stack.add(intVal(b ? 1 : 0));
                        } else if (v.kind == K_FACT && (v.fact == F_LIVING || v.fact == F_PLAYER)) {
                            s.stack.add(cond(new Atom(A_INST, v.fact, null, new String[] {t.replace('/', '.')}, true), true));
                        } else {
                            run(runLeaves, runReasons, s, "instanceof on another value");
                            break pathLoop;
                        }
                    }
                    case Opcodes.IFNULL, Opcodes.IFNONNULL -> {
                        Val v = pop(s);
                        int target = labelAt.get(((JumpInsnNode) n).label);
                        if (v == null || target <= s.pc) { run(runLeaves, runReasons, s, target <= s.pc ? "backward jump" : "stack underflow"); break pathLoop; }
                        boolean jumpIfNull = op == Opcodes.IFNULL;
                        if (v.kind == K_EVENT || v.kind == K_NULL) {   // the event is never null; null is null
                            boolean isNull = v.kind == K_NULL;
                            s.pc = (isNull == jumpIfNull) ? target : s.pc + 1;
                            continue;
                        }
                        if (v.kind != K_FACT) { run(runLeaves, runReasons, s, "null test on another value"); break pathLoop; }
                        Atom a = new Atom(A_NULL, v.fact, null, null, true);
                        work.add(s.fork(target, a.with(jumpIfNull)));
                        s.atoms.add(a.with(!jumpIfNull));
                        s.pc++;
                        continue;
                    }
                    case Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE -> {
                        Val b = pop(s), a = pop(s);
                        int target = labelAt.get(((JumpInsnNode) n).label);
                        if (a == null || b == null || target <= s.pc) { run(runLeaves, runReasons, s, target <= s.pc ? "backward jump" : "stack underflow"); break pathLoop; }
                        boolean jumpIfEqual = op == Opcodes.IF_ACMPEQ;
                        Boolean folded = foldEquals(a, b);
                        if (folded != null) {
                            s.pc = (folded == jumpIfEqual) ? target : s.pc + 1;
                            continue;
                        }
                        Atom atom = equalsAtom(a, b);
                        if (atom == null) { run(runLeaves, runReasons, s, "reference compare of other values"); break pathLoop; }
                        work.add(s.fork(target, atom.with(jumpIfEqual)));
                        s.atoms.add(atom.with(!jumpIfEqual));
                        s.pc++;
                        continue;
                    }
                    case Opcodes.IFEQ, Opcodes.IFNE -> {
                        Val v = pop(s);
                        int target = labelAt.get(((JumpInsnNode) n).label);
                        if (v == null || target <= s.pc) { run(runLeaves, runReasons, s, target <= s.pc ? "backward jump" : "stack underflow"); break pathLoop; }
                        boolean jumpIfZero = op == Opcodes.IFEQ;
                        if (v.kind == K_INT) {
                            s.pc = ((v.intValue == 0) == jumpIfZero) ? target : s.pc + 1;
                            continue;
                        }
                        if (v.kind != K_COND) { run(runLeaves, runReasons, s, "branch on another value"); break pathLoop; }
                        // the value is true (non-zero) when the atom holds with expect = trueMeans
                        Atom whenTrue = v.cond.with(v.trueMeans), whenFalse = v.cond.with(!v.trueMeans);
                        work.add(s.fork(target, jumpIfZero ? whenFalse : whenTrue));
                        s.atoms.add(jumpIfZero ? whenTrue : whenFalse);
                        s.pc++;
                        continue;
                    }
                    case Opcodes.GOTO -> {
                        int target = labelAt.get(((JumpInsnNode) n).label);
                        if (target <= s.pc) { run(runLeaves, runReasons, s, "backward jump"); break pathLoop; }
                        s.pc = target;
                        continue;
                    }
                    default -> {
                        run(runLeaves, runReasons, s, "runs " + opName(op));
                        break pathLoop;
                    }
                }
                s.pc++;
            }
        }
        // a try/catch block over any instruction an early-return path executes: an exception there would go elsewhere
        for (TryCatchBlockNode t : m.tryCatchBlocks) {
            int from = labelAt.get(t.start), to = labelAt.get(t.end);
            for (int i : returnPathInsns) if (i >= from && i < to) return reject("try/catch over the opening instructions");
        }
        List<Clause> clauses = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (List<Atom> path : returns) {
            Clause c = normalise(path);
            if (c == null) continue;
            String k = c.toString();
            if (seen.add(k)) clauses.add(c);
            if (clauses.size() >= MAX_CLAUSES) break;
        }
        String form = form(clauses);
        if (clauses.isEmpty()) return new Result(List.of(), runLeaves, returnLeaves,
                returns.isEmpty() ? "no early return (" + String.join("; ", runReasons.stream().limit(3).toList()) + ")" : "early returns infeasible", paths, form);
        return new Result(clauses, runLeaves, returnLeaves, null, paths, form);
    }

    private static Val pop(State s) {
        return s.stack.isEmpty() ? null : s.stack.remove(s.stack.size() - 1);
    }

    private static void run(Set<Integer> leaves, Set<String> reasons, State s, String why) {
        leaves.add(s.pc);
        reasons.add(why);
    }

    private static Result reject(String why) {
        return new Result(List.of(), Set.of(), Set.of(), why, 0, "-");
    }

    private static Boolean foldEquals(Val a, Val b) {
        if (a.kind == K_CONST && b.kind == K_CONST) return a.constant.equals(b.constant);
        if (a.kind == K_NULL && b.kind == K_NULL) return true;
        if ((a.kind == K_NULL && (b.kind == K_CONST || b.kind == K_EVENT)) || (b.kind == K_NULL && (a.kind == K_CONST || a.kind == K_EVENT))) return false;
        if (a.kind == K_EVENT && b.kind == K_EVENT) return true;
        return null;
    }

    private static Atom equalsAtom(Val a, Val b) {
        if (a.kind == K_FACT && b.kind == K_CONST) return new Atom(A_EQ, a.fact, b.constant, null, true);
        if (b.kind == K_FACT && a.kind == K_CONST) return new Atom(A_EQ, b.fact, a.constant, null, true);
        if (a.kind == K_FACT && b.kind == K_NULL) return new Atom(A_NULL, a.fact, null, null, true);
        if (b.kind == K_FACT && a.kind == K_NULL) return new Atom(A_NULL, b.fact, null, null, true);
        return null;
    }

    /**
     * One path's conditions as a clause: duplicates dropped, the negative instanceof tests of a subject merged into one
     * "none of" atom (instanceof has no effect and cannot throw, so their order does not matter), contradictions (the path
     * cannot be taken) give null.
     */
    static Clause normalise(List<Atom> path) {
        List<Atom> out = new ArrayList<>();
        java.util.Map<String, Boolean> seen = new java.util.HashMap<>();
        java.util.Map<Integer, String> eqTrue = new java.util.HashMap<>();
        java.util.Map<Integer, Boolean> isNull = new java.util.HashMap<>();
        java.util.Map<Integer, Set<String>> noneOf = new java.util.LinkedHashMap<>();
        java.util.Map<Integer, Set<String>> someOf = new java.util.HashMap<>();
        for (Atom a : path) {
            if (a.kind == A_INST) {
                if (a.expect) {
                    someOf.computeIfAbsent(a.fact, k -> new HashSet<>()).add(a.names[0]);
                    String k = a.key();
                    Boolean prev = seen.put(k, true);
                    if (prev != null && !prev) return null;
                    if (prev == null) out.add(a);
                } else {
                    noneOf.computeIfAbsent(a.fact, k -> new LinkedHashSet<>()).addAll(Arrays.asList(a.names));
                }
                continue;
            }
            String k = a.key();
            Boolean prev = seen.put(k, a.expect);
            if (prev != null) {
                if (prev != a.expect) return null;    // the same test both ways: infeasible
                continue;
            }
            if ((a.kind == A_EQ || a.kind == A_EQNN) && a.expect) {
                String c = eqTrue.put(a.fact, a.constant);
                if (c != null && !c.equals(a.constant)) return null;   // one value cannot equal two constants
            }
            if (a.kind == A_NULL) isNull.put(a.fact, a.expect);
            out.add(a);
        }
        for (java.util.Map.Entry<Integer, Boolean> e : isNull.entrySet()) {
            if (!e.getValue()) continue;
            if (eqTrue.containsKey(e.getKey()) || someOf.containsKey(e.getKey())) return null;   // null equals no constant, is no instance
        }
        for (java.util.Map.Entry<Integer, Set<String>> e : noneOf.entrySet()) {
            Set<String> pos = someOf.get(e.getKey());
            if (pos != null) for (String p : pos) if (e.getValue().contains(p)) return null;
            out.add(new Atom(A_INST, e.getKey(), null, e.getValue().toArray(new String[0]), false));
        }
        // cheap tests first (all atoms are pure; order does not change the result)
        out.sort((x, y) -> Integer.compare(x.kind == A_INST ? 1 : 0, y.kind == A_INST ? 1 : 0));
        return new Clause(out.toArray(new Atom[0]));
    }

    /** A short name of the guard's form for the proof table. */
    static String form(List<Clause> clauses) {
        if (clauses.isEmpty()) return "-";
        Set<String> parts = new TreeSet<>();
        for (Clause c : clauses) {
            if (c.atoms.length == 0) parts.add("empty");
            for (Atom a : c.atoms) {
                parts.add(switch (a.kind) {
                    case A_EQ, A_EQNN -> FACT_NAMES[a.fact];
                    case A_NULL -> FACT_NAMES[a.fact] + "-null";
                    case A_LEVEL_CLIENT -> "isClientSide";
                    case A_INST -> FACT_NAMES[a.fact] + (a.expect ? "-instanceof" : "-instanceof-none" + (a.names.length > 1 ? "-chain" : ""));
                    default -> "?";
                });
            }
        }
        return String.join("+", parts);
    }

    private static String opName(int op) {
        for (java.lang.reflect.Field f : Opcodes.class.getFields()) {
            try {
                if (f.getType() == int.class && f.getInt(null) == op && !f.getName().startsWith("ACC_") && !f.getName().startsWith("V")
                        && !f.getName().startsWith("T_") && !f.getName().startsWith("H_") && !f.getName().startsWith("F_")
                        && !f.getName().startsWith("ASM") && !f.getName().equals("SOURCE_DEPRECATED") && !f.getName().equals("SOURCE_MASK"))
                    return f.getName();
            } catch (IllegalAccessException ignored) {
                // public constants
            }
        }
        return "opcode " + op;
    }
}
