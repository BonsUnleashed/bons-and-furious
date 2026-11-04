package bons.furious.patch.forge_listener_guard;

import java.util.function.Predicate;

/**
 * forge_listener_prologue_guard: "is the code of this class exactly its class file?", from ModLauncher's transformer
 * audit trail (the record crash reports print as "Transformer Audit"). ModLauncher adds an entry for a class only when
 * a launch plugin or a transformer changed it (LaunchPluginHandler.offerClassNodeToPlugins records a plugin only when
 * it returned change flags; Mixin adds one custom entry per applied mixin config; coremods and transformation services
 * appear as "xf:"). Accepted: reasons ("re:..."), the access transformer ("pl:accesstransformer:..", access flags
 * only), RuntimeDistCleaner ("pl:runtimedistcleaner:..", removes members of the other side only) and, for Forge event
 * classes, eventbus' own EventSubclassTransformer ("pl:eventbus:..", it only adds the listener-list, cancelable and
 * has-result members). Anything else - every mixin, coremod or other plugin - means the code may differ from the bytes
 * the analysis reads: that class's listeners are always called.
 *
 * Unknown (no ModLauncher, no audit trail) answers false, so nothing is guarded. The offline harness supplies its own
 * answer through {@link #harness(Predicate)}.
 */
public final class AuditTrail {
    private static volatile Predicate<String> override;
    private static volatile Object trail;   // cpw.mods.modlauncher.api.ITransformerAuditTrail, or a Boolean.FALSE marker
    private static final Object NONE = Boolean.FALSE;

    private AuditTrail() {}

    /** Harness only (plain JVM, no ModLauncher): the answer for every class name. */
    public static void harness(Predicate<String> untransformed) {
        override = untransformed;
    }

    /** True when the class's audit string has only accepted entries. eventClass: eventbus' event transformer allowed. */
    public static boolean untransformed(String dottedName, boolean eventClass) {
        Predicate<String> o = override;
        if (o != null) return o.test(dottedName);
        String audit = auditString(dottedName);
        if (audit == null) return false;
        if (audit.isEmpty()) return true;
        for (String part : audit.split(",")) {
            String p = part.trim();
            if (p.isEmpty() || p.startsWith("re:") || p.startsWith("pl:accesstransformer:") || p.startsWith("pl:runtimedistcleaner:")) continue;
            if (eventClass && p.startsWith("pl:eventbus:")) continue;
            return false;
        }
        return true;
    }

    /** The audit string, "" when the class has no entry, null when there is no audit trail. */
    static String auditString(String dottedName) {
        Object t = trail;
        if (t == null) t = trail = find();
        if (t == NONE) return null;
        try {
            return ((cpw.mods.modlauncher.api.ITransformerAuditTrail) t).getAuditString(dottedName);
        } catch (Throwable e) {
            trail = NONE;
            return null;
        }
    }

    private static Object find() {
        try {
            cpw.mods.modlauncher.Launcher launcher = cpw.mods.modlauncher.Launcher.INSTANCE;
            if (launcher == null) return NONE;
            Object a = launcher.environment().getProperty(cpw.mods.modlauncher.api.IEnvironment.Keys.AUDITTRAIL.get()).orElse(null);
            return a != null ? a : NONE;
        } catch (Throwable e) {
            return NONE;
        }
    }
}
