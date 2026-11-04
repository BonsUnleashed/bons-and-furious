package bons.furious.patch.l2archery_coo49;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch l2archery_bow_upgrades_memo (L2 Archery 2.5.0, both sides; exact). L2 Archery is LGPL-2.1; none
 * of its code is carried. Ours, idea from Collections Of Optimizations (l2archery.cacheBowUpgrades), idea text only.
 *
 * What L2 Archery does: GenericBowItem.getUpgrades(stack), called several times per rendered bow per frame (durability
 * bar, flux numbers) and per tick while drawing, reads the stack's "upgrades" list and, for every id string, parses a new
 * ResourceLocation (splitting the string and checking every character) and looks the upgrade up in L2 Archery's upgrade
 * registry, then hands back a new ArrayList of the upgrades it found.
 *
 * What the switch does: the one `new ResourceLocation(id)` of that loop (a @Redirect of the constructor call) hands out the
 * location kept for that id string; everything else runs as shipped (the getOrCreateTag call, the contains check, L2
 * Library's list read, the registry lookups), and a bow without an upgrades entry never reaches the call. Why the answers
 * are the same: a ResourceLocation is an immutable value made from the string alone (the same namespace and path, so equal,
 * with the same hash code), and the loop only hands it to the registry's getValue, which looks it up by equality. An
 * invalid id is never kept: the constructor throws the same exception as in the shipped code. At most 4,096 id strings are
 * kept; an id the switch has not kept is parsed as shipped, after one lookup.
 *
 * Shadow mode (-Dbons_and_furious.l2archeryBowUpgradesMemo.shadow=true): every kept location is compared with a freshly
 * parsed one and the fresh one is used: SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20. Flag:
 * -Dbons_and_furious.l2archeryBowUpgradesMemo=false parses every time. HITS counts kept answers only with
 * -Dbons_and_furious.l2archeryBowUpgradesMemo.count=true (the harness; nothing is counted in game).
 */
public final class BowUpgrades {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.l2archeryBowUpgradesMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.l2archeryBowUpgradesMemo.shadow");
    static final boolean COUNT = Boolean.getBoolean("bons_and_furious.l2archeryBowUpgradesMemo.count");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Locations handed out from the cache (counted only with the count property: for the harness). */
    public static final AtomicLong HITS = new AtomicLong();
    static final int MAX_IDS = 4096;
    private static final ConcurrentHashMap<String, ResourceLocation> IDS = new ConcurrentHashMap<>();
    private static volatile boolean announced;

    private BowUpgrades() {
    }

    /** The loop's new ResourceLocation(id). */
    public static ResourceLocation id(String id) {
        if (!enabled) return new ResourceLocation(id);
        ResourceLocation kept = IDS.get(id);
        if (kept == null) {
            ResourceLocation made = new ResourceLocation(id);   // an invalid id throws here, as in the shipped code, and is never kept
            if (IDS.size() < MAX_IDS) IDS.putIfAbsent(id, made);
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: l2archery_bow_upgrades_memo applies (L2 Archery bows keep their parsed upgrade ids)");
            }
            return made;
        }
        if (SHADOW) {
            ResourceLocation fresh = new ResourceLocation(id);
            SHADOW_CHECKS.incrementAndGet();
            if ((!fresh.equals(kept) || !fresh.m_135827_().equals(kept.m_135827_()) || !fresh.m_135815_().equals(kept.m_135815_()))
                    && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
                LOGGER.warn("Bons and Furious: l2archery_bow_upgrades_memo shadow mismatch for id {}: kept {}, parsed {}", id, kept, fresh);
            }
            return fresh;
        }
        if (COUNT) HITS.incrementAndGet();
        return kept;
    }
}
