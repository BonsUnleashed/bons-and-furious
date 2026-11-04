package bons.furious.patch.tomeofblood_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch tomeofblood_living_upgrade_uuid_memo (Tome of Blood: Rebirth 0.4.5, LGPL-3.0; both sides; exact
 * variant). Ours; no Tome of Blood code is carried. Idea: Collections Of Optimizations (tomeofblood.cacheLivingUpgradeUuids),
 * idea text only.
 *
 * What the mod does. LivingMageArmorItem.getAttributeModifiers (asked whenever anything wants the chestplate's attributes:
 * every armour change, every stack change of the armour (it stores its living experience in its own tag), every tooltip
 * frame, recipe viewers) hands each trained Blood Magic living upgrade its attribute modifiers with the id
 * UUID.nameUUIDFromBytes(upgrade.getKey().toString().getBytes()): a new MD5 digest from the security providers and a hash
 * of the key's bytes on every call.
 *
 * What the switch does. The UUID.nameUUIDFromBytes call is wrapped: bytes seen before (equal content) are answered with the
 * UUID the original computed for them; new bytes run the original, whose answer is kept (at most 4,096 different keys, then
 * every new key is computed and not kept). The key's toString and getBytes still run as shipped, so the bytes are the ones
 * the original would hash; nameUUIDFromBytes is a pure function of them, so the value is the same. Exact variant: the one
 * internal difference is that a key's UUID is the same object on every call instead of a new equal object (attribute code
 * compares modifier ids with equals). Null bytes run the original (which throws as before).
 *
 * Runtime flag: -Dbons_and_furious.tomeofbloodLivingUpgradeUuidMemo=false. SHADOW MODE (rigs):
 * -Dbons_and_furious.tomeofbloodLivingUpgradeUuidMemo.shadow=true runs the original on every hit as well and compares
 * (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20); the original's UUID is returned.
 */
public final class TobUuids {
    public static final String KEY = "tomeofblood_living_upgrade_uuid_memo";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.tomeofbloodLivingUpgradeUuidMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.tomeofbloodLivingUpgradeUuidMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** At most this many keys are kept. */
    public static final int LIMIT = 4096;
    /** Statistics for probes (plain fields): answers from the memo, original calls made. */
    public static long hits, misses;

    private static final ConcurrentHashMap<Bytes, UUID> MEMO = new ConcurrentHashMap<>();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private TobUuids() {
    }

    /** A byte array compared by content. The kept copies are private; a lookup key wraps the caller's array. */
    static final class Bytes {
        final byte[] b;
        final int hash;

        Bytes(byte[] b) {
            this.b = b;
            this.hash = Arrays.hashCode(b);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Bytes x && x.hash == this.hash && Arrays.equals(x.b, this.b);
        }
    }

    /** UUID.nameUUIDFromBytes(bytes), wrapped. */
    public static UUID uuid(byte[] bytes, Operation<UUID> original) {
        if (!enabled || bytes == null) return original.call(bytes);
        Bytes key = new Bytes(bytes);
        UUID kept = MEMO.get(key);
        if (kept != null) {
            if (SHADOW) {
                UUID asked = original.call(bytes);
                SHADOW_CHECKS.incrementAndGet();
                if (!kept.equals(asked) && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                    LOGGER.warn("Bons and Furious: {} SHADOW MISMATCH: kept {}, computed {}", KEY, kept, asked);
                return asked;
            }
            hits++;
            return kept;
        }
        UUID computed = original.call(bytes);
        misses++;
        if (computed != null && MEMO.size() < LIMIT) MEMO.putIfAbsent(new Bytes(bytes.clone()), computed);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (Tome of Blood's living upgrade modifier ids are computed once per upgrade)", KEY);
        }
        return computed;
    }

    /** For the harness: how many keys are kept. */
    public static int kept() {
        return MEMO.size();
    }
}
