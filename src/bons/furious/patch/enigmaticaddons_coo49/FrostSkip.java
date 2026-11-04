package bons.furious.patch.enigmaticaddons_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch enigmaticaddons_idle_frost_skip (Enigmatic Addons 1.2.7, both sides). Ours; no Enigmatic Addons
 * code is carried. Idea: Collections Of Optimizations (enigmaticaddons.skipIdleFrostScan), idea text only.
 *
 * Enigmatic Addons adds two per-tick freezing checks to every living entity on both sides:
 * (a) its MixinLivingEntity.tickMix handler (end of LivingEntity.tick) does "if (!canFreeze() && getTicksFrozen() > 0)
 *     setTicksFrozen(0)", so canFreeze() runs first for every entity, every tick: Minecraft's armour tag checks, Curios'
 *     freeze-immune curio walk and Enigmatic Addons' own canFreezeMix (an effect check, two curio lookups and an
 *     enchantment level);
 * (b) AddonEventHandler.onTick ends with the Frost Protection level of the entity and writes 0 frozen ticks (level 16 or
 *     more) or max(frozen ticks - 1, 0) (levels 1-15, some ticks).
 * With no frozen ticks the condition of (a) is false whatever canFreeze answers, and both writes of (b) set 0 on a 0, which
 * SynchedEntityData.set ignores (an equal value changes nothing). So, while the entity has no frozen ticks:
 * (a) canFreeze() is left out (true is passed on, which ends the condition where a true answer ends it) when that call can
 *     only compute a value: the entity's class does not override canFreeze; the canFreeze bodies of LivingEntity and
 *     Entity hold only Minecraft's own calls and handlers of the reviewed read-only mixins (EaPlugin's look at the merged
 *     classes, no mixin of another mod with priority 999999 or more on them); and Curios (curios_presence's quiet()) says
 *     a curio walk on the entity now would apply no pending slot update and only read;
 * (b) the enchantment level lookup (a read) is left out and 0 passed on, so the handler writes nothing.
 * Otherwise both run as shipped. Flag: -Dbons_and_furious.enigmaticaddonsIdleFrostSkip=false; shadow (still asks
 * canFreeze and checks that it left the frozen ticks and the Curios state as they were):
 * -Dbons_and_furious.enigmaticaddonsIdleFrostSkip.shadow=true.
 */
public final class FrostSkip {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.enigmaticaddonsIdleFrostSkip", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.enigmaticaddonsIdleFrostSkip.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong();
    public static final AtomicLong SHADOW_MISMATCHES = new AtomicLong();
    /** canFreeze calls and level lookups left out. */
    public static final AtomicLong CAN_FREEZE_SKIPS = new AtomicLong();
    public static final AtomicLong LEVEL_SKIPS = new AtomicLong();
    private static volatile boolean announced;
    /** "" once verified, else the reason (null = not decided yet). */
    private static volatile String verdict;
    private static final MethodHandle QUIET = quietHandle();

    private FrostSkip() {
    }

    private static MethodHandle quietHandle() {
        try {
            Class<?> c = Class.forName("bons.furious.patch.curios_presence.CuriosPresence", false, FrostSkip.class.getClassLoader());
            return MethodHandles.lookup().findStatic(c, "quiet", MethodType.methodType(boolean.class, LivingEntity.class));
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: enigmaticaddons_idle_frost_skip cannot reach curios_presence's quiet() ({}); canFreeze runs as shipped", t.toString());
            return null;
        }
    }

    private static final ClassValue<Boolean> PLAIN_CLASS = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("m_142079_").getDeclaringClass() == LivingEntity.class;
            } catch (Throwable t) {
                return Boolean.FALSE;
            }
        }
    };

    /** (a) The wrapped canFreeze() call of the tickMix handler. */
    public static boolean canFreeze(LivingEntity e, Operation<Boolean> original) {
        if (!enabled || e == null || e.m_146888_() != 0 || !PLAIN_CLASS.get(e.getClass()) || !verified() || !quiet(e)) return original.call(e);
        CAN_FREEZE_SKIPS.incrementAndGet();
        if (!announced) announce();
        if (SHADOW) {
            original.call(e);
            SHADOW_CHECKS.incrementAndGet();
            if ((e.m_146888_() != 0 || !quiet(e)) && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
                LOGGER.warn("Bons and Furious: enigmaticaddons_idle_frost_skip shadow mismatch: canFreeze changed the state of {}", e);
            }
        }
        return true;
    }

    /** (b) The wrapped Frost Protection level lookup of AddonEventHandler.onTick: true = leave it out (0). */
    public static boolean skipLevelLookup(LivingEntity e) {
        if (!enabled || e == null || e.m_146888_() != 0) return false;
        LEVEL_SKIPS.incrementAndGet();
        return true;
    }

    private static boolean quiet(LivingEntity e) {
        try {
            return QUIET != null && (boolean) QUIET.invokeExact(e);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean verified() {
        String v = verdict;
        if (v == null) v = verify();
        return v.isEmpty();
    }

    private static synchronized String verify() {
        if (verdict != null) return verdict;
        String why = EaPlugin.livingVerdict;
        if (why == null) why = "the look at LivingEntity did not run";
        if (why.isEmpty()) why = EaPlugin.entityVerdict == null ? "the look at Entity did not run" : EaPlugin.entityVerdict;
        if (why.isEmpty()) why = later("net/minecraft/world/entity/LivingEntity", EaPlugin.livingPriority);
        if (why.isEmpty()) why = later("net/minecraft/world/entity/Entity", EaPlugin.entityPriority);
        if (!why.isEmpty()) LOGGER.info("Bons and Furious: enigmaticaddons_idle_frost_skip asks canFreeze as shipped: {}", why);
        verdict = why;
        return why;
    }

    /**
     * "" when no mixin of another mod with priority at or above the look's is on {@code internal} (Bons and Furious' own
     * mixins are left out: none of them hooks canFreeze, checked by the group's offline proof over the whole source tree).
     */
    public static String later(String internal, int ours) {
        try {
            for (org.spongepowered.asm.mixin.extensibility.IMixinInfo mi : org.spongepowered.asm.mixin.transformer.ClassInfo
                    .forName(internal).getAppliedMixins()) {
                if (mi.getClassName().startsWith("bons.furious.mixin.")) continue;
                if (mi.getPriority() >= ours) return "the mixin " + mi.getClassName() + " (priority " + mi.getPriority() + ") is on " + internal;
            }
            return "";
        } catch (Throwable t) {
            return "the mixins applied to " + internal + " cannot be listed (" + t + ")";
        }
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: enigmaticaddons_idle_frost_skip applies (Enigmatic Addons' freezing checks are skipped for entities that are not freezing)");
    }

    /** For the offline proof: forget the decision. */
    public static void resetForProof() {
        verdict = null;
    }
}
