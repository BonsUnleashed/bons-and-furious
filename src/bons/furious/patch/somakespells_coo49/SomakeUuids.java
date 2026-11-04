package bons.furious.patch.somakespells_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch somakespells_balance_uuid_memo (Somake Spells 1.0.9, All Rights Reserved; both sides; exact
 * variant). Ours; no Somake Spells code is carried. Idea: Collections Of Optimizations (somakespells.cacheBalanceUuids), idea
 * text only.
 *
 * What the mod does. AttributeBalanceHandler rebalances weapons, staves, spell books and armour through Forge's
 * ItemAttributeModifierEvent (and Curios' attribute event): every time anything asks a stack of a rebalanced item for its
 * attributes (equipment changes, every tooltip frame, recipe viewers indexing items) it builds up to sixteen attribute
 * modifiers, each with the id uuid(key) = UUID.nameUUIDFromBytes(key.getBytes(UTF_8)) of a fixed key string such as
 * "weapon:attack_damage" or the item's own prefix plus ":armor". Every call asks the security providers for a new MD5
 * digest and hashes the key again.
 *
 * What the switch does. A key seen before is answered with the UUID the original computed for it; a new key runs the
 * original and keeps its answer (at most 4,096 keys; beyond that every new key is computed and not kept). nameUUIDFromBytes
 * is a pure function of the key's bytes, and a String key has the same UTF-8 bytes every time, so the value is the same.
 * Exact variant: the one internal difference is that a key's UUID is now the same object on every call instead of a new
 * equal object (attribute code compares modifier ids with equals). A null key runs the original (which throws as before).
 *
 * Runtime flag: -Dbons_and_furious.somakespellsBalanceUuidMemo=false. SHADOW MODE (rigs):
 * -Dbons_and_furious.somakespellsBalanceUuidMemo.shadow=true runs the original on every hit as well and compares the two
 * values (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20); the original's UUID is returned.
 */
public final class SomakeUuids {
    public static final String KEY = "somakespells_balance_uuid_memo";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.somakespellsBalanceUuidMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.somakespellsBalanceUuidMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** At most this many keys are kept. */
    public static final int LIMIT = 4096;
    /** Statistics for probes (plain fields): answers from the memo, original calls made. */
    public static long hits, misses;

    private static final ConcurrentHashMap<String, UUID> MEMO = new ConcurrentHashMap<>();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private SomakeUuids() {
    }

    /** AttributeBalanceHandler.uuid(key), wrapped. */
    public static UUID uuid(String key, Operation<UUID> original) {
        if (!enabled || key == null) return original.call(key);
        UUID kept = MEMO.get(key);
        if (kept != null) {
            if (SHADOW) {
                UUID asked = original.call(key);
                SHADOW_CHECKS.incrementAndGet();
                if (!kept.equals(asked) && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                    LOGGER.warn("Bons and Furious: {} SHADOW MISMATCH for key {}: kept {}, computed {}", KEY, key, kept, asked);
                return asked;
            }
            hits++;
            return kept;
        }
        UUID computed = original.call(key);
        misses++;
        if (computed != null && MEMO.size() < LIMIT) MEMO.putIfAbsent(key, computed);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (Somake Spells' attribute balance ids are computed once per key)", KEY);
        }
        return computed;
    }

    /** For the harness: how many keys are kept. */
    public static int kept() {
        return MEMO.size();
    }
}
