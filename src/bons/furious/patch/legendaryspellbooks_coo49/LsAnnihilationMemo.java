package bons.furious.patch.legendaryspellbooks_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.higurashi.legendary_spellbooks.config.CommonConfig;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.event.entity.living.LivingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch legendary_spellbooks_annihilation_bonus_memo (Legendary Spellbooks 0.4.1, LGPL-3.0; server levels;
 * exact). Ours; no Legendary Spellbooks code is carried. Idea: Collections Of Optimizations
 * (legendaryspellbooks.annihilationBonusInterval), idea text only; COO refreshes the bonus every 10 ticks instead of every 2
 * (a lag of up to half a second), this one skips exactly the runs that would change nothing.
 *
 * AnnihilationAttributeBonusEvent.onLivingTick runs on the living tick of every living entity; every second tick on a
 * server level it looks up three attribute instances (fire and ender spell power, annihilation spell power; RegistryObject
 * reads and AttributeMap.getInstance), returns if the entity has no annihilation instance, reads the fire and ender values
 * (AttributeInstance.getValue), the two config factors, finalValue = (fire - 1) * factor + (ender - 1) * factor, and the
 * annihilation instance's "Annihilation Bonus" modifier; only when that modifier is missing or its amount differs from
 * finalValue does it remove it and add a new one with finalValue (that is the run's only write).
 *
 * The memo (LsAnnihilationMemoMixin; per entity, on the entity through LsLivingMemoMixin) keeps, from the entity's last full
 * run, the three instances (recorded from the run's own three lookups), each instance's setDirty count (LsAttributeVersionMixin),
 * the two factors' bits, and whether the run ended with the modifier present at a number (finalValue not NaN). A later run is
 * skipped exactly when: the annihilation instance was absent last time (the original then returns after the three lookups);
 * or the annihilation instance saw no setDirty since (so its modifiers, the Annihilation Bonus among them, are unchanged), the
 * fire and ender instances saw no setDirty since and are not dirty (so getValue answers their unchanged cached value without
 * recomputing), the factors have the same bits, and the last run ended at a number. Then every read gives the answer of the
 * last full run, finalValue is the same number, the modifier holds it, and the original would write nothing: every step it
 * skips (registry reads, getInstance on an existing entry, getValue of a clean instance, config reads, getModifier) is a
 * read. AttributeMap.getInstance never replaces an instance it made (computeIfAbsent over a final map from a final supplier),
 * so the recorded instances are the ones the lookups would return; every base value, modifier, replaceFrom and load change
 * of an AttributeInstance goes through setDirty. In any other case the original runs as shipped and is recorded again.
 *
 * Flag: -Dbons_and_furious.legendarySpellbooksAnnihilationBonusMemo=false. SHADOW MODE (rigs):
 * -Dbons_and_furious.legendarySpellbooksAnnihilationBonusMemo.shadow=true runs the original wherever a run would be skipped
 * and checks that its lookups returned the recorded instances and that it changed no instance (SHADOW_CHECKS /
 * SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class LsAnnihilationMemo {
    public static final String KEY = "legendary_spellbooks_annihilation_bonus_memo";
    /** The modifier id the handler uses (its BONUS_UUID; the guard on its static initializer pins it). */
    static final UUID BONUS = UUID.fromString("d1c92506-bc83-41ed-ae4b-47b0b798a33e");

    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.legendarySpellbooksAnnihilationBonusMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.legendarySpellbooksAnnihilationBonusMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** For probes and harnesses (plain fields): runs skipped, full runs recorded. */
    public static long skipped, recorded;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private LsAnnihilationMemo() {
    }

    /** One entity's last full run. */
    static final class Memo {
        AttributeInstance fire, ender, annihilation;
        int fireVersion, enderVersion, annihilationVersion;
        long fireFactor, enderFactor;
        boolean recorded, settled;
    }

    static Memo memo(LivingEntity entity, boolean create) {
        if (!(entity instanceof LsAnnihilationHolder h)) return null;
        Object o = h.bons$legendaryspellbooksCoo49Memo();
        if (o instanceof Memo m) return m;
        if (!create) return null;
        Memo m = new Memo();
        h.bons$legendaryspellbooksCoo49Memo(m);
        return m;
    }

    static int version(AttributeInstance i) {
        return ((LsAttributeVersionDuck) i).bons$legendaryspellbooksCoo49Version();
    }

    static boolean dirty(AttributeInstance i) {
        return ((LsAttributeVersionDuck) i).bons$legendaryspellbooksCoo49Dirty();
    }

    static long fireFactor() {
        Object v = CommonConfig.ANNIHILATION_FIRE_BONUS.get();
        return Double.doubleToRawLongBits((Double) v);
    }

    static long enderFactor() {
        Object v = CommonConfig.ANNIHILATION_ENDER_BONUS.get();
        return Double.doubleToRawLongBits((Double) v);
    }

    /** True when the original run would read exactly what the last full run read and write nothing (see the class comment). */
    static boolean unchanged(Memo m) {
        if (!m.recorded || !m.settled) return false;
        AttributeInstance a = m.annihilation;
        if (a == null) return true;
        if (version(a) != m.annihilationVersion) return false;
        AttributeInstance f = m.fire, e = m.ender;
        if (f != null && (version(f) != m.fireVersion || dirty(f))) return false;
        if (e != null && (version(e) != m.enderVersion || dirty(e))) return false;
        return fireFactor() == m.fireFactor && enderFactor() == m.enderFactor;
    }

    /** The three lookups of the handler (ordinals 0, 1, 2): as shipped, the result kept for the memo while the switch is on. */
    public static AttributeInstance lookup(LivingEntity entity, Attribute attribute, Operation<AttributeInstance> original, int slot) {
        AttributeInstance found = original.call(entity, attribute);
        if (!enabled) return found;
        Memo m = memo(entity, true);
        if (m != null) {
            if (slot == 0) m.fire = found;
            else if (slot == 1) m.ender = found;
            else m.annihilation = found;
        }
        return found;
    }

    /** LsAnnihilationMemoMixin's @WrapMethod of the handler. */
    public static void tick(LivingEvent.LivingTickEvent event, Operation<Void> original) {
        LivingEntity entity = event == null ? null : event.getEntity();
        if (!enabled || entity == null) {
            original.call(event);
            return;
        }
        // the handler's own opening test: odd ticks and client levels end it after reads only
        if (entity.f_19797_ % 2 != 0 || entity.m_9236_().m_5776_()) return;
        Memo m = memo(entity, false);
        if (m != null && unchanged(m)) {
            if (SHADOW) {
                shadow(event, original, entity, m);
                return;
            }
            skipped++;
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: {} applies (the Annihilation Bonus is recomputed only when the fire or ender spell power or the bonus itself changed)", KEY);
            }
            return;
        }
        original.call(event);
        record(entity);
    }

    /** After a full run: keep what it read and how it ended. */
    static void record(LivingEntity entity) {
        Memo m = memo(entity, true);
        if (m == null) return;
        m.recorded = true;
        recorded++;
        AttributeInstance a = m.annihilation;
        if (a == null) {
            m.settled = true;
            return;
        }
        m.annihilationVersion = version(a);
        m.fireVersion = m.fire == null ? 0 : version(m.fire);
        m.enderVersion = m.ender == null ? 0 : version(m.ender);
        m.fireFactor = fireFactor();
        m.enderFactor = enderFactor();
        AttributeModifier bonus = a.m_22111_(BONUS);
        m.settled = bonus != null && !Double.isNaN(bonus.m_22218_());
    }

    /** Shadow mode: run the original where the memo would skip, check it read the recorded instances and changed nothing. */
    static void shadow(LivingEvent.LivingTickEvent event, Operation<Void> original, LivingEntity entity, Memo m) {
        AttributeInstance f = m.fire, e = m.ender, a = m.annihilation;
        int fv = f == null ? 0 : version(f), ev = e == null ? 0 : version(e), av = a == null ? 0 : version(a);
        AttributeModifier before = a == null ? null : a.m_22111_(BONUS);
        original.call(event);
        SHADOW_CHECKS.incrementAndGet();
        boolean same = m.fire == f && m.ender == e && m.annihilation == a
                && (f == null || version(f) == fv) && (e == null || version(e) == ev) && (a == null || version(a) == av)
                && (a == null || a.m_22111_(BONUS) == before);
        if (!same) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) LOGGER.warn("Bons and Furious: {} SHADOW MISMATCH {}: a skipped run of {} would have changed its annihilation bonus or read other instances", KEY, n, entity);
            record(entity);
        }
    }
}
