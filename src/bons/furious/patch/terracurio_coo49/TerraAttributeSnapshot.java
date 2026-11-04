package bons.furious.patch.terracurio_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch terracurio_attribute_map_snapshot (Terra Curio 0.2.0, org.confluence.terra_curio, LGPL-3.0;
 * both sides). Ours; no Terra Curio code is carried. Idea: Collections Of Optimizations (terracurio.leanAttributeMap), idea
 * text only.
 *
 * <p>ModAttributes keeps its attribute replacement table in a private static Hashtable (MAP), and every
 * getCustomAttribute / hasCustomAttribute call (per living entity per tick on the client and the server thread through the
 * pickup-range hook, plus the damage, arrow and break-speed paths) is a synchronized Hashtable.get, so in single player the
 * two threads contend for one monitor. MAP is written only inside readJsonConfig (its own puts and ApothicHelper.preset(MAP),
 * which readJsonConfig calls); nothing else in the jar touches the private field (census of both builds; the guard lines pin
 * ModAttributes' methods and ApothicHelper.preset).
 *
 * <p>readJsonConfig's start (enter) withdraws the snapshot; its normal end (leave), when no other readJsonConfig call is in
 * progress, publishes a HashMap copy of MAP taken under MAP's own monitor into a volatile field. While a snapshot is
 * published, the two getters answer a non-null key from it: the same entries as the table (keys and values are the same
 * objects; Attribute keeps Object's identity equals and hashCode, so both maps find the same entry). A null key, an
 * unpublished snapshot (before the first readJsonConfig, while one runs, after one that threw: its end is never reached, so
 * nothing is published again), another table object or the switch off take the shipped Hashtable.get (the same
 * NullPointerException for a null key). Answers are identical; the only change is that the lock is no longer taken.
 *
 * <p>Runtime flag: -Dbons_and_furious.terracurioAttributeMapSnapshot=false. SHADOW MODE for rigs:
 * -Dbons_and_furious.terracurioAttributeMapSnapshot.shadow=true also asks the table on every snapshot answer and compares
 * (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20 mismatches; the table's answer is returned).
 */
public final class TerraAttributeSnapshot {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.terracurioAttributeMapSnapshot", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.terracurioAttributeMapSnapshot.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** The table the snapshot was copied from (null = nothing published). */
    private static volatile Hashtable<?, ?> source;
    private static volatile HashMap<Object, Object> snapshot;
    /** readJsonConfig calls in progress (guarded by the class monitor). */
    private static int active;
    private static volatile boolean announced;

    private TerraAttributeSnapshot() {
    }

    /** At the start of readJsonConfig: withdraw the snapshot before the table can change. */
    public static synchronized void enter() {
        active++;
        snapshot = null;
        source = null;
    }

    /** At a normal end of readJsonConfig: publish a copy of the table once no other call is in progress. */
    public static synchronized void leave(Hashtable<?, ?> map) {
        if (--active != 0 || map == null || !enabled) return;
        HashMap<Object, Object> copy;
        synchronized (map) {
            copy = new HashMap<>(map);
        }
        source = map;
        snapshot = copy;
    }

    /** In place of the getters' Hashtable.get(key). */
    public static Object get(Hashtable<?, ?> map, Object key, Operation<Object> original) {
        HashMap<Object, Object> s = snapshot;
        if (s == null || key == null || map != source || !enabled) return original.call(map, key);
        Object v = s.get(key);
        if (SHADOW) {
            Object theirs = original.call(map, key);
            SHADOW_CHECKS.incrementAndGet();
            if (!Objects.equals(theirs, v) || theirs != v) {
                long n = SHADOW_MISMATCHES.incrementAndGet();
                if (n <= 20) LOGGER.warn("Bons and Furious: terracurio_attribute_map_snapshot SHADOW MISMATCH {}: {} -> table {}, snapshot {}", n, key, theirs, v);
            }
            return theirs;
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: terracurio_attribute_map_snapshot applies (Terra Curio's attribute replacement lookups no longer lock the table)");
        }
        return v;
    }
}
