package bons.furious.patch.modernfix_capability_guards;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.modernfix.annotation.FeatureLevel;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.embeddedt.modernfix.forge.capability.CapabilityProviderDispatcherGenerator;
import org.embeddedt.modernfix.forge.capability.GeneratedDispatcher;
import org.embeddedt.modernfix.forge.capability.analysis.CapabilityAnalysisResult;
import org.embeddedt.modernfix.forge.capability.analysis.CapabilityRef;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Bons and Furious switch modernfix_capability_guards (ModernFix 5.27.77 and 5.27.89, capability classes byte-identical;
 * Forge 47.4.16 / 47.4.28, capability classes byte-identical; both sides). Idea: ModernFix's own bytecode analysis of
 * capability providers (BETA, skipped at ModernFix's default GA level) and NeoForge 20.3's capability rework, idea text
 * only; the proof, the refusal rules and the shadow mode are ours. No ModernFix code is carried: we call its public
 * dispatcher generator with analysis on and decide, for each provider class, whether its analysis may be acted on.
 *
 * <p>What the game does. Forge asks every capability provider attached to an entity, item stack, block entity or chunk,
 * in order, until one answers (CapabilityDispatcher.getCapability). ModernFix's faster_capabilities (on at GA) replaces that
 * loop with a generated class per provider-type list, but at GA it still calls every provider for every query: its
 * analysis that would let it skip providers that cannot answer is BETA, and ModernFix's BETA level also re-sorts the
 * providers of every AttachCapabilitiesEvent (AttachCapabilitiesEventMixin), analyses the classes as they are in the jar
 * rather than as they run, and does not check for side effects. In this pack a vanilla mob carries 20-35 providers and the
 * dispatcher path is 4.85 % of the entity-scene server thread.
 *
 * <p>What the switch does. ModernFix's one-argument getOrGenerateDispatcher (called from its CapabilityDispatcher
 * constructor hook) asks for the dispatcher with analysis on; the provider order, ModernFix's generated code and its
 * cache are unchanged (the cache key includes the analysis flag). The AttachCapabilitiesEvent re-sort is never enabled.
 * For every provider class the generator analyses, ModernFix's own result is kept only when ours confirms it, otherwise
 * the provider gets Indeterminate (called for every capability, exactly as at GA):
 * <ul>
 *   <li>no mixin config of any mod targets the provider's class or any class between it and the class that declares its
 *       getCapability (the analysed bytecode is then the code that runs);</li>
 *   <li>the declaring class's getCapability passes {@link ProviderProof#proveBody}: for every capability outside the claimed
 *       set (null included) and every side, the body returns the LazyOptional.empty() singleton, writes nothing, calls
 *       nothing else and cannot throw;</li>
 *   <li>every static Capability field the body reads resolves the way ModernFix's generator resolves it, is static, final,
 *       non-null, of Forge's own Capability class, and reading it now cannot run code at another moment than before
 *       (field rules in {@link ProviderProof}).</li>
 * </ul>
 * A provider is then skipped exactly for capabilities its body provably answers with the singleton and nothing else, so the
 * first present answer, every object handed out, the order of the remaining calls and every exception are the same.
 * KnownCapabilities with more than five entries and Indeterminate results are left as they are (ModernFix calls such
 * providers always).
 *
 * <p>Stands down (ModernFix decides alone, as without this switch): ModernFix runs at stability_level BETA (its own
 * analysis and re-sort run), or its option mixin.perf.faster_capabilities.bytecode_analysis is off. Runtime switch:
 * -Dbons_and_furious.modernfixCapabilityGuards=false (dispatchers generated afterwards are ModernFix's GA ones).
 *
 * <p>SHADOW mode for rigs: -Dbons_and_furious.modernfixCapabilityGuards.shadow=true gives every CapabilityDispatcher a
 * {@link ShadowDispatcher} instead: it calls every provider in order, exactly as ModernFix's GA dispatcher, and checks for
 * each provider the switch would have skipped that its answer is the singleton (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN at
 * most 20, naming the provider class and the capability).
 */
public final class CapabilityGuards {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.modernfixCapabilityGuards", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.modernfixCapabilityGuards.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Provider classes whose ModernFix analysis was confirmed / refused (each class counted once). */
    public static final AtomicLong ACCEPTED = new AtomicLong(), REFUSED = new AtomicLong();
    /** ModernFix's generator: its class loader is the one it resolves capability fields with. */
    private static final ClassLoader GENERATOR_LOADER = CapabilityProviderDispatcherGenerator.class.getClassLoader();
    private static final ConcurrentHashMap<Class<?>, Verdict> VERDICTS = new ConcurrentHashMap<>();
    private static volatile Boolean standsDown;
    private static volatile Set<String> mixinTargets;
    private static volatile boolean announced, warned;

    private CapabilityGuards() {
    }

    /**
     * Our decision for one provider class: accepted (ModernFix's claim may be acted on; {@code known} = the identity set of
     * the claimed capability objects, empty for AlwaysEmpty) or refused with a reason.
     */
    public record Verdict(boolean accepted, boolean alwaysEmpty, Set<Object> known, String reason) {
        static Verdict refused(String why) {
            return new Verdict(false, false, Set.of(), why);
        }
    }

    // ------------------------------------------------------------------------------------------- the two hooks

    /** CapabilityProviderDispatcherGenerator.getOrGenerateDispatcher(ICapabilityProvider[]) (ModernFix's GA entry). */
    public static GeneratedDispatcher dispatcher(ICapabilityProvider[] providers, Operation<GeneratedDispatcher> original) {
        if (!enabled || providers == null || standsDown()) return original.call((Object) providers);
        announce();
        if (SHADOW) return ShadowDispatcher.of(providers);
        return CapabilityProviderDispatcherGenerator.getOrGenerateDispatcher(providers, true);
    }

    /** The CapabilityAnalyzer.analyze(type) call in ModernFix's generateClass (only reached with analysis on). */
    public static CapabilityAnalysisResult analyze(Class<? extends ICapabilityProvider> type, Operation<CapabilityAnalysisResult> original) {
        CapabilityAnalysisResult theirs = original.call(type);
        if (!enabled || standsDown()) return theirs;
        return confirmed(type, theirs);
    }

    /** ModernFix's result when ours confirms it, otherwise Indeterminate (the provider is called for every capability). */
    static CapabilityAnalysisResult confirmed(Class<?> type, CapabilityAnalysisResult theirs) {
        if (type == null || theirs == null || theirs instanceof CapabilityAnalysisResult.Indeterminate) return theirs;
        if (theirs instanceof CapabilityAnalysisResult.KnownCapabilities k && k.capabilities().size() > 5) return theirs;   // called always
        Verdict v = verdict(type, theirs);
        return v.accepted() ? theirs : new CapabilityAnalysisResult.Indeterminate("Bons and Furious: " + v.reason());
    }

    /** Our verdict for the provider class (cached per class; ModernFix caches its analysis per class the same way). */
    public static Verdict verdict(Class<?> type, CapabilityAnalysisResult theirs) {
        Verdict v = VERDICTS.get(type);
        if (v != null) return v;
        try {
            v = prove(type, theirs);
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                LOGGER.warn("Bons and Furious: modernfix_capability_guards could not check {} ({}); it and any other provider it cannot check are called for every capability",
                        type.getName(), t.toString());
            }
            v = Verdict.refused("check failed: " + t);
        }
        Verdict prev = VERDICTS.putIfAbsent(type, v);
        if (prev != null) return prev;
        (v.accepted() ? ACCEPTED : REFUSED).incrementAndGet();
        LOGGER.debug("Bons and Furious: modernfix_capability_guards: {} {}", type.getName(), v.accepted()
                ? (v.alwaysEmpty() ? "is never called (proven always empty)" : "is called only for its " + v.known().size() + " capabilities") : "is called always (" + v.reason() + ")");
        return v;
    }

    // ------------------------------------------------------------------------------------------- the proof

    static Verdict prove(Class<?> type, CapabilityAnalysisResult theirs) throws Exception {
        boolean alwaysEmpty = theirs instanceof CapabilityAnalysisResult.AlwaysEmpty;
        if (!alwaysEmpty && !(theirs instanceof CapabilityAnalysisResult.KnownCapabilities)) return Verdict.refused("analysis " + theirs);
        Class<?> declaring = type.getMethod("getCapability", Capability.class, Direction.class).getDeclaringClass();
        if (declaring == ICapabilityProvider.class) return Verdict.refused("no getCapability of its own");
        // 1. the analysed bytecode is the code that runs: no mixin targets the provider's class or the declaring class's chain
        Set<String> targets = mixinTargets();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (targets.contains(c.getName())) return Verdict.refused("a mixin targets " + c.getName());
            if (c == declaring) break;
        }
        if (targets.contains(declaring.getName())) return Verdict.refused("a mixin targets " + declaring.getName());
        // 2. the claimed capabilities, resolved the way ModernFix's generator resolves them
        Set<Object> known = Collections.newSetFromMap(new IdentityHashMap<>());
        String[] why = new String[1];
        RuntimeFields fields = new RuntimeFields(type);
        if (!alwaysEmpty) {
            for (CapabilityRef ref : ((CapabilityAnalysisResult.KnownCapabilities) theirs).capabilities()) {
                Object c = fields.capability(ref.owner(), ref.fieldName(), why);
                if (c == null) return Verdict.refused("claimed capability " + ref + ": " + why[0]);
                known.add(c);
            }
        }
        // 3. the body, read the way ModernFix reads it (the declaring class's own class file)
        ClassNode node = classNode(declaring);
        if (node == null) return Verdict.refused("class file of " + declaring.getName() + " not readable");
        MethodNode method = null;
        for (MethodNode m : node.methods) if (m.name.equals("getCapability") && m.desc.equals(ProviderProof.GET_CAPABILITY_DESC)) method = m;
        String body = ProviderProof.proveBody(node, method, known, fields);
        if (body != null) return Verdict.refused(body);
        return new Verdict(true, alwaysEmpty, Collections.unmodifiableSet(known), null);
    }

    /** Static capability fields as ModernFix's generator finds them, with the field rules of ProviderProof. */
    static final class RuntimeFields implements ProviderProof.Fields, ProviderProof.ClassFiles {
        final Class<?> provider;

        RuntimeFields(Class<?> provider) {
            this.provider = provider;
        }

        /** A class with a constructed object is initialized: here FML's @Mod instance of a loaded mod. */
        @Override
        public boolean initialized(String internalName) {
            return modClasses().contains(internalName);
        }

        @Override
        public Object capability(String owner, String name, String[] why) {
            try {
                Class<?> named = Class.forName(owner.replace('/', '.'), false, GENERATOR_LOADER);
                Field f = refField(named, name);
                if (f == null) { why[0] = "not declared in " + named.getName() + " or its direct interfaces"; return null; }
                int mod = f.getModifiers();
                if (!Modifier.isStatic(mod) || !Modifier.isFinal(mod)) { why[0] = "not static final"; return null; }
                if (f.getType() != Capability.class) { why[0] = "not of type Capability"; return null; }
                Class<?> holder = f.getDeclaringClass();   // the class a read initializes (JLS 12.4.1)
                if (!initialized(holder) && !initialized(holder.getName().replace('.', '/'))) {
                    String pure = ProviderProof.pureStaticInit(classNode(holder), this);
                    if (pure != null) { why[0] = "reading it could initialize " + holder.getName() + " at another moment: " + pure; return null; }
                }
                f.setAccessible(true);
                Object v = f.get(null);
                if (v == null) { why[0] = "is null"; return null; }
                if (v.getClass() != Capability.class) { why[0] = "is a " + v.getClass().getName(); return null; }
                return v;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                why[0] = e.toString();
                return null;
            }
        }

        /** The provider's own class and its superclasses are initialized: a provider object exists. */
        private boolean initialized(Class<?> holder) {
            for (Class<?> c = provider; c != null; c = c.getSuperclass()) if (c == holder) return true;
            return false;
        }

        @Override
        public ClassNode read(String internalName) {
            try (InputStream in = (provider.getClassLoader() != null ? provider.getClassLoader() : GENERATOR_LOADER).getResourceAsStream(internalName + ".class")) {
                return in == null ? null : node(in.readAllBytes());
            } catch (Exception e) {
                return null;
            }
        }
    }

    /** ModernFix's getRefField rule: declared in the class, else in one of its direct interfaces. */
    static Field refField(Class<?> c, String name) {
        try {
            return c.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            for (Class<?> i : c.getInterfaces()) {
                try {
                    return i.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                    // next interface
                }
            }
        }
        return null;
    }

    /** The class's own class file, read as ModernFix's analyzer reads it (CapabilityAnalyzer.readClass). */
    static ClassNode classNode(Class<?> c) {
        try (InputStream in = c.getResourceAsStream("/" + c.getName().replace('.', '/') + ".class")) {
            return in == null ? null : node(in.readAllBytes());
        } catch (Exception e) {
            return null;
        }
    }

    static ClassNode node(byte[] bytes) {
        ClassNode n = new ClassNode();
        new ClassReader(bytes).accept(n, 0);
        return n;
    }

    private static volatile Set<String> modClasses;

    /**
     * Internal names of the classes of FML's constructed mod objects (each @Mod class of a loaded mod: an object of it
     * exists, so it is initialized). Read once dispatchers are generated (after mod construction); empty offline.
     */
    static Set<String> modClasses() {
        Set<String> s = modClasses;
        if (s != null) return s;
        Set<String> out = new HashSet<>();
        try {
            net.minecraftforge.fml.ModList list = net.minecraftforge.fml.ModList.get();
            if (list != null) list.forEachModContainer((id, c) -> {
                Object mod = c.getMod();
                if (mod != null) out.add(mod.getClass().getName().replace('.', '/'));
            });
        } catch (Throwable t) {
            LOGGER.debug("Bons and Furious: modernfix_capability_guards could not list the mod objects ({})", t.toString());
        }
        s = Set.copyOf(out);
        if (!s.isEmpty()) modClasses = s;
        return s;
    }

    /** Every class some mixin config targets (dotted names), read once after the configs were prepared. */
    static Set<String> mixinTargets() {
        Set<String> t = mixinTargets;
        if (t != null) return t;
        Set<String> out = new HashSet<>();
        try {
            for (org.spongepowered.asm.mixin.transformer.Config c : org.spongepowered.asm.mixin.Mixins.getConfigs()) {
                Set<String> s = c.getConfig().getTargets();
                if (s != null) out.addAll(s);
            }
        } catch (Throwable e) {
            LOGGER.debug("Bons and Furious: modernfix_capability_guards could not list mixin targets ({})", e.toString());
        }
        mixinTargets = t = Set.copyOf(out);
        return t;
    }

    // ------------------------------------------------------------------------------------------- stand-down, log

    static boolean standsDown() {
        Boolean s = standsDown;
        if (s != null) return s;
        synchronized (CapabilityGuards.class) {
            if (standsDown != null) return standsDown;
            boolean down = false;
            try {
                ModernFixMixinPlugin plugin = ModernFixMixinPlugin.instance;
                if (plugin != null) {
                    if (ModernFixMixinPlugin.activeFeatureLevel().isAtLeast(FeatureLevel.BETA)) {
                        down = true;
                        LOGGER.info("Bons and Furious: modernfix_capability_guards stands down: ModernFix runs at stability_level BETA, so its own capability analysis decides");
                    } else if (!plugin.isOptionEnabled("perf.faster_capabilities.bytecode_analysis.CapabilityAnalyzer")) {
                        down = true;
                        LOGGER.info("Bons and Furious: modernfix_capability_guards stands down: ModernFix's mixin.perf.faster_capabilities.bytecode_analysis is off in its config");
                    }
                }
            } catch (Throwable t) {
                down = true;
                LOGGER.warn("Bons and Furious: modernfix_capability_guards stands down: could not read ModernFix's settings ({})", t.toString());
            }
            standsDown = down;
            return down;
        }
    }

    private static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: modernfix_capability_guards applies: ModernFix's capability dispatchers skip a provider only for capabilities it is proven to answer with nothing{}",
                    SHADOW ? " - shadow verification on (every provider is still called)" : "");
        }
    }
}
