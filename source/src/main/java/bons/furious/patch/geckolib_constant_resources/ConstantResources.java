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
 * geckolib_texture_template_memo (since 1.0.49); GeckoLib 4.9.3 for NeoForge 1.21.1, client. Helper of
 * GeoModelConstantResourceMixin and GeoModelTextureTemplateMixin; no GeckoLib or Minecraft code is carried.
 *
 * GeckoLib asks a mob's model for its model file every frame (GeoModel.getModelResource(animatable, renderer)), for its
 * texture at least once per frame (getTextureResource, again for glowing layers and a shader pack's shadow pass), and for its
 * animation file when an animation queue is built (getAnimation -> getAnimationResource). On Minecraft 1.21.1 the
 * ResourceLocation constructor is private, so hundreds of model classes answer those with a factory over fixed strings:
 * {@code return ResourceLocation.fromNamespaceAndPath("ns", "path");} (shape B, hand-written models) or MCreator's
 * {@code return ResourceLocation.parse("ns:path");} (shape P); and MCreator's entity models answer the texture with
 * {@code ResourceLocation.parse("ns:textures/entities/" + entity.getTexture() + ".png")} (shape TP; others write
 * {@code ResourceLocation.fromNamespaceAndPath("ns", "textures/entities/" + entity.getTexture() + ".png")}, shape TB).
 * Every call builds a new location: the split at ':' for P and TP, path validation, construction hooks of other mods, and
 * for the templates a string concatenation too, with the same value every time (for TP / TB: every time the texture string is
 * the same). Shape A ({@code new ResourceLocation("ns", "path")}, the 1.20.1 form) is still recognised but cannot be compiled
 * against 1.21.1.
 *
 * geckolib_constant_resource_reuse. For each concrete model class this reads, once, the bytecode of the method each of the
 * three getters dispatches to (the javac bridge, which casts to the model's animatable type and calls the typed getter, and
 * that typed getter) and accepts only these exact shapes: bridge = load this, load the argument, checkcast T, invokevirtual
 * the typed getter, return; typed getter = shape B (two string constants, invokestatic ResourceLocation.fromNamespaceAndPath,
 * return), shape P (one string constant, invokestatic ResourceLocation.parse, return) or shape A. B only while
 * fromNamespaceAndPath, and P only while parse and bySeparator, are static methods returning a ResourceLocation that no mixin
 * has replaced (FNAP_OK, PARSE_OK; the switch's guard pins their bodies). It also requires that no mixin has merged anything
 * into the model classes. For an accepted getter the first call runs the original and keeps its result; later calls return
 * that same location when the argument is null or a T (an argument of another type runs the original, so its
 * ClassCastException is unchanged). A getter whose original throws (an invalid constant) keeps nothing and throws every time.
 * Every other getter runs the original on every call.
 * Exact variant: the same value every time (equal, so every hash map, memoized render type and texture lookup answers the
 * same; for A and B also the same namespace and path string objects); one location object per model class and getter instead
 * of a new one per call, and the construction work (the split, path validation, other mods' construction hooks) runs once
 * instead of every call.
 *
 * geckolib_texture_template_memo. For a texture getter of shape TP (load the argument, invokevirtual X.getTexture(), one
 * invokedynamic string concatenation with exactly one argument and constant text around it whose leading text holds the
 * namespace and its ':' separator, invokestatic ResourceLocation.parse, return) or TB (a string constant, then the same
 * concatenation, invokestatic ResourceLocation.fromNamespaceAndPath, return), behind the same bridge, no mixin merged into
 * the model classes, while the factory it calls is the one PARSE_OK / FNAP_OK vouches for, and where the getTexture() the
 * argument's own class dispatches to is the plain synched-data read {@code return (String) this.entityData.get(ACCESSOR);}
 * (its bytecode is read once per animatable class; no mixin merged into that class), the switch reads the texture string
 * itself (the same read the original makes first), and hands out the location kept for an equal string; for a string seen
 * for the first time the original runs and its result is kept when it is exactly the location of that string (equal
 * namespace, path = text before + string + text after), at most 64 strings per model class. A null texture string, an
 * animatable of another type, a getTexture that is not that read, and any exception run the original.
 * Exact variant: the same value as the original for the same texture string (equal location); one location per model class
 * and texture string instead of a new one per call, and the construction work runs once per string. A first-time string
 * reads getTexture() twice (ours and the original's), a pure read.
 *
 * Runtime flags: -Dbons_and_furious.geckolibConstantResourceReuse=false runs every constant getter,
 * -Dbons_and_furious.geckolibTextureTemplateMemo=false every texture template. SHADOW MODE for rigs:
 * -Dbons_and_furious.geckolibConstantResourceReuse.shadow=true / -Dbons_and_furious.geckolibTextureTemplateMemo.shadow=true
 * call the original every time, return its result and compare it with the kept one (equal; for A, B and TB also the same
 * namespace string object, for A and B the same path string object); SHADOW_CHECKS / SHADOW_MISMATCHES and
 * TEXTURE_SHADOW_CHECKS / TEXTURE_SHADOW_MISMATCHES, WARN for the first 20.
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
    /** Model classes analysed, getters accepted, of them shape B and shape P, and texture templates accepted (statistics). */
    public static final AtomicLong CLASSES = new AtomicLong(), ACCEPTED = new AtomicLong(), ACCEPTED_B = new AtomicLong(),
            ACCEPTED_P = new AtomicLong(), TEMPLATES = new AtomicLong();
    /** At most this many texture strings are kept per model class (geckolib_texture_template_memo). */
    public static final int TEXTURES_PER_MODEL = 64;

    public static final int MODEL = 0, TEXTURE = 1, ANIMATION = 2;
    static final String[] NAMES = {"getModelResource", "getTextureResource", "getAnimationResource"};
    static final String GEO_ANIMATABLE = "software/bernie/geckolib/animatable/GeoAnimatable";
    static final String RL = "net/minecraft/resources/ResourceLocation";
    static final String RL_DESC = "L" + RL + ";";
    static final String SS_DESC = "(Ljava/lang/String;Ljava/lang/String;)";
    static final String S_DESC = "(Ljava/lang/String;)";
    static final String CONCAT_FACTORY = "java/lang/invoke/StringConcatFactory";
    static final String SYNCHED = "net/minecraft/network/syncher/SynchedEntityData";

    /**
     * Shape B and TB are accepted only while ResourceLocation.fromNamespaceAndPath(String, String) is a static method
     * returning a ResourceLocation that no mixin has replaced (no @MixinMerged on it). Its 1.21.1 body,
     * {@code return createUntrusted(ns, path);} (the validated private constructor), is pinned by the switches' guards.
     */
    public static final boolean FNAP_OK = factoryOk("fromNamespaceAndPath", String.class, String.class);
    /**
     * Shape P and TP are accepted only while ResourceLocation.parse(String) and bySeparator(String, char) are static methods
     * returning a ResourceLocation that no mixin has replaced. Their 1.21.1 bodies (parse = bySeparator(s, ':'); the split at
     * the first ':', then createUntrusted, or withDefaultNamespace without a ':') are pinned by the switches' guards.
     */
    public static final boolean PARSE_OK = factoryOk("parse", String.class) && factoryOk("bySeparator", String.class, char.class);

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced, textureAnnounced;

    /** One getter of one model class: accepted (constant) or not, the animatable type its bridge casts to, the kept location. */
    public static final class Getter {
        public final boolean constant;
        public final Class<?> param;
        public final String why;
        /** The typed getter's shape ("A", "B", "P", "TP" / "TB" for a texture template, or null). */
        public final String shape;
        /** The texture template (geckolib_texture_template_memo), or null. */
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

    /** A texture template "ns", prefix + getTexture() + suffix, and its kept locations. */
    public static final class Template {
        public final String namespace, prefix, suffix;
        /** True for shape TP (parse: the namespace and path are substrings of one concatenation, new strings each call). */
        public final boolean parsed;
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

        Template(String namespace, String prefix, String suffix, boolean parsed, Class<?> owner, MethodHandle getTexture) {
            this.namespace = namespace;
            this.prefix = prefix;
            this.suffix = suffix;
            this.parsed = parsed;
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
                    if ("P".equals(out[i].shape)) ACCEPTED_P.incrementAndGet();
                }
                if (out[i].template != null) TEMPLATES.incrementAndGet();
            }
            CLASSES.incrementAndGet();
            return out;
        }
    };

    /**
     * Per animatable class, whether the getTexture() an invokevirtual selects for it is the plain synched-data read (its own
     * bytecode, no mixin merged into its class). Asked only for arguments of a template's bridge type.
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
                if (kept != null) return SHADOW ? shadow(g, kept, model, animatable, original, which) : kept;
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

    /** geckolib_texture_template_memo: in place of the 1-argument texture getter call inside GeoModel's getTextureResource(animatable, renderer). */
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
                    if (kept != null) return TEXTURE_SHADOW ? textureShadow(t, kept, model, animatable, original, s) : kept;
                }
                ResourceLocation v = original.call(model, animatable);
                if (s != null && t.kept.size() >= TEXTURES_PER_MODEL) {
                    if (++t.fullMisses > TEXTURES_PER_MODEL) t.spent = true;
                } else if (s != null && v != null && t.namespace.equals(v.getNamespace()) && v.getPath().equals(t.prefix + s + t.suffix)) {
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

    /** Whether an animatable class's dispatched getTexture() is the plain synched-data read (for the harness). */
    public static boolean plainTexture(Class<?> animatableClass) {
        return PLAIN_TEXTURE.get(animatableClass);
    }

    private static ResourceLocation shadow(Getter g, ResourceLocation kept, Object model, Object animatable, Operation<ResourceLocation> original, int which) {
        ResourceLocation fresh = original.call(model, animatable);
        SHADOW_CHECKS.incrementAndGet();
        boolean strings = "P".equals(g.shape)
                ? fresh != null && fresh.getNamespace().equals(kept.getNamespace()) && fresh.getPath().equals(kept.getPath())
                : fresh != null && fresh.getNamespace() == kept.getNamespace() && fresh.getPath() == kept.getPath();
        boolean same = fresh != null && fresh.equals(kept) && strings;
        if (!same && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
            LOGGER.warn("Bons and Furious: geckolib_constant_resource_reuse SHADOW MISMATCH {}: {}.{} answered {}, kept {}", SHADOW_MISMATCHES.get(),
                    model.getClass().getName(), NAMES[which], fresh, kept);
        }
        return fresh;
    }

    private static ResourceLocation textureShadow(Template t, ResourceLocation kept, Object model, Object animatable, Operation<ResourceLocation> original, String s) {
        ResourceLocation fresh = original.call(model, animatable);
        TEXTURE_SHADOW_CHECKS.incrementAndGet();
        boolean namespace = fresh != null && (t.parsed ? fresh.getNamespace().equals(kept.getNamespace()) : fresh.getNamespace() == kept.getNamespace());
        boolean same = fresh != null && fresh.equals(kept) && namespace;
        if (!same && TEXTURE_SHADOW_MISMATCHES.incrementAndGet() <= 20) {
            LOGGER.warn("Bons and Furious: geckolib_texture_template_memo SHADOW MISMATCH {}: {}.getTextureResource answered {}, kept {} for texture {}",
                    TEXTURE_SHADOW_MISMATCHES.get(), model.getClass().getName(), fresh, kept, s);
        }
        return fresh;
    }

    // ------------------------------------------------------------------------------------------------- the template proof

    /** A public static factory of ResourceLocation returning a ResourceLocation, with no @MixinMerged on it. */
    static boolean factoryOk(String name, Class<?>... params) {
        try {
            Method m = ResourceLocation.class.getMethod(name, params);
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
        if (constantP(n2)) {
            if (!PARSE_OK) return new Getter(false, null, "ResourceLocation.parse is absent or replaced by a mixin");
            return new Getter(true, param, "constant (parse)", "P", null);
        }
        if (which == TEXTURE && param != geo) {
            Template t = template(n2, param, model.getClassLoader());
            if (t != null) return new Getter(false, param, "texture template", t.parsed ? "TP" : "TB", t);
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

    /** Shape B: ldc String, ldc String, invokestatic ResourceLocation.fromNamespaceAndPath(String, String), areturn. */
    static boolean constantB(MethodNode m) {
        List<AbstractInsnNode> c = code(m);
        if (c.size() != 4 || !m.tryCatchBlocks.isEmpty()) return false;
        if (!(c.get(0) instanceof LdcInsnNode l1) || !(l1.cst instanceof String)) return false;
        if (!(c.get(1) instanceof LdcInsnNode l2) || !(l2.cst instanceof String)) return false;
        if (!(c.get(2) instanceof MethodInsnNode f) || !factoryCall(f, "fromNamespaceAndPath", SS_DESC)) return false;
        return c.get(3).getOpcode() == Opcodes.ARETURN;
    }

    /** Shape P (MCreator for 1.21.1): ldc String, invokestatic ResourceLocation.parse(String), areturn. */
    static boolean constantP(MethodNode m) {
        List<AbstractInsnNode> c = code(m);
        if (c.size() != 3 || !m.tryCatchBlocks.isEmpty()) return false;
        if (!(c.get(0) instanceof LdcInsnNode l1) || !(l1.cst instanceof String)) return false;
        if (!(c.get(1) instanceof MethodInsnNode f) || !factoryCall(f, "parse", S_DESC)) return false;
        return c.get(2).getOpcode() == Opcodes.ARETURN;
    }

    /** invokestatic ResourceLocation.name(args)ResourceLocation on the class itself. */
    static boolean factoryCall(MethodInsnNode f, String name, String args) {
        return f.getOpcode() == Opcodes.INVOKESTATIC && !f.itf && f.owner.equals(RL) && f.name.equals(name) && f.desc.equals(args + RL_DESC);
    }

    /**
     * Shape TP: aload 1, invokevirtual X.getTexture()String, invokedynamic StringConcatFactory.makeConcatWithConstants
     * (String)String with a recipe of constant text around exactly one argument whose leading text holds "ns:" (a ':' after at
     * least one character), invokestatic ResourceLocation.parse(String), areturn. Shape TB: ldc String ns, then the same
     * getTexture and concatenation (any constant text), invokestatic ResourceLocation.fromNamespaceAndPath(String, String),
     * areturn. X a supertype of (or) the bridge's type declaring a public getTexture(). Null for anything else, and for a
     * shape whose factory PARSE_OK / FNAP_OK does not vouch for.
     */
    static Template template(MethodNode m, Class<?> param, ClassLoader loader) throws Exception {
        List<AbstractInsnNode> c = code(m);
        if (!m.tryCatchBlocks.isEmpty()) return null;
        boolean parsed;
        String ns;
        int at;
        if (c.size() == 5) {
            parsed = true;
            ns = null;
            at = 0;
        } else if (c.size() == 6 && c.get(0) instanceof LdcInsnNode l1 && l1.cst instanceof String s) {
            parsed = false;
            ns = s;
            at = 1;
        } else {
            return null;
        }
        if (!(c.get(at) instanceof VarInsnNode a1) || a1.getOpcode() != Opcodes.ALOAD || a1.var != 1) return null;
        if (!(c.get(at + 1) instanceof MethodInsnNode get) || get.getOpcode() != Opcodes.INVOKEVIRTUAL || get.itf
                || !get.name.equals("getTexture") || !get.desc.equals("()Ljava/lang/String;")) return null;
        if (!(c.get(at + 2) instanceof InvokeDynamicInsnNode indy) || !indy.name.equals("makeConcatWithConstants")
                || !indy.desc.equals("(Ljava/lang/String;)Ljava/lang/String;")) return null;
        Handle bsm = indy.bsm;
        if (bsm.getTag() != Opcodes.H_INVOKESTATIC || !bsm.getOwner().equals(CONCAT_FACTORY) || !bsm.getName().equals("makeConcatWithConstants")
                || indy.bsmArgs.length != 1 || !(indy.bsmArgs[0] instanceof String recipe)) return null;
        int arg = recipe.indexOf('\u0001');
        if (arg < 0 || recipe.indexOf('\u0001', arg + 1) >= 0 || recipe.indexOf('\u0002') >= 0) return null;
        if (!(c.get(at + 3) instanceof MethodInsnNode f)) return null;
        if (parsed ? !factoryCall(f, "parse", S_DESC) || !PARSE_OK : !factoryCall(f, "fromNamespaceAndPath", SS_DESC) || !FNAP_OK) return null;
        if (c.get(at + 4).getOpcode() != Opcodes.ARETURN) return null;
        String prefix = recipe.substring(0, arg), suffix = recipe.substring(arg + 1);
        if (parsed) {
            int colon = prefix.indexOf(':');
            if (colon < 1) return null;   // the namespace must be fixed by the constant text before the texture name
            ns = prefix.substring(0, colon);
            prefix = prefix.substring(colon + 1);
        }
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
        return new Template(ns, prefix, suffix, parsed, owner, h);
    }

    /**
     * The getTexture() an invokevirtual selects for {@code type} (the nearest declaration, walking up) is aload 0, getfield
     * Entity.entityData, getstatic of an EntityDataAccessor, invokevirtual SynchedEntityData.get, checkcast String, areturn,
     * in a class no mixin merged anything into (NeoForge 1.21.1 runs on Mojang names).
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
            if (!(c.get(1) instanceof FieldInsnNode data) || data.getOpcode() != Opcodes.GETFIELD || !data.name.equals("entityData")
                    || !data.desc.equals("L" + SYNCHED + ";")) return false;
            if (!(c.get(2) instanceof FieldInsnNode acc) || acc.getOpcode() != Opcodes.GETSTATIC
                    || !acc.desc.equals("Lnet/minecraft/network/syncher/EntityDataAccessor;")) return false;
            if (!(c.get(3) instanceof MethodInsnNode get) || get.getOpcode() != Opcodes.INVOKEVIRTUAL || !get.owner.equals(SYNCHED)
                    || !get.name.equals("get") || !get.desc.equals("(Lnet/minecraft/network/syncher/EntityDataAccessor;)Ljava/lang/Object;")) return false;
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
