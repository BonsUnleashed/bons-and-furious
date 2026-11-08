package bons.furious.patch.armageddon_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch armageddon_progression_id_memo (Armageddon 3.3.0b for NeoForge 1.21.1, both sides; exact variant).
 * Helper of the group's two mixins; no Armageddon code is carried. Idea: Collections Of Optimizations
 * (armageddon.cacheProgressionIds), idea text only.
 *
 * Armageddon's progression gates (the eleven armour-tier gates, the enchanted-armour gate, the Hollow pickaxe tick, the mob
 * health / damage scaling and the Nether gate) run per player (or per mob) per tick on both sides. Each run builds the same
 * ids again from text: ResourceLocation.parse of a constant string (62 call sites, 26 distinct strings), ItemTags.create /
 * TagKey.create of those ids (47 + 2 sites) and, for the config lists of extra restricted items,
 * BuiltInRegistries.ITEM.getKey(item).toString() (45 sites), every result a new object (the same counts as the Forge
 * 1.20.1 build).
 *
 * The wrappers hand out kept results instead:
 *   parse      String -> the ResourceLocation the original made for it (only successful parses are kept, so a failing
 *              parse still throws on every call)
 *   itemTag    ResourceLocation -> the TagKey ItemTags.create returned (TagKey.create interns: the same object comes back)
 *   tag        (registry, ResourceLocation) -> the TagKey TagKey.create returned
 *   idString   ResourceLocation -> the String its toString returned
 * ResourceLocation is a final class on 1.21.1, so every key is a plain ResourceLocation (equality and text by namespace and
 * path). Every map is bounded ({@link #LIMIT} entries; past that the original call). Callers use the results only by value:
 * the advancement lookup (ServerAdvancementManager.get) and the tag tests compare ids by equals / the tag holder set, the
 * id strings go to List.contains (equals), so the one internal difference is object identity: a kept ResourceLocation /
 * String equal to the new one the original builds (the TagKey is the interned one either way).
 *
 * Flag: -Dbons_and_furious.armageddonProgressionIdMemo=false. SHADOW MODE:
 * -Dbons_and_furious.armageddonProgressionIdMemo.shadow=true makes every kept answer call the original too and compare
 * (equals for ids and strings, == for tag keys), counting SHADOW_CHECKS / SHADOW_MISMATCHES (WARN for the first 20); the
 * original's result is returned.
 */
public final class ProgressionIds {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.armageddonProgressionIdMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.armageddonProgressionIdMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Entries per map at most; a new key past it calls the original every time. */
    public static final int LIMIT = 4096;
    /** For the harness: answers given from a kept entry. */
    public static final AtomicLong KEPT = new AtomicLong();

    private static final ConcurrentHashMap<String, ResourceLocation> IDS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ResourceLocation, TagKey<?>> ITEM_TAGS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ResourceKey<?>, ConcurrentHashMap<ResourceLocation, TagKey<?>>> TAGS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ResourceLocation, String> STRINGS = new ConcurrentHashMap<>();
    private static volatile boolean announced;

    private ProgressionIds() {
    }

    private static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: armageddon_progression_id_memo applies (Armageddon's progression gates reuse their ids, tag keys and id strings){}",
                    SHADOW ? " (SHADOW MODE: every kept answer is compared with the original's)" : "");
        }
    }

    private static void mismatch(String what) {
        long n = SHADOW_MISMATCHES.incrementAndGet();
        if (n <= 20) LOGGER.warn("Bons and Furious: armageddon_progression_id_memo SHADOW MISMATCH {}: {}", n, what);
    }

    /** True for a key the maps may hold: a ResourceLocation (final on 1.21.1, so never a subclass with its own equality). */
    private static boolean plain(ResourceLocation id) {
        return id != null && id.getClass() == ResourceLocation.class;
    }

    /** ResourceLocation.parse(id) in a progression gate. */
    public static ResourceLocation parse(String id, Operation<ResourceLocation> original) {
        if (!enabled || id == null) return original.call(id);
        ResourceLocation kept = IDS.get(id);
        if (kept == null) {
            ResourceLocation fresh = original.call(id);   // a failing parse throws here, nothing is kept
            if (plain(fresh) && IDS.size() < LIMIT) {
                IDS.putIfAbsent(id, fresh);
                announce();
            }
            return fresh;
        }
        if (SHADOW) {
            ResourceLocation fresh = original.call(id);
            SHADOW_CHECKS.incrementAndGet();
            if (!kept.equals(fresh) || fresh.getClass() != ResourceLocation.class) mismatch("parse \"" + id + "\": kept " + kept + ", original " + fresh);
            return fresh;
        }
        KEPT.incrementAndGet();
        return kept;
    }

    /** ItemTags.create(id) in a progression gate. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static TagKey itemTag(ResourceLocation id, Operation<TagKey> original) {
        if (!enabled || !plain(id)) return original.call(id);
        TagKey kept = ITEM_TAGS.get(id);
        if (kept == null) {
            TagKey fresh = original.call(id);
            if (fresh != null && ITEM_TAGS.size() < LIMIT) {
                ITEM_TAGS.putIfAbsent(id, fresh);
                announce();
            }
            return fresh;
        }
        if (SHADOW) {
            TagKey fresh = original.call(id);
            SHADOW_CHECKS.incrementAndGet();
            if (fresh != kept) mismatch("ItemTags.create " + id + ": kept " + kept + " (" + System.identityHashCode(kept) + "), original " + fresh + " (" + System.identityHashCode(fresh) + ")");
            return fresh;
        }
        KEPT.incrementAndGet();
        return kept;
    }

    /** TagKey.create(registry, id) in a progression gate. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static TagKey tag(ResourceKey registry, ResourceLocation id, Operation<TagKey> original) {
        if (!enabled || registry == null || !plain(id)) return original.call(registry, id);
        ConcurrentHashMap<ResourceLocation, TagKey<?>> byId = TAGS.get(registry);
        TagKey kept = byId == null ? null : byId.get(id);
        if (kept == null) {
            TagKey fresh = original.call(registry, id);
            if (fresh != null && TAGS.size() < 64) {
                ConcurrentHashMap<ResourceLocation, TagKey<?>> m = TAGS.computeIfAbsent(registry, k -> new ConcurrentHashMap<>());
                if (m.size() < LIMIT) {
                    m.putIfAbsent(id, fresh);
                    announce();
                }
            }
            return fresh;
        }
        if (SHADOW) {
            TagKey fresh = original.call(registry, id);
            SHADOW_CHECKS.incrementAndGet();
            if (fresh != kept) mismatch("TagKey.create " + registry + " " + id + ": kept " + kept + ", original " + fresh);
            return fresh;
        }
        KEPT.incrementAndGet();
        return kept;
    }

    /** ResourceLocation.toString() of an item's registry id in a progression gate. */
    public static String idString(ResourceLocation id, Operation<String> original) {
        if (!enabled || !plain(id)) return original.call(id);   // null: the original call throws as before
        String kept = STRINGS.get(id);
        if (kept == null) {
            String fresh = original.call(id);
            if (fresh != null && STRINGS.size() < LIMIT) {
                STRINGS.putIfAbsent(id, fresh);
                announce();
            }
            return fresh;
        }
        if (SHADOW) {
            String fresh = original.call(id);
            SHADOW_CHECKS.incrementAndGet();
            if (!kept.equals(fresh)) mismatch("toString " + id + ": kept \"" + kept + "\", original \"" + fresh + "\"");
            return fresh;
        }
        KEPT.incrementAndGet();
        return kept;
    }

    /** For the harness: entries kept per map. */
    public static int[] sizes() {
        int tags = 0;
        for (ConcurrentHashMap<ResourceLocation, TagKey<?>> m : TAGS.values()) tags += m.size();
        return new int[] {IDS.size(), ITEM_TAGS.size(), tags, STRINGS.size()};
    }

    /** For the harness: forget every kept entry. */
    public static void clear() {
        IDS.clear();
        ITEM_TAGS.clear();
        TAGS.clear();
        STRINGS.clear();
    }
}
