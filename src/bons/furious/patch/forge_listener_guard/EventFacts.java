package bons.furious.patch.forge_listener_guard;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * forge_listener_prologue_guard: the event facts an analysed prologue may read, for one posted event class, both as the
 * analyser's {@link PrologueAnalyser.Resolver} (what a field or getter in a listener's bytecode reads on that class) and
 * as the reader the dispatch uses (the same final fields, read once per test).
 *
 * Facts and their fields (all final; finality is checked on the running class, after access transformers):
 *   phase / side / type  TickEvent.phase / side / type (public final)
 *   level                TickEvent$LevelTickEvent.level (public final); level.isClientSide = Level.f_46443_ (public final)
 *   player               TickEvent$PlayerTickEvent.player (public final)
 *   entity               LivingEvent.livingEntity (private final), reached through getEntity()
 *   stage                RenderLevelStageEvent.stage (private final), reached through getStage() (client)
 * A getter call is accepted only when the method virtual dispatch selects on the posted class (and every bridge it
 * forwards to) is, in its class file, `ALOAD 0; GETFIELD <that final field>; ARETURN` (a bridge: `ALOAD 0; INVOKEVIRTUAL
 * or INVOKESPECIAL <getter>; ARETURN`), in a class the audit trail shows untouched by mixins and coremods.
 */
public final class EventFacts implements PrologueAnalyser.Resolver {
    static final String TICK = "net.minecraftforge.event.TickEvent", LEVEL_TICK = "net.minecraftforge.event.TickEvent$LevelTickEvent",
            PLAYER_TICK = "net.minecraftforge.event.TickEvent$PlayerTickEvent", LIVING = "net.minecraftforge.event.entity.living.LivingEvent",
            STAGE_EVENT = "net.minecraftforge.client.event.RenderLevelStageEvent", LEVEL = "net.minecraft.world.level.Level";
    /** Accepted constant classes: initialised whenever an event that holds one of their values exists. */
    static final java.util.Set<String> CONSTANT_CLASSES = java.util.Set.of("net/minecraftforge/event/TickEvent$Phase",
            "net/minecraftforge/event/TickEvent$Type", "net/minecraftforge/fml/LogicalSide", "net/minecraftforge/client/event/RenderLevelStageEvent$Stage");

    final Class<?> eventClass;
    private final ClassLoader loader;

    EventFacts(Class<?> eventClass) {
        this.eventClass = eventClass;
        this.loader = eventClass.getClassLoader();
    }

    private Class<?> loaded(String internal) {
        try {
            return Class.forName(internal.replace('/', '.'), false, loader);
        } catch (Throwable t) {
            return null;
        }
    }

    /** The fact a field (declaring class, name) of the posted event is, or 0. */
    static int factOfField(Field f) {
        if (f == null || Modifier.isStatic(f.getModifiers()) || !Modifier.isFinal(f.getModifiers())) return 0;
        String d = f.getDeclaringClass().getName(), n = f.getName();
        if (d.equals(TICK)) return switch (n) {
            case "phase" -> PrologueAnalyser.F_PHASE;
            case "side" -> PrologueAnalyser.F_SIDE;
            case "type" -> PrologueAnalyser.F_TYPE;
            default -> 0;
        };
        if (d.equals(LEVEL_TICK) && n.equals("level")) return PrologueAnalyser.F_LEVEL;
        if (d.equals(PLAYER_TICK) && n.equals("player")) return PrologueAnalyser.F_PLAYER;
        if (d.equals(LIVING) && n.equals("livingEntity")) return PrologueAnalyser.F_LIVING;
        if (d.equals(STAGE_EVENT) && n.equals("stage")) return PrologueAnalyser.F_STAGE;
        return 0;
    }

    private static Field field(Class<?> c, String name, String desc) {
        for (; c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equals(name) && org.objectweb.asm.Type.getDescriptor(f.getType()).equals(desc)) return f;
            }
        }
        return null;
    }

    /** True when the internal name is the posted class or one of its supertypes (so it is loaded; nothing else is looked up). */
    private boolean supertypeOfEvent(String internal) {
        return Profiles.isSubtype(eventClass, internal.replace('/', '.'));
    }

    @Override
    public int eventField(String owner, String name, String desc) {
        if (!supertypeOfEvent(owner)) return 0;
        Class<?> o = loaded(owner);
        if (o == null || !o.isAssignableFrom(eventClass)) return 0;
        Field f = field(o, name, desc);
        if (f == null || !Modifier.isPublic(f.getModifiers())) return 0;   // the public fields; private ones only via getters
        return factOfField(f);
    }

    @Override
    public int eventGetter(int opcode, String owner, String name, String desc) {
        if (opcode != Opcodes.INVOKEVIRTUAL || !desc.startsWith("()L") || !supertypeOfEvent(owner)) return 0;
        Class<?> o = loaded(owner);
        if (o == null || o.isInterface() || !o.isAssignableFrom(eventClass)) return 0;
        Field f = getterField(eventClass, name, desc, false, 0);
        return factOfField(f);
    }

    /**
     * The final field a getter returns: the method INVOKEVIRTUAL selects on `start` (special: the method declared in
     * start or its superclasses, as INVOKESPECIAL resolves), verified in its class file; null if not a plain getter.
     */
    static Field getterField(Class<?> start, String name, String desc, boolean special, int depth) {
        if (depth > 3) return null;
        Method m = null;
        for (Class<?> c = start; c != null && m == null; c = c.getSuperclass()) {
            for (Method x : c.getDeclaredMethods()) {
                if (x.getName().equals(name) && org.objectweb.asm.Type.getMethodDescriptor(x).equals(desc) && !Modifier.isStatic(x.getModifiers())
                        && !Modifier.isAbstract(x.getModifiers()) && (special || !Modifier.isPrivate(x.getModifiers()))) {
                    m = x;
                    break;
                }
            }
        }
        if (m == null || !AuditTrail.untransformed(m.getDeclaringClass().getName(), true)) return null;
        MethodNode node = ListenerTargets.methodNode(m);
        if (node == null || !node.tryCatchBlocks.isEmpty()) return null;
        AbstractInsnNode[] code = real(node);
        if (code.length == 3 && code[0].getOpcode() == Opcodes.ALOAD && ((org.objectweb.asm.tree.VarInsnNode) code[0]).var == 0
                && code[2].getOpcode() == Opcodes.ARETURN) {
            if (code[1] instanceof FieldInsnNode fi && fi.getOpcode() == Opcodes.GETFIELD) {
                Class<?> fo;
                try {
                    fo = Class.forName(fi.owner.replace('/', '.'), false, m.getDeclaringClass().getClassLoader());
                } catch (Throwable t) {
                    return null;
                }
                if (!fo.isAssignableFrom(m.getDeclaringClass())) return null;
                Field f = field(fo, fi.name, fi.desc);
                return f != null && Modifier.isFinal(f.getModifiers()) && !Modifier.isStatic(f.getModifiers()) ? f : null;
            }
            if (code[1] instanceof MethodInsnNode mi && mi.desc.startsWith("()L") && (mi.getOpcode() == Opcodes.INVOKEVIRTUAL || mi.getOpcode() == Opcodes.INVOKESPECIAL)) {
                if (mi.getOpcode() == Opcodes.INVOKEVIRTUAL) return getterField(start, mi.name, mi.desc, false, depth + 1);
                Class<?> so;
                try {
                    so = Class.forName(mi.owner.replace('/', '.'), false, m.getDeclaringClass().getClassLoader());
                } catch (Throwable t) {
                    return null;
                }
                if (!so.isAssignableFrom(m.getDeclaringClass())) return null;
                return getterField(so, mi.name, mi.desc, true, depth + 1);
            }
        }
        return null;
    }

    static AbstractInsnNode[] real(MethodNode m) {
        java.util.List<AbstractInsnNode> out = new java.util.ArrayList<>();
        for (AbstractInsnNode n : m.instructions) if (n.getOpcode() >= 0) out.add(n);
        return out.toArray(new AbstractInsnNode[0]);
    }

    @Override
    public boolean levelClientField(String owner, String name, String desc) {
        if (!desc.equals("Z") || !owner.equals("net/minecraft/world/level/Level")) return false;   // the static type javac writes
        Class<?> o = loaded(owner);
        Class<?> level = loaded("net/minecraft/world/level/Level");
        if (o == null || level == null || !level.isAssignableFrom(o)) return false;
        Field f = field(o, name, desc);
        return f != null && f.getDeclaringClass() == level && f.getName().equals("f_46443_") && Modifier.isFinal(f.getModifiers())
                && Modifier.isPublic(f.getModifiers()) && !Modifier.isStatic(f.getModifiers());
    }

    @Override
    public String constant(String owner, String name, String desc) {
        if (!CONSTANT_CLASSES.contains(owner) || !desc.equals("L" + owner + ";")) return null;
        Class<?> o = loaded(owner);
        if (o == null) return null;
        try {
            Field f = o.getDeclaredField(name);
            int mod = f.getModifiers();
            if (!Modifier.isStatic(mod) || !Modifier.isFinal(mod) || !Modifier.isPublic(mod) || f.getType() != o) return null;
            return owner + "." + name;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public int sideMethod(int opcode, String owner, String name, String desc) {
        if (opcode != Opcodes.INVOKEVIRTUAL || !owner.equals("net/minecraftforge/fml/LogicalSide") || !desc.equals("()Z")) return 0;
        // LogicalSide.isClient / isServer are fingerprinted by BusShape (fmlcore's class file) and must be untouched
        if (BusShape.check() != null || !AuditTrail.untransformed("net.minecraftforge.fml.LogicalSide", false)) return 0;
        return name.equals("isClient") ? 1 : name.equals("isServer") ? -1 : 0;
    }

    /**
     * True when the event that builds a table holds an instance of the constant's class (a phase, side, type or stage
     * value that is not null), so that class is initialised and reading the constant has no effect of its own.
     */
    static boolean constantClassInitialised(String id, Event sample) {
        String owner = id.substring(0, id.lastIndexOf('.')).replace('/', '.');
        Object[] held;
        if (sample instanceof TickEvent t) held = new Object[] {t.phase, t.side, t.type};
        else if (STAGE_EVENT.equals(sample.getClass().getName()) && StageHolder.STAGE != null) held = new Object[] {StageHolder.STAGE.get(sample)};   // by name: no client class is loaded on a server
        else return false;
        for (Object v : held) {
            if (v == null) continue;
            for (Class<?> c = v.getClass(); c != null; c = c.getSuperclass()) if (c.getName().equals(owner)) return true;
        }
        return false;
    }

    @Override
    public Boolean eventInstanceOf(String internalName) {
        return Profiles.isSubtype(eventClass, internalName.replace('/', '.'));
    }

    /** The value of a constant id ("internal/Owner.NAME"), read from its (initialised) class. */
    Object constantValue(String id) throws ReflectiveOperationException {
        int dot = id.lastIndexOf('.');
        Class<?> o = Class.forName(id.substring(0, dot).replace('/', '.'), false, loader);
        Field f = o.getDeclaredField(id.substring(dot + 1));
        return f.get(null);
    }

    // ---- the reader (dispatch side) ----

    /** LivingEvent.livingEntity and RenderLevelStageEvent.stage; resolved on first use (stage: client only). */
    private static final class LivingHolder {
        static final VarHandle ENTITY = handle(LIVING, "livingEntity");
    }

    private static final class StageHolder {
        static final VarHandle STAGE = handle(STAGE_EVENT, "stage");
    }

    private static VarHandle handle(String cls, String name) {
        try {
            Class<?> c = Class.forName(cls, false, EventFacts.class.getClassLoader());
            Field f = c.getDeclaredField(name);
            return MethodHandles.privateLookupIn(c, MethodHandles.lookup()).unreflectVarHandle(f);
        } catch (Throwable t) {
            return null;
        }
    }

    /** True when the reader can read this fact (the private fields' handles resolved). */
    /**
     * The RenderLevelStageEvent$Stage constants (public static final fields of that class), for the post signature; asked
     * only for a table of RenderLevelStageEvent, whose posts hold a Stage, so the class is initialised. Empty if unreadable
     * (GuardTable then guards no post of that table: a stage it cannot index is never skipped).
     */
    static Object[] stageConstants(Class<?> eventClass) {
        try {
            Class<?> st = Class.forName(STAGE_EVENT + "$Stage", false, eventClass.getClassLoader());
            java.util.List<Object> out = new java.util.ArrayList<>();
            for (Field f : st.getDeclaredFields()) {
                int m = f.getModifiers();
                if (Modifier.isStatic(m) && Modifier.isFinal(m) && Modifier.isPublic(m) && f.getType() == st) out.add(f.get(null));
            }
            return out.toArray();
        } catch (Throwable t) {
            return new Object[0];
        }
    }

    static boolean readable(int fact) {
        return switch (fact) {
            case PrologueAnalyser.F_LIVING -> LivingHolder.ENTITY != null;
            case PrologueAnalyser.F_STAGE -> StageHolder.STAGE != null;
            default -> true;
        };
    }

    /** The fact's value on an event of the table's class (the analyser only produced facts that class has). */
    static Object read(int fact, Event e) {
        switch (fact) {
            case PrologueAnalyser.F_PHASE: return ((TickEvent) e).phase;
            case PrologueAnalyser.F_SIDE: return ((TickEvent) e).side;
            case PrologueAnalyser.F_TYPE: return ((TickEvent) e).type;
            case PrologueAnalyser.F_LEVEL: return ((TickEvent.LevelTickEvent) e).level;
            case PrologueAnalyser.F_PLAYER: return ((TickEvent.PlayerTickEvent) e).player;
            case PrologueAnalyser.F_LIVING: return LivingHolder.ENTITY.get(e);
            case PrologueAnalyser.F_STAGE: return StageHolder.STAGE.get(e);
            default: throw new IllegalStateException("fact " + fact);
        }
    }
}
