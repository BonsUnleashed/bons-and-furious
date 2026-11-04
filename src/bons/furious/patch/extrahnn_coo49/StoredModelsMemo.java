package bons.furious.patch.extrahnn_coo49;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch extrahnn_stored_models_memo (Extra Hostile Neural Networks 1.2.2, both sides; exact). Extra
 * Hostile Neural Networks is MIT; none of its code is carried. Ours, idea from Collections Of Optimizations
 * (extrahnn.cacheStoredModels), idea text only.
 *
 * What Extra Hostile Neural Networks does: ExtraDataModelItem.getStoredModels(stack) reads the stack's data_model.ids list
 * and, for every id string, parses a new ResourceLocation (splitting the string and checking every character) and asks
 * Hostile Neural Networks' DataModelRegistry for its DynamicHolder, then hands back a new ArrayList of those holders.
 * Callers: the item renderer every frame, getName (every slot, tooltip and recipe-viewer search), the Ultimate Simulation
 * Chamber and the Simulation Modeling block every tick.
 *
 * What the switch does: the one `new ResourceLocation(id)` of that loop (a @Redirect of the constructor call) hands out the
 * location kept for that id string; everything else runs as shipped, and the empty-stack and tagless paths never reach the
 * call. Why the answers are the same: a ResourceLocation is an immutable value made from the string alone (the same
 * namespace and path, so equal, with the same hash code), and the loop only hands it to DataModelRegistry.holder, which
 * finds the holder by equality (Placebo's DynamicRegistry keeps one holder per id in a ConcurrentHashMap that is only ever
 * added to). An invalid id is never kept: the constructor throws the same exception as in the shipped code. At most 4,096
 * id strings are kept (Hostile Neural Networks 5.3.3 ships 67 models); an id the switch has not kept is parsed as shipped,
 * after one lookup.
 *
 * Shadow mode (-Dbons_and_furious.extrahnnStoredModelsMemo.shadow=true): every kept location is compared with a freshly
 * parsed one (equal, same namespace and path) and the fresh one is used: SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the
 * first 20. Flag: -Dbons_and_furious.extrahnnStoredModelsMemo=false parses every time. HITS counts kept answers only with
 * -Dbons_and_furious.extrahnnStoredModelsMemo.count=true (the harness; nothing is counted in game).
 */
public final class StoredModelsMemo {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.extrahnnStoredModelsMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.extrahnnStoredModelsMemo.shadow");
    static final boolean COUNT = Boolean.getBoolean("bons_and_furious.extrahnnStoredModelsMemo.count");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Locations handed out from the cache (counted only with the count property: for the harness). */
    public static final AtomicLong HITS = new AtomicLong();
    static final int MAX_IDS = 4096;
    private static final ConcurrentHashMap<String, ResourceLocation> IDS = new ConcurrentHashMap<>();
    private static volatile boolean announced;

    private StoredModelsMemo() {
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
                LOGGER.info("Bons and Furious: extrahnn_stored_models_memo applies (data model items keep their parsed model ids)");
            }
            return made;
        }
        if (SHADOW) {
            ResourceLocation fresh = new ResourceLocation(id);
            SHADOW_CHECKS.incrementAndGet();
            if ((!fresh.equals(kept) || !fresh.m_135827_().equals(kept.m_135827_()) || !fresh.m_135815_().equals(kept.m_135815_()))
                    && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
                LOGGER.warn("Bons and Furious: extrahnn_stored_models_memo shadow mismatch for id {}: kept {}, parsed {}", id, kept, fresh);
            }
            return fresh;
        }
        if (COUNT) HITS.incrementAndGet();
        return kept;
    }
}
