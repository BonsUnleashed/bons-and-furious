package bons.furious.patch.traveloptics_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch traveloptics_caster_effect_precheck (T.O Magic 'n Extras 6.3.0, All Rights Reserved; both sides;
 * exact). Ours; no T.O code is carried. Idea: Collections Of Optimizations (traveloptics.leanCastEffectChecks), idea text
 * only.
 *
 * What the mod does. BlackoutHandler.onEntityCast and CastingHandler.onEntityCast run on Forge's living tick for every
 * living entity on both sides. Each first stores entity.hasEffect(its effect) and only then asks "instanceof
 * AbstractSpellCastingMob" (Iron's Spells' caster mobs): the stored answer is read only inside that branch (a caster on a
 * server level with the effect has its cast cancelled). For every other entity the answer is never read.
 *
 * What the switch does. Both hasEffect calls are wrapped (ToBlackoutCastPrecheckMixin, ToCastingCastPrecheckMixin): for an
 * entity that is not an AbstractSpellCastingMob the call is not made and false stands in; for a caster mob (and a null
 * entity, which throws as before) the original call. hasEffect is a read of the entity's effect map (a mod that injects into
 * it changes only its answer, which the handler never reads for these entities), so nothing else changes; the effect's
 * registry entry is still read first, as shipped. No counter on this hot path (the harness counts the effect map's lookups
 * instead).
 *
 * Runtime flag: -Dbons_and_furious.travelopticsCasterEffectPrecheck=false.
 */
public final class ToCasterEffects {
    public static final String KEY = "traveloptics_caster_effect_precheck";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.travelopticsCasterEffectPrecheck", "true"));

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private ToCasterEffects() {
    }

    /** entity.hasEffect(effect) in a T.O onEntityCast handler, wrapped. */
    public static boolean hasEffect(LivingEntity entity, MobEffect effect, Operation<Boolean> original) {
        if (!enabled || entity == null || entity instanceof AbstractSpellCastingMob) return original.call(entity, effect);
        if (!announced) announce();
        return false;
    }

    private static void announce() {
        announced = true;
        LOGGER.info("Bons and Furious: {} applies (T.O's Blackout and Casting tick handlers read the effect only for spell-casting mobs)", KEY);
    }
}
