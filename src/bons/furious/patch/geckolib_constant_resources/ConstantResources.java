package bons.furious.patch.geckolib_constant_resources;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

/**
 * Bons and Furious switches geckolib_constant_resource_reuse (since 1.0.45, extended in 1.0.49) and
 * geckolib_texture_template_memo (since 1.0.49); GeckoLib 4.8.4, client. Helper of GeoModelConstantResourceMixin and
 * GeoModelTextureTemplateMixin; no GeckoLib or Minecraft code is carried.
 *
 * GeckoLib asks a mob's model for its model file every frame (GeoModel.getModelResource(animatable, renderer)), for its
 * texture at least once per frame (getTextureResource, again for glowing layers and Oculus's shadow pass), and for its
 * animation file when an animation queue is built (getAnimation -> getAnimationResource). Hundreds of model classes answer
 * those with a location made of two fixed strings ({@code return new ResourceLocation("ns", "path");}, shape A, MCreator's
 * template and others; or {@code return ResourceLocation.fromNamespaceAndPath("ns", "path");}, shape B, the Forge 47.3+
 * factory that Iron's Spellbooks and its add-ons use), and MCreator's entity models answer the texture with
 * {@code new ResourceLocation("ns", "textures/entities/" + entity.getTexture() + ".png")} (shape T). Every call builds a new
 * location: path validation (with Oculus's per-character hook), AllTheLeaks' string interning and timer, and for shape T a
 * string concatenation too, with the same value every time (for T: every time the texture string is the same).
 *
 * geckolib_constant_resource_reuse. For each concrete model class this reads, once, the bytecode of the method each of the
 * three getters dispatches to (the javac bridge, which casts to the model's animatable type and calls the typed getter, and
 * that typed getter) and accepts only these exact shapes: bridge = load this, load the argument, checkcast T, invokevirtual
 * the typed getter, return; typed getter = shape A (new ResourceLocation, dup, two string constants, its (String, String)
 * constructor, return) or, since 1.0.49, shape B (two string constants, invokestatic ResourceLocation.fromNamespaceAndPath,
 * return), shape B only while fromNamespaceAndPath is Forge's {@code return new ResourceLocation(ns, path);} (the switch's
 * guard pins that body) and no mixin has replaced it (FNAP_OK). It also requires that no mixin has merged anything into the
 * model classes. For an accepted getter the first call runs the original and keeps its result; later calls return that same
 * location when the argument is null or a T (an argument of another type runs the original, so its ClassCastException is
 * unchanged). Every other getter runs the original on every call.
 * Exact variant: the same value every time (equal, with the same namespace and path strings, so every hash map, memoized
 * render type and texture lookup answers the same); one location object per model class and getter instead of a new one
 * per call, and the construction hooks (AllTheLeaks' interning and timer, path validation) run once instead of every call.
 *
 * geckolib_texture_template_memo. For a texture getter of shape T (new ResourceLocation, dup, a string constant, load the
 * argument, invokevirtual X.getTexture(), one invokedynamic string concatenation with exactly one argument and constant text
 * around it, the (String, String) constructor, return; behind the same bridge, no mixin merged into the model classes),
 * where the getTexture() the argument's own class dispatches to is the plain synched-data read
 * {@code return (String) this.entityData.get(ACCESSOR);} (its bytecode is read once per animatable class; no mixin merged
 * into that class), the switch reads the texture string itself (the same read the original makes first), and hands out the
 * location kept for an equal string; for a string seen for the first time the original runs and its result is kept when it
 * is exactly the location of that string (equal namespace, path = text before + string + text after), at most 64 strings per
 * model class. A null texture string, an animatable of another type, a getTexture that is not that read, and any exception
 * run the original.
 * Exact variant: the same value as the original for the same texture string (equal location, the namespace is the same
 * string constant); one location per model class and texture string instead of a new one per call, and the construction
 * hooks run once per string. A first-time string reads getTexture() twice (ours and the original's), a pure read.
 *
 * Runtime flags: -Dbons_and_furious.geckolibConstantResourceReuse=false runs every constant getter,
 * -Dbons_and_furious.geckolibTextureTemplateMemo=false every texture template. SHADOW MODE for rigs:
 * -Dbons_and_furious.geckolibConstantResourceReuse.shadow=true / -Dbons_and_furious.geckolibTextureTemplateMemo.shadow=true
 * call the original every time, return its result and compare it with the kept one (equal, the same namespace string; for
 * constant getters also the same path string); SHADOW_CHECKS / SHADOW_MISMATCHES and TEXTURE_SHADOW_CHECKS /
 * TEXTURE_SHADOW_MISMATCHES, WARN for the first 20.
 */
public final class ConstantResources {
    /** Runtime switch of geckolib_constant_resource_reuse (the config switch acts when GeoModel is transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.geckolibConstantResourceReuse", "true"));
    /** Shadow mode (see the class comment). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.geckolibConstantResourceReuse.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Runtime switch of geckolib_texture_template_memo (since 1.0.49). */
    public static volatile boolean textureEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.geckolibTextureTemplateMemo", "true"));
    /** Shadow mode of geckolib_texture_template_memo (since 1.0.49). */
    public static final boolean TEXTURE_SHADOW = Boolean.getBoolean("bons_and_furious.geckolibTextureTemplateMemo.shadow");
    public static final AtomicLong TEXTURE_SHADOW_CHECKS = new AtomicLong(), TEXTURE_SHADOW_MISMATCHES = new AtomicLong();
    /** Model classes analysed, getters accepted, of them shape B, and texture templates accepted (statistics for probes and the rig). */
    public static final AtomicLong CLASSES = new AtomicLong(), ACCEPTED = new AtomicLong(), ACCEPTED_B = new AtomicLong(), TEMPLATES = new AtomicLong();
    /** At most this many texture strings are kept per model class (geckolib_texture_template_memo). */
    public static final int TEXTURES_PER_MODEL = 64;

    public static final int MODEL = 0, TEXTURE = 1, ANIMATION = 2;
    static final String[] NAMES = {"getModelResource", "getTextureResource", "getAnimationResource"};
    static final String GEO_ANIMATABLE = "software/bernie/geckolib/core/animatable/GeoAnimatable";
    static final String RL = "net/minecraft/resources/ResourceLocation";
    static final String RL_DESC = "L" + RL + ";";
    static final String SS_DESC = "(Ljava/lang/String;Ljava/lang/String;)";
    static final String CONCAT_FACTORY = "java/lang/invoke/StringConcatFactory";
    static final String SYNCHED = "net/minecraft/network/syncher/SynchedEntityData";

    /**
     * Since 1.0.49: shape B is accepted only while ResourceLocation.fromNamespaceAndPath(String, String) is a static method
     * returning a ResourceLocation that no mixin has replaced (no @MixinMerged on it). Its body, Forge's
     * {@code return new ResourceLocation(ns, path);} (47.3.22 to 47.4.28), is pinned by the switch's guard.
     */
    public static final boolean FNAP_OK = fnapOk();

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced, textureAnnounced;

    /** One getter of one model class: accepted (constant) or not, the animatable type its bridge casts to, the kept location. */
    public static final class Getter {
        public final boolean constant;
        public final Class<?> param;
        public final String why;
        /** Since 1.0.49: the typed getter's shape ("A", "B", "T" for a texture template, or null). */
        public final String shape;
        /** Since 1.0.49: the texture template (geckolib_texture_template_memo), or null. */
        public final Template template;
        volatile ResourceLocation value;

        Getter(boolean constant, Class<?> param, String why) {
            this(constant, param, why, constant ? "A" : null, null);
        }

        Getter(boolean constant, Class<?> param, String why, String shape, Template template) {
            this.constant = constant;
            this.param = param;
            this.why = why;
            this.shape = shape;
            this.template = template;
        }
    }

    /** Since 1.0.49: an MCreator texture template "ns", prefix + getTexture() + suffix, and its kept locations. */
    public static final class Template {
        public final String namespace, prefix, suffix;
        /** The class whose getTexture() the typed getter calls (the bridge's type or a supertype of it). */
        public final Class<?> owner;
        /** owner.getTexture() as (Object) -> String, a virtual call (each argument's own override runs). */
        final MethodHandle getTexture;
        final ConcurrentHashMap<String, ResourceLocation> kept = new ConcurrentHashMap<>();
        /**
         * Misses after all TEXTURES_PER_MODEL places were taken; past that many again the class is "spent": its calls go straight
         * to the original (one field read more), so a model class with more texture names than places never pays the lookup
         * for long. Racy counting and a plain flag are fine: they only decide when to stop looking (a thread that has not seen
         * the flag yet just looks a few more times).
         */
        int fullMisses;
        boolean spent;

        Template(String namespace, String prefix, String suffix, Class<?> owner, MethodHandle getTexture) {
            this.namespace = namespace;
            this.prefix = prefix;
            this.suffix = suffix;
            this.owner = owner;
            this.getTexture = getTexture;
        }

        /** The number of texture strings kept (for the harness). */
        public int keptCount() {
            return this.kept.size();
        }

        /** Whether a location is kept for this texture string (for the harness). */
        public boolean keeps(String texture) {
            return texture != null && this.kept.containsKey(texture);
        }

        /** Whether this model class stopped looking (more texture names than places; for the harness). */
        public boolean spent() {
            return this.spent;
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
                if (out[i].constant) {
                    ACCEPTED.incrementAndGet();
                    if ("B".equals(out[i].shape)) ACCEPTED_B.incrementAndGet();
                }
                if (out[i].template != null) TEMPLATES.incrementAndGet();
            }
            CLASSES.incrementAndGet();
            return out;
        }
    };

    /**
     * Since 1.0.49: per animatable class, whether the getTexture() an invokevirtual selects for it is the plain synched-data
     * read (its own bytecode, no mixin merged into its class). Asked only for arguments of a template's bridge type.
     */
    private static final ClassValue<Boolean> PLAIN_TEXTURE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return plainTextureRead(type);
            } catch (Throwable t) {
                return Boolean.FALSE;
            }
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

    /**
     * Since 1.0.49 (geckolib_texture_template_memo): in place of the 1-argument texture getter call inside GeoModel's
     * getTextureResource(animatable, renderer).
     */
    public static ResourceLocation texture(Object model, Object animatable, Operation<ResourceLocation> original) {
        if (textureEnabled && animatable != null) {
            Getter g = GETTERS.get(model.getClass())[TEXTURE];
            Template t = g.template;
            if (t != null && !t.spent && g.param.isInstance(animatable) && PLAIN_TEXTURE.get(animatable.getClass())) {
                String s;
                try {
                    s = (String) t.getTexture.invokeExact(animatable);
                } catch (Throwable e) {
                    return original.call(model, animatable);   // the original makes the same read first and fails the same way
                }
                if (s != null) {
                    ResourceLocation kept = t.kept.get(s);
                    if (kept != null) return TEXTURE_SHADOW ? textureShadow(kept, model, animatable, original, s) : kept;
                }
                ResourceLocation v = original.call(model, animatable);
                if (s != null && t.kept.size() >= TEXTURES_PER_MODEL) {
                    if (++t.fullMisses > TEXTURES_PER_MODEL) t.spent = true;
                } else if (s != null && v != null && t.namespace.equals(v.m_135827_()) && v.m_135815_().equals(t.prefix + s + t.suffix)) {
                    t.kept.putIfAbsent(s, v);   // two threads may both build it once: equal values, either may be kept
                    if (!textureAnnounced) {
                        textureAnnounced = true;
                        LOGGER.info("Bons and Furious: geckolib_texture_template_memo: MCreator GeckoLib textures hand out one location per texture name{}",
                                TEXTURE_SHADOW ? " (SHADOW MODE: the original runs every time and is compared)" : "");
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

    /** Since 1.0.49: whether an animatable class's dispatched getTexture() is the plain synched-data read (for the harness). */
    public static boolean plainTexture(Class<?> animatableClass) {
        return PLAIN_TEXTURE.get(animatableClass);
    }

    private static ResourceLocation shadow(ResourceLocation kept, Object model, Object animatable, Operation<ResourceLocation> original, int which) {
        ResourceLocation fresh = original.call(model, animatable);
        SHADOW_CHECKS.incrementAndGet();
        boolean same = fresh != null && fresh.equals(kept) && fresh.m_135827_() == kept.m_135827_() && fresh.m_135815_() == kept.m_135815_();
        if (!same && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
            LOGGER.warn("Bons and Furious: geckolib_constant_resource_reuse SHADOW MISMATCH {}: {}.{} answered {}, kept {}", SHADOW_MISMATCHES.get(),
                    model.getClass().getName(), NAMES[which], fresh, kept);
        }
        return fresh;
    }

    private static ResourceLocation textureShadow(ResourceLocation kept, Object model, Object animatable, Operation<ResourceLocation> original, String s) {
        ResourceLocation fresh = original.call(model, animatable);
        TEXTURE_SHADOW_CHECKS.incrementAndGet();
        boolean same = fresh != null && fresh.equals(kept) && fresh.m_135827_() == kept.m_135827_();
        if (!same && TEXTURE_SHADOW_MISMATCHES.incrementAndGet() <= 20) {
            LOGGER.warn("Bons and Furious: geckolib_texture_template_memo SHADOW MISMATCH {}: {}.getTextureResource answered {}, kept {} for texture {}",
                    TEXTURE_SHADOW_MISMATCHES.get(), model.getClass().getName(), fresh, kept, s);
        }
        return fresh;
    }

    // ------------------------------------------------------------------------------------------------- the template proof

    static boolean fnapOk() {
        try {
            Method m = ResourceLocation.class.getMethod("fromNamespaceAndPath", String.class, String.class);
            return Modifier.isStatic(m.getModifiers()) && m.getReturnType() == ResourceLocation.class && m.getAnnotation(MixinMerged.class) == null;
        } catch (Throwable t) {
            return false;
        }
    }

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
        if (constant(n2)) return new Getter(true, param, "constant", "A", null);
        if (constantB(n2)) {
            if (!FNAP_OK) return new Getter(false, null, "ResourceLocation.fromNamespaceAndPath is absent or replaced by a mixin");
            return new Getter(true, param, "constant (fromNamespaceAndPath)", "B", null);
        }
        if (which == TEXTURE && param != geo) {
            Template t = template(n2, param, model.getClassLoader());
            if (t != null) return new Getter(false, param, "texture template", "T", t);
        }
        return new Getter(false, null, "not a constant location");
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
        ClassNode node = classNode(c);
        if (node == null) return null;
        for (MethodNode m : node.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
        return null;
    }

    static ClassNode classNode(Class<?> c) throws Exception {
        String res = c.getName().replace('.', '/') + ".class";
        ClassLoader l = c.getClassLoader();
        byte[] b;
        try (InputStream in = l != null ? l.getResourceAsStream(res) : ClassLoader.getSystemResourceAsStream(res)) {
            if (in == null) return null;
            b = in.readAllBytes();
        }
        ClassNode node = new ClassNode();
        new ClassReader(b).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
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

    /** Shape A: new ResourceLocation, dup, ldc String, ldc String, invokespecial ResourceLocation.<init>(String, String), areturn. */
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

    /** Since 1.0.49, shape B: ldc String, ldc String, invokestatic ResourceLocation.fromNamespaceAndPath(String, String), areturn. */
    static boolean constantB(MethodNode m) {
        List<AbstractInsnNode> c = code(m);
        if (c.size() != 4 || !m.tryCatchBlocks.isEmpty()) return false;
        if (!(c.get(0) instanceof LdcInsnNode l1) || !(l1.cst instanceof String)) return false;
        if (!(c.get(1) instanceof LdcInsnNode l2) || !(l2.cst instanceof String)) return false;
        if (!(c.get(2) instanceof MethodInsnNode f) || f.getOpcode() != Opcodes.INVOKESTATIC || f.itf || !f.owner.equals(RL)
                || !f.name.equals("fromNamespaceAndPath") || !f.desc.equals(SS_DESC + RL_DESC)) return false;
        return c.get(3).getOpcode() == Opcodes.ARETURN;
    }

    /**
     * Since 1.0.49, shape T: new ResourceLocation, dup, ldc String ns, aload 1, invokevirtual X.getTexture()String,
     * invokedynamic StringConcatFactory.makeConcatWithConstants (String)String with a recipe of constant text around exactly
     * one argument, invokespecial ResourceLocation.<init>(String, String), areturn; X a supertype of (or) the bridge's type
     * declaring a public getTexture(). Null for anything else.
     */
    static Template template(MethodNode m, Class<?> param, ClassLoader loader) throws Exception {
        List<AbstractInsnNode> c = code(m);
        if (c.size() != 8 || !m.tryCatchBlocks.isEmpty()) return null;
        if (!(c.get(0) instanceof TypeInsnNode n) || n.getOpcode() != Opcodes.NEW || !n.desc.equals(RL)) return null;
        if (c.get(1).getOpcode() != Opcodes.DUP) return null;
        if (!(c.get(2) instanceof LdcInsnNode l1) || !(l1.cst instanceof String ns)) return null;
        if (!(c.get(3) instanceof VarInsnNode a1) || a1.getOpcode() != Opcodes.ALOAD || a1.var != 1) return null;
        if (!(c.get(4) instanceof MethodInsnNode get) || get.getOpcode() != Opcodes.INVOKEVIRTUAL || get.itf
                || !get.name.equals("getTexture") || !get.desc.equals("()Ljava/lang/String;")) return null;
        if (!(c.get(5) instanceof InvokeDynamicInsnNode indy) || !indy.name.equals("makeConcatWithConstants")
                || !indy.desc.equals("(Ljava/lang/String;)Ljava/lang/String;")) return null;
        Handle bsm = indy.bsm;
        if (bsm.getTag() != Opcodes.H_INVOKESTATIC || !bsm.getOwner().equals(CONCAT_FACTORY) || !bsm.getName().equals("makeConcatWithConstants")
                || indy.bsmArgs.length != 1 || !(indy.bsmArgs[0] instanceof String recipe)) return null;
        int arg = recipe.indexOf('\u0001');
        if (arg < 0 || recipe.indexOf('\u0001', arg + 1) >= 0 || recipe.indexOf('\u0002') >= 0) return null;
        if (!(c.get(6) instanceof MethodInsnNode init) || init.getOpcode() != Opcodes.INVOKESPECIAL || !init.owner.equals(RL)
                || !init.name.equals("<init>") || !init.desc.equals("(Ljava/lang/String;Ljava/lang/String;)V")) return null;
        if (c.get(7).getOpcode() != Opcodes.ARETURN) return null;
        Class<?> owner = Class.forName(get.owner.replace('/', '.'), false, loader);
        if (!owner.isAssignableFrom(param) || owner.isInterface()) return null;
        Method gt;
        try {
            gt = owner.getMethod("getTexture");
        } catch (NoSuchMethodException e) {
            return null;
        }
        if (gt.getReturnType() != String.class || Modifier.isStatic(gt.getModifiers())) return null;
        MethodHandle h = MethodHandles.publicLookup().findVirtual(owner, "getTexture", MethodType.methodType(String.class))
                .asType(MethodType.methodType(String.class, Object.class));
        return new Template(ns, recipe.substring(0, arg), recipe.substring(arg + 1), owner, h);
    }

    /**
     * Since 1.0.49: the getTexture() an invokevirtual selects for {@code type} (the nearest declaration, walking up) is
     * aload 0, getfield Entity.entityData (f_19804_), getstatic of an EntityDataAccessor, invokevirtual
     * SynchedEntityData.get (m_135370_), checkcast String, areturn, in a class no mixin merged anything into.
     */
    static boolean plainTextureRead(Class<?> type) throws Exception {
        for (Class<?> k = type; k != null; k = k.getSuperclass()) {
            Method declared;
            try {
                declared = k.getDeclaredMethod("getTexture");
            } catch (NoSuchMethodException e) {
                continue;
            }
            int mods = declared.getModifiers();
            if (declared.getReturnType() != String.class || Modifier.isPrivate(mods)) continue;   // a private method overrides nothing
            if (Modifier.isStatic(mods) || Modifier.isAbstract(mods) || Modifier.isSynchronized(mods) || Modifier.isNative(mods)) return false;
            if (merged(k)) return false;
            MethodNode m = node(k, "getTexture", "()Ljava/lang/String;");
            if (m == null) return false;
            List<AbstractInsnNode> c = code(m);
            if (c.size() != 6 || !m.tryCatchBlocks.isEmpty()) return false;
            if (!(c.get(0) instanceof VarInsnNode a0) || a0.getOpcode() != Opcodes.ALOAD || a0.var != 0) return false;
            if (!(c.get(1) instanceof FieldInsnNode data) || data.getOpcode() != Opcodes.GETFIELD || !data.name.equals("f_19804_")
                    || !data.desc.equals("L" + SYNCHED + ";")) return false;
            if (!(c.get(2) instanceof FieldInsnNode acc) || acc.getOpcode() != Opcodes.GETSTATIC
                    || !acc.desc.equals("Lnet/minecraft/network/syncher/EntityDataAccessor;")) return false;
            if (!(c.get(3) instanceof MethodInsnNode get) || get.getOpcode() != Opcodes.INVOKEVIRTUAL || !get.owner.equals(SYNCHED)
                    || !get.name.equals("m_135370_") || !get.desc.equals("(Lnet/minecraft/network/syncher/EntityDataAccessor;)Ljava/lang/Object;")) return false;
            if (!(c.get(4) instanceof TypeInsnNode cast) || cast.getOpcode() != Opcodes.CHECKCAST || !cast.desc.equals("java/lang/String")) return false;
            if (c.get(5).getOpcode() != Opcodes.ARETURN) return false;
            Field f = null;
            for (Class<?> o = Class.forName(acc.owner.replace('/', '.'), false, k.getClassLoader()); o != null && f == null; o = o.getSuperclass()) {
                try {
                    f = o.getDeclaredField(acc.name);
                } catch (NoSuchFieldException ignored) {
                }
            }
            return f != null && Modifier.isStatic(f.getModifiers()) && Modifier.isFinal(f.getModifiers());
        }
        return false;
    }
}
