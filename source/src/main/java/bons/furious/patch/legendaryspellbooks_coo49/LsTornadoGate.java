package bons.furious.patch.legendaryspellbooks_coo49;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.entity.PartEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch legendary_spellbooks_tornado_presence_gate (Legendary Spellbooks 0.3.2 for Minecraft 1.21.1,
 * LGPL-3.0; the Tornado is Legendary Monsters 2.2.3's; server levels; exact). Ours; no Legendary Spellbooks or Legendary
 * Monsters code is carried. Idea: Collections Of Optimizations (legendaryspellbooks.leanTornadoScan), idea text only; COO
 * skips the search while no tornado ticked in the last tick with a once-a-second safety net, this one is exact and needs no
 * net.
 *
 * TornadoEntityHandler.onLivingUpdate listens to NeoForge's EntityTickEvent.Pre, so it runs on the tick of every entity on
 * server levels (items and projectiles included): it grows the entity's box by 2 and, unless the entity rides a Tornado,
 * asks the level for every Tornado inside it (Level.getEntitiesOfClass: a walk over every entity section the box touches,
 * plus a new box, type test and list), then sets or clears the entity's in_tornado tag from the answer. While
 * vanilla_entity_class_count_layer says the level holds no Tornado (registration
 * bons_and_furious.legendaryspellbooks_coo49.entity_classes.json: Tornado and its subclasses, counted only while Legendary
 * Spellbooks is installed and this switch is on), the search is marked EMPTY: vanilla_entity_walk_skip leaves out its walk
 * over the entity sections, which could visit no Tornado. The call itself still runs (the profiler's "getEntities" count,
 * NeoForge's part-entity pass, every other mod's hook on the search) and returns the new empty list it would have
 * returned; the vehicle test before it and the in_tornado tag logic after it run unchanged. Only searches for exactly the
 * Tornado class are marked; the gate answers "unknown" (the original runs) whenever the layer does (counts doubtful,
 * another thread, a layer absent or off). Flag: -Dbons_and_furious.legendarySpellbooksTornadoPresenceGate=false.
 * SHADOW MODE (rigs): -Dbons_and_furious.legendarySpellbooksTornadoPresenceGate.shadow=true never marks; where the gate
 * would act it runs the search in full and checks that it found no Tornado (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the
 * first 20).
 */
public final class LsTornadoGate {
    public static final String KEY = "legendary_spellbooks_tornado_presence_gate";
    public static final String TORNADO = "net.miauczel.legendary_monsters.entity.AnimatedMonster.Projectile.Tornado";

    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.legendarySpellbooksTornadoPresenceGate", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.legendarySpellbooksTornadoPresenceGate.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Statistics for probes (plain field): tornado searches marked EMPTY. */
    public static long markedEmpty;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile int announced;
    /** The class the search names, remembered by identity after one check by name. */
    private static volatile Class<?> seen;

    private LsTornadoGate() {
    }

    /** The layer's index of Tornado, resolved on first use (the layer reads its registration files when it initialises). */
    private static final class Index {
        static final int TORNADO_INDEX = EntityPresenceLink.trackedIndex(TORNADO);
    }

    private static void announce(int bit, String what) {
        synchronized (LsTornadoGate.class) {
            if ((announced & (1 << bit)) != 0) return;
            announced |= 1 << bit;
        }
        LOGGER.info("Bons and Furious: {}: {}", KEY, what);
    }

    private static boolean names(Class<?> searched) {
        if (searched == null) return false;
        if (searched == seen) return true;
        if (!TORNADO.equals(searched.getName())) return false;
        seen = searched;
        return true;
    }

    /** LsTornadoScanMixin (a @Redirect): the handler's Tornado search, marked EMPTY while the level holds no Tornado. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List tornadoSearch(Level level, Class searched, AABB box) {
        if (!enabled || !names(searched)) return level.getEntitiesOfClass(searched, box);
        int index = Index.TORNADO_INDEX;
        if (index < 0) return level.getEntitiesOfClass(searched, box);
        if (SHADOW) {
            if (!EntityPresenceLink.none(level, index)) return level.getEntitiesOfClass(searched, box);
            announce(0, "SHADOW MODE: the tornado searches of the entity tick run in full and are checked");
            List found = level.getEntitiesOfClass(searched, box);
            SHADOW_CHECKS.incrementAndGet();
            for (Object e : found) {
                if (e instanceof PartEntity) continue;
                long n = SHADOW_MISMATCHES.incrementAndGet();
                if (n <= 20) LOGGER.warn("Bons and Furious: {} SHADOW MISMATCH {}: the layer says {} holds no tornado, the search found {}", KEY, n, level, e);
                break;
            }
            return found;
        }
        if (!EntityPresenceLink.markEmpty(KEY, level, searched, index)) return level.getEntitiesOfClass(searched, box);
        markedEmpty++;
        if ((announced & 2) == 0) announce(1, "the entity tick's tornado search walks no entity sections while the level holds no tornado");
        try {
            return level.getEntitiesOfClass(searched, box);
        } finally {
            EntityPresenceLink.unmark();
        }
    }
}
