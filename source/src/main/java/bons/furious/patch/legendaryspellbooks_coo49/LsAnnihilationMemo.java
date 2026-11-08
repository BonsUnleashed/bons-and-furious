package bons.furious.patch.legendaryspellbooks_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch legendary_spellbooks_annihilation_bonus_memo (Legendary Spellbooks 0.3.2 for Minecraft 1.21.1,
 * LGPL-3.0; server levels; exact). Ours; no Legendary Spellbooks code is carried. Idea: Collections Of Optimizations
 * (legendaryspellbooks.annihilationBonusInterval), idea text only; COO refreshes the bonus every 10 ticks instead of every 2
 * (a lag of up to half a second), this one skips exactly the runs that would change nothing.
 *
 * AnnihilationAttributeBonusEvent.onLivingTick listens to NeoForge's EntityTickEvent.Post; for a living entity, every second
 * tick on a server level, it looks up three attribute instances (fire and ender spell power, annihilation spell power;
 * LivingEntity.getAttribute(Holder), i.e. AttributeMap.getInstance), returns if the entity has no annihilation instance,
 * reads the fire and ender values (AttributeInstance.getValue), finalValue = (max(0, fire - 1) + max(0, ender - 1)) / 2, and
 * the annihilation instance's legendary_spellbooks:annihilation_bonus modifier; only when that modifier is missing or its
 * amount differs from finalValue does it remove it and add a new transient one with finalValue (that is the run's only
 * write).
 *
 * The memo (LsAnnihilationMemoMixin; per entity, on the entity through LsLivingMemoMixin) keeps, from the entity's last full
 * run, the three instances (recorded from the run's own three lookups), each instance's setDirty count (LsAttributeVersionMixin)
 * and whether the run ended with the modifier present at a number (finalValue not NaN). A later run is skipped exactly when:
 * the annihilation instance was absent last time (the original then returns after the three lookups); or the annihilation
 * instance saw no setDirty since (so its modifiers, the bonus among them, are unchanged), the fire and ender instances saw
 * no setDirty since and are not dirty (so getValue answers their unchanged cached value without recomputing), and the last
 * run ended at a number. Then every read gives the answer of the last full run, finalValue is the same number, the modifier
 * holds it, and the original would write nothing: every step it skips (holder reads, getInstance on an existing entry,
 * getValue of a clean instance, getModifier) is a read. AttributeMap.getInstance never replaces an instance it made
 * (computeIfAbsent over a final map from a final supplier), so the recorded instances are the ones the lookups would
 * return; every base value, modifier, replaceFrom and load change of an AttributeInstance goes through setDirty. In any
 * other case the original runs as shipped and is recorded again. Entities that are not living entities run the original
 * (its own first test ends it).
 *
 * Flag: -Dbons_and_furious.legendarySpellbooksAnnihilationBonusMemo=false. SHADOW MODE (rigs):
 * -Dbons_and_furious.legendarySpellbooksAnnihilationBonusMemo.shadow=true runs the original wherever a run would be skipped
 * and checks that its lookups returned the recorded instances and that it changed no instance (SHADOW_CHECKS /
 * SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class LsAnnihilationMemo {
    public static final String KEY = "legendary_spellbooks_annihilation_bonus_memo";
    /** The modifier id the handler uses (LegendarySpellbooks.id("annihilation_bonus"); its guard pins id()). */
    static final ResourceLocation BONUS = ResourceLocation.fromNamespaceAndPath("legendary_spellbooks", "annihilation_bonus");

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

    /** True when the original run would read exactly what the last full run read and write nothing (see the class comment). */
    static boolean unchanged(Memo m) {
        if (!m.recorded || !m.settled) return false;
        AttributeInstance a = m.annihilation;
        if (a == null) return true;
        if (version(a) != m.annihilationVersion) return false;
        AttributeInstance f = m.fire, e = m.ender;
        if (f != null && (version(f) != m.fireVersion || dirty(f))) return false;
        return e == null || version(e) == m.enderVersion && !dirty(e);
    }

    /** The three lookups of the handler (ordinals 0, 1, 2): as shipped, the result kept for the memo while the switch is on. */
    public static AttributeInstance lookup(LivingEntity entity, Holder<Attribute> attribute, Operation<AttributeInstance> original, int slot) {
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
    public static void tick(EntityTickEvent.Post event, Operation<Void> original) {
        Entity e = event == null ? null : event.getEntity();
        if (!enabled || !(e instanceof LivingEntity entity)) {
            original.call(event);
            return;
        }
        // the handler's own opening test: odd ticks and client levels end it after reads only
        if (entity.tickCount % 2 != 0 || entity.level().isClientSide()) return;
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
        AttributeModifier bonus = a.getModifier(BONUS);
        m.settled = bonus != null && !Double.isNaN(bonus.amount());
    }

    /** Shadow mode: run the original where the memo would skip, check it read the recorded instances and changed nothing. */
    static void shadow(EntityTickEvent.Post event, Operation<Void> original, LivingEntity entity, Memo m) {
        AttributeInstance f = m.fire, e = m.ender, a = m.annihilation;
        int fv = f == null ? 0 : version(f), ev = e == null ? 0 : version(e), av = a == null ? 0 : version(a);
        AttributeModifier before = a == null ? null : a.getModifier(BONUS);
        original.call(event);
        SHADOW_CHECKS.incrementAndGet();
        boolean same = m.fire == f && m.ender == e && m.annihilation == a
                && (f == null || version(f) == fv) && (e == null || version(e) == ev) && (a == null || version(a) == av)
                && (a == null || a.getModifier(BONUS) == before);
        if (!same) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) LOGGER.warn("Bons and Furious: {} SHADOW MISMATCH {}: a skipped run of {} would have changed its annihilation bonus or read other instances", KEY, n, entity);
            record(entity);
        }
    }
}
