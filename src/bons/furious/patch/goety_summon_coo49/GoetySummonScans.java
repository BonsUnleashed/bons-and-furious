package bons.furious.patch.goety_summon_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49, group goety_summon_coo49 (server). Helper of the group's mixins; no code of Goety (MIT) or Goety
 * Cataclysm (all rights reserved) is carried: the logic is ours.
 *
 * <p>Goety's necromancer summon goals (AbstractNecromancer$SummonZombieSpell and the Cairn, Mossy, Wither and Drowned
 * necromancers' SummonServantSpell in Goety 2.5.60.0; AbstractDraugrNecromancer$SummonServantSpell in Goety Cataclysm
 * 1.20-1.9.2) answer canUse as {@code i = level.getEntitiesOfClass(LivingEntity, box grown by 64 / 16 / 64, alive and owned
 * by this necromancer).size(); return super.canUse() && i < N;}. The parent SummoningSpellGoal.canUse only reads (the
 * necromancer's target, whether it is alive and within 12 blocks, isSpellCasting, getSpellCooldown), and the search and its
 * predicate (isAlive, instanceof IOwned, getTrueOwner) only read too. So whenever the parent test answers false, which is
 * every check of an idle necromancer (no target, target too far, casting, cooldown), the count cannot change the answer:
 * the switch asks the parent test first and then marks the search UNUSED for vanilla_entity_walk_skip, which leaves out its
 * walk over the entity sections; the call itself runs (profiler count, Valkyrien Skies' box hooks, Forge's part entity pass)
 * and canUse answers false, as before. The goal's own super.canUse() call after the search is answered with the parent
 * test's answer asked before it (the mixins' second wrapper; nothing between the two reads writes), so the parent test runs
 * once per canUse, as shipped (asking it twice put a goal whose parent test answers true at 0.963 of
 * the shipped class). When it answers true, nothing is marked and the search runs as before.
 *
 * <p>Keys: goety_summon_goal_scan_after_check (Goety's five goals), goety_cataclysm_summon_goal_scan_after_check (the
 * Draugr necromancer's goal). Runtime flags (=false: off): bons_and_furious.goetySummonGoalScanAfterCheck,
 * bons_and_furious.goetyCataclysmSummonGoalScanAfterCheck. No shadow mode: the marked search's result cannot be read
 * (canUse answers false whatever it holds); proven offline on the real goals.
 */
public final class GoetySummonScans {
    public static final int GOETY = 0, CATACLYSM = 1;
    static final String[] KEYS = {"goety_summon_goal_scan_after_check", "goety_cataclysm_summon_goal_scan_after_check"};
    public static volatile boolean goety = flag("goetySummonGoalScanAfterCheck");
    public static volatile boolean cataclysm = flag("goetyCataclysmSummonGoalScanAfterCheck");
    /** Statistics (plain fields): searches marked unused, per key. */
    public static long markedGoety, markedCataclysm;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile int announced;

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
    public static List idle(int which, Level level, Class<?> searched, Object box, Object predicate, Operation<List> original) {
        if (!EntityPresenceLink.markUnused(KEYS[which], level, searched)) return original.call(level, searched, box, predicate);
        if (which == GOETY) markedGoety++;
        else markedCataclysm++;
        if ((announced & (1 << which)) == 0) {
            announced |= 1 << which;
            LOGGER.info("Bons and Furious: {}: an idle necromancer's summon check no longer walks the entities around it for a count it cannot use", KEYS[which]);
        }
        try {
            return original.call(level, searched, box, predicate);
        } finally {
            EntityPresenceLink.unmark();
        }
    }
}
