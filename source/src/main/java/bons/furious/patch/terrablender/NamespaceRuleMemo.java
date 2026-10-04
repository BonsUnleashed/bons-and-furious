package bons.furious.patch.terrablender;

import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.slf4j.Logger;

/**
 * Bons and Furious switch terrablender_namespace_rule_memo (TerraBlender 4.1.0.8 for NeoForge 1.21.1, LGPL-3.0;
 * Minecraft 1.21.1, NeoForge 21.1.252; server side of world generation). Mojang names for Minecraft members.
 *
 * TerraBlender puts NamespacedSurfaceRuleSource on top of every overworld and nether surface rule it manages. Its rule
 * (NamespacedRule) runs for every stone block a chunk's surface pass visits (about 150,000 per 768-high chunk) and each
 * time finds the biome's namespace again: Holder.is with a capturing lambda, ResourceKey.location().getNamespace(),
 * Map.containsKey, then unwrapKey (an Optional) and Map.get with the same string. The map is TerraBlender's ImmutableMap,
 * fixed for the rule's lifetime (one rule per chunk surface context), and a Holder.Reference's key never changes, so the
 * rule selected for one Holder object is the same every time. NamespacedRuleMemoMixin keeps the last Holder and the
 * rule selected for it and selects again only when the biome object changes. A Holder.Direct has no key: is() is false
 * and nothing is selected, the same for the same object.
 *
 * -Dbons_and_furious.namespaceRuleMemo=false selects on every call (checked per call).
 * -Dbons_and_furious.namespaceRuleMemo.shadow=true (verification runs only) also selects on every memo hit and counts
 * selections that differ from the remembered one (SHADOW_CHECKS / SHADOW_MISMATCHES).
 *
 * Ported to 1.21.1: no logic change. Holder.Reference.is(Predicate) is still predicate.test(key()), Holder.Direct.is is
 * still false, and the memo keys on the Holder the context's biome supplier returns, so ModernFix's reused
 * PositionalBiomeGetter (one supplier object per context, re-aimed per block) is handled like vanilla's per-block
 * memoized supplier.
 */
public final class NamespaceRuleMemo {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.namespaceRuleMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.namespaceRuleMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Selections made (memo misses and switch-off calls); the proof reads it. */
    public static final AtomicLong SELECTIONS = new AtomicLong();
    private static volatile boolean announced;

    private NamespaceRuleMemo() {
    }

    /**
     * TerraBlender's selection for one call: the namespace rule for the biome's namespace when the map has that namespace,
     * otherwise null. A namespace present with no rule throws the NullPointerException TerraBlender's own call would.
     */
    public static Object select(Map<String, ?> rules, Holder<Biome> biome) {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: terrablender_namespace_rule_memo applies (TerraBlender's namespace rule is chosen once per biome instead of once per block){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        SELECTIONS.incrementAndGet();
        if (biome.is(key -> rules.containsKey(key.location().getNamespace()))) {
            Object selected = rules.get(biome.unwrapKey().get().location().getNamespace());
            if (selected == null) throw new NullPointerException("TerraBlender has no namespace rule for " + biome);
            // terrablender_lazy_namespace_rules: a deferred namespace rule is built here, at its first use, as its own
            // tryApply would build it in this same call; the memo then keeps the built rule.
            return LazyNamespaceRules.resolve(selected);
        }
        return null;
    }

    /** Shadow check on a memo hit: select again and compare with the remembered rule. */
    public static void shadow(Map<String, ?> rules, Holder<Biome> biome, Object remembered) {
        Object fresh = select(rules, biome);
        SHADOW_CHECKS.incrementAndGet();
        if (fresh != remembered && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: terrablender_namespace_rule_memo shadow mismatch for {}", biome);
    }

    /** Rethrows whatever TerraBlender's code or a method handle threw, unchanged and unwrapped. */
    public static RuntimeException rethrow(Throwable t) {
        return NamespaceRuleMemo.<RuntimeException>sneaky(t);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> T sneaky(Throwable t) throws T {
        throw (T) t;
    }
}
