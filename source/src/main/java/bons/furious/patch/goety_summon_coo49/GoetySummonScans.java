package bons.furious.patch.goety_summon_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49, group goety_summon_coo49 (server; NeoForge 1.21.1). Helper of the group's mixin; no code of
 * Goety (MIT) or Goety Cataclysm (all rights reserved) is carried: the logic is ours.
 *
 * <p>Goety Cataclysm 1.21.1-1.8.4's AbstractDraugrNecromancer$SummonServantSpell answers canUse as {@code i =
 * level.getEntitiesOfClass(LivingEntity, box grown by 64 / 16 / 64, alive and owned by this necromancer).size(); return
 * super.canUse() && i < 6;}. The parent SummoningSpellGoal.canUse (Goety 3.2.2) only reads (the necromancer's target,
 * whether it is alive and within 12 blocks, isSpellCasting, getSpellCooldown), and the search and its predicate (isAlive,
 * instanceof IOwned, getTrueOwner) only read too. So whenever the parent test answers false, which is every check of an
 * idle necromancer (no target, target too far, casting, cooldown), the count cannot change the answer: the switch asks the
 * parent test first and then marks the search UNUSED for vanilla_entity_walk_skip, which leaves out its walk over the
 * entity sections; the call itself runs (profiler count, Valkyrien Skies' box hooks, NeoForge's part entity pass) and
 * canUse answers false, as before. The goal's own super.canUse() call after the search is answered with the parent test's
 * answer asked before it (the mixin's second wrapper; nothing between the two reads writes), so the parent test runs once
 * per canUse, as shipped. When it answers true, nothing is marked and the search runs as before. (Goety 3.2.2's own
 * necromancer goals already ask the parent test first and count through remainingOwnedSummonSlots only after it.)
 *
 * <p>Key: goety_cataclysm_summon_goal_scan_after_check. Runtime flag (=false: off):
 * bons_and_furious.goetyCataclysmSummonGoalScanAfterCheck. No shadow mode: the marked search's result cannot be read
 * (canUse answers false whatever it holds).
 */
public final class GoetySummonScans {
    static final String KEY = "goety_cataclysm_summon_goal_scan_after_check";
    public static volatile boolean cataclysm = flag("goetyCataclysmSummonGoalScanAfterCheck");
    /** Statistics (plain field): searches marked unused. */
    public static long markedCataclysm;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private GoetySummonScans() {
    }

    private static boolean flag(String name) {
        return !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + name, "true"));
    }

    /**
     * The summon goal's search after SummoningSpellGoal.canUse() answered false (asked just before by the mixin, only while
     * its flag is on): marked UNUSED, then run. The mixin runs the search itself when the parent test answers true.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List idle(Level level, Class<?> searched, Object box, Object predicate, Operation<List> original) {
        if (!EntityPresenceLink.markUnused(KEY, level, searched)) return original.call(level, searched, box, predicate);
        markedCataclysm++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {}: an idle Draugr Necromancer's summon check no longer walks the entities around it for a count it cannot use", KEY);
        }
        try {
            return original.call(level, searched, box, predicate);
        } finally {
            EntityPresenceLink.unmark();
        }
    }
}
