package bons.furious.patch.geckolib_constant_resources;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

/**
 * Bons and Furious switch geckolib_constant_resource_reuse (GeckoLib 4.8.4, client). Helper of GeoModelConstantResourceMixin;
 * no GeckoLib or Minecraft code is carried.
 *
 * GeckoLib asks a mob's model for its model file every frame (GeoModel.getModelResource(animatable, renderer)), for its
 * texture at least once per frame (getTextureResource, again for glowing layers and Oculus's shadow pass), and for its
 * animation file when an animation queue is built (getAnimation -> getAnimationResource). Hundreds of model classes in this
 * pack (MCreator's GeckoLib template and others) answer those with {@code return new ResourceLocation("ns", "path");}: a new
 * location, path validation (with Oculus's per-character hook, see vanilla_rl_path_char_fast) and AllTheLeaks' string
 * interning and timer on every call, with the same value every time.
 *
 * What changes: for each concrete model class this reads, once, the bytecode of the method each of the three getters
 * dispatches to (the javac bridge, which casts to the model's animatable type and calls the typed getter, and that typed
 * getter) and accepts only this exact shape: bridge = load this, load the argument, checkcast T, invokevirtual the typed
 * getter, return; typed getter = new ResourceLocation, dup, two string constants, its (String, String) constructor, return.
 * It also requires that no mixin has merged anything into those classes. For an accepted getter the first call runs the
 * original and keeps its result; later calls return that same location when the argument is null or a T (an argument of
 * another type runs the original, so its ClassCastException is unchanged). Every other getter (a texture that depends on the
 * mob's variant, a field, anything else) runs the original on every call.
 * Exact variant: the same value every time (equal, with the same namespace and path strings, so every hash map, memoized
 * render type and texture lookup answers the same); one location object per model class and getter instead of a new one
 * per call, and the construction hooks (AllTheLeaks' interning and timer, path validation) run once instead of every call.
 *
 * Runtime flag: -Dbons_and_furious.geckolibConstantResourceReuse=false runs every getter. SHADOW MODE for rigs:
 * -Dbons_and_furious.geckolibConstantResourceReuse.shadow=true calls the original every time, returns its result and compares
 * it with the kept one (equal, and the same namespace and path string objects); SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for
 * the first 20.
 */
public final class ConstantResources {
    /** Runtime switch (the config switch acts when GeoModel is transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.geckolibConstantResourceReuse", "true"));
    /** Shadow mode (see the class comment). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.geckolibConstantResourceReuse.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Model classes analysed, and their getters accepted (statistics for probes and the rig). */
    public static final AtomicLong CLASSES = new AtomicLong(), ACCEPTED = new AtomicLong();

    public static final int MODEL = 0, TEXTURE = 1, ANIMATION = 2;
    static final String[] NAMES = {"getModelResource", "getTextureResource", "getAnimationResource"};
    static final String GEO_ANIMATABLE = "software/bernie/geckolib/animatable/GeoAnimatable";
    static final String RL = "net/minecraft/resources/ResourceLocation";
    static final String RL_DESC = "L" + RL + ";";

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    /** One getter of one model class: accepted (constant) or not, the animatable type its bridge casts to, the kept location. */
    public static final class Getter {
        public final boolean constant;
        public final Class<?> param;
        public final String why;
        volatile ResourceLocation value;

        Getter(boolean constant, Class<?> param, String why) {
            this.constant = constant;
            this.param = param;
            this.why = why;
        }
    }

    private static final ClassValue<Getter[]> GETTERS = new ClassValue<>() {
        @Override
        protected Getter[] computeValue(Class<?> model) {
            Getter[] out = new Getter[3];
            for (int i = 0; i < 3; i++) {
                try {
                    out[i] = analyse(model, i);
                } catch (Throwable t) {
                    out[i] = new Getter(false, null, "not analysed: " + t);
                }
                if (out[i].constant) ACCEPTED.incrementAndGet();
            }
            CLASSES.incrementAndGet();
            return out;
        }
    };

    private ConstantResources() {
    }

    /** In place of the 1-argument getter call inside GeoModel's 2-argument getModelResource / getTextureResource / getAnimation. */
    public static ResourceLocation get(Object model, Object animatable, int which, Operation<ResourceLocation> original) {
        if (enabled) {
            Getter g = GETTERS.get(model.getClass())[which];
            if (g.constant && (animatable == null || g.param.isInstance(animatable))) {
                ResourceLocation kept = g.value;
                if (kept != null) return SHADOW ? shadow(kept, model, animatable, original, which) : kept;
                ResourceLocation v = original.call(model, animatable);
                if (v != null) {
                    g.value = v;   // two threads may both build it once: equal values, either may be kept
                    if (!announced) {
                        announced = true;
                        LOGGER.info("Bons and Furious: geckolib_constant_resource_reuse: GeckoLib models with constant model / texture / animation files hand out one location each{}",
                                SHADOW ? " (SHADOW MODE: the original runs every time and is compared)" : "");
                    }
                }
                return v;
            }
        }
        return original.call(model, animatable);
    }

    /** The getter verdicts of a model class (for the harness and rig probes). */
    public static Getter[] getters(Class<?> modelClass) {
        return GETTERS.get(modelClass);
    }

    private static ResourceLocation shadow(ResourceLocation kept, Object model, Object animatable, Operation<ResourceLocation> original, int which) {
        ResourceLocation fresh = original.call(model, animatable);
        SHADOW_CHECKS.incrementAndGet();
        boolean same = fresh != null && fresh.equals(kept) && fresh.getNamespace() == kept.getNamespace() && fresh.getPath() == kept.getPath();
        if (!same && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
            LOGGER.warn("Bons and Furious: geckolib_constant_resource_reuse SHADOW MISMATCH {}: {}.{} answered {}, kept {}", SHADOW_MISMATCHES.get(),
                    model.getClass().getName(), NAMES[which], fresh, kept);
        }
        return fresh;
    }

    // ------------------------------------------------------------------------------------------------- the template proof

    static Getter analyse(Class<?> model, int which) throws Exception {
        String name = NAMES[which];
        Class<?> geo = Class.forName(GEO_ANIMATABLE.replace('/', '.'), false, ConstantResources.class.getClassLoader());
        Method m1 = dispatched(model, name, geo);
        if (m1 == null) return new Getter(false, null, "no implementation");
        MethodNode n1 = node(m1.getDeclaringClass(), name, "(L" + GEO_ANIMATABLE + ";)" + RL_DESC);
        if (n1 == null) return new Getter(false, null, "bytecode not found for " + m1);
        if (merged(m1.getDeclaringClass())) return new Getter(false, null, "a mixin changes " + m1.getDeclaringClass().getName());
        String[] bridge = bridge(n1);   // {cast type, call owner, call name} or null
        Method m2 = m1;
        MethodNode n2 = n1;
        Class<?> param = geo;
        if (bridge != null) {
            if (!bridge[2].equals(name)) return new Getter(false, null, "the bridge calls " + bridge[2]);
            param = Class.forName(bridge[0].replace('/', '.'), false, model.getClassLoader());
            Class<?> owner = Class.forName(bridge[1].replace('/', '.'), false, model.getClassLoader());
            if (!owner.isAssignableFrom(model)) return new Getter(false, null, "the bridge calls into " + owner.getName());
            m2 = dispatched(model, name, param);
            if (m2 == null) return new Getter(false, null, "no typed implementation");
            n2 = node(m2.getDeclaringClass(), name, "(L" + bridge[0] + ";)" + RL_DESC);
            if (n2 == null) return new Getter(false, null, "bytecode not found for " + m2);
            if (merged(m2.getDeclaringClass())) return new Getter(false, null, "a mixin changes " + m2.getDeclaringClass().getName());
        }
        int mods = m2.getModifiers();
        if (!Modifier.isPublic(mods) || Modifier.isStatic(mods) || Modifier.isSynchronized(mods) || Modifier.isNative(mods))
            return new Getter(false, null, "modifiers " + Modifier.toString(mods));
        if (!constant(n2)) return new Getter(false, null, "not a constant location");
        return new Getter(true, param, "constant");
    }

    /** The method an invokevirtual of name(param) selects for an instance of {@code type}: public, not abstract, returns ResourceLocation. */
    static Method dispatched(Class<?> type, String name, Class<?> param) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != 1 || m.getParameterTypes()[0] != param) continue;
                if (m.getReturnType() != ResourceLocation.class) continue;
                if (Modifier.isAbstract(m.getModifiers()) || !Modifier.isPublic(m.getModifiers())) return null;
                return m;
            }
        }
        return null;
    }

    /** True when any method of the class carries @MixinMerged (another mod's mixin, or ours, merged something into it). */
    static boolean merged(Class<?> c) {
        for (Method m : c.getDeclaredMethods()) if (m.getAnnotation(MixinMerged.class) != null) return true;
        return false;
    }

    /** The method's bytecode as the class file holds it (the class's own loader serves the jar entry). */
    static MethodNode node(Class<?> c, String name, String desc) throws Exception {
        String res = c.getName().replace('.', '/') + ".class";
        ClassLoader l = c.getClassLoader();
        byte[] b;
        try (InputStream in = l != null ? l.getResourceAsStream(res) : ClassLoader.getSystemResourceAsStream(res)) {
            if (in == null) return null;
            b = in.readAllBytes();
        }
        ClassNode node = new ClassNode();
        new ClassReader(b).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        for (MethodNode m : node.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
        return null;
    }

    static List<AbstractInsnNode> code(MethodNode m) {
        List<AbstractInsnNode> out = new ArrayList<>();
        for (AbstractInsnNode n : m.instructions) if (n.getOpcode() >= 0) out.add(n);
        return out;
    }

    /** aload 0, aload 1, checkcast T, invokevirtual Owner.name(T)ResourceLocation, areturn -> {T, Owner, name}; else null. */
    static String[] bridge(MethodNode m) {
        List<AbstractInsnNode> c = code(m);
        if (c.size() != 5 || !m.tryCatchBlocks.isEmpty()) return null;
        if (!(c.get(0) instanceof VarInsnNode a0) || a0.getOpcode() != Opcodes.ALOAD || a0.var != 0) return null;
        if (!(c.get(1) instanceof VarInsnNode a1) || a1.getOpcode() != Opcodes.ALOAD || a1.var != 1) return null;
        if (!(c.get(2) instanceof TypeInsnNode cast) || cast.getOpcode() != Opcodes.CHECKCAST) return null;
        if (!(c.get(3) instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKEVIRTUAL) return null;
        if (!call.desc.equals("(L" + cast.desc + ";)" + RL_DESC)) return null;
        if (c.get(4).getOpcode() != Opcodes.ARETURN) return null;
        return new String[] {cast.desc, call.owner, call.name};
    }

    /** new ResourceLocation, dup, ldc String, ldc String, invokespecial ResourceLocation.<init>(String, String), areturn. */
    static boolean constant(MethodNode m) {
        List<AbstractInsnNode> c = code(m);
        if (c.size() != 6 || !m.tryCatchBlocks.isEmpty()) return false;
        if (!(c.get(0) instanceof TypeInsnNode n) || n.getOpcode() != Opcodes.NEW || !n.desc.equals(RL)) return false;
        if (c.get(1).getOpcode() != Opcodes.DUP) return false;
        if (!(c.get(2) instanceof LdcInsnNode l1) || !(l1.cst instanceof String)) return false;
        if (!(c.get(3) instanceof LdcInsnNode l2) || !(l2.cst instanceof String)) return false;
        if (!(c.get(4) instanceof MethodInsnNode init) || init.getOpcode() != Opcodes.INVOKESPECIAL || !init.owner.equals(RL)
                || !init.name.equals("<init>") || !init.desc.equals("(Ljava/lang/String;Ljava/lang/String;)V")) return false;
        return c.get(5).getOpcode() == Opcodes.ARETURN;
    }
}
