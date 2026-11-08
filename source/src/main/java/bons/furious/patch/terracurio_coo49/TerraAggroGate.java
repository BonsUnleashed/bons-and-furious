package bons.furious.patch.terracurio_coo49;

import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch terracurio_aggro_scan_solo_gate (Terra Curio 1.1.1 for NeoForge 1.21.1, org.confluence.terra_curio,
 * LGPL-3.0; server side). Ours; no Terra Curio code is carried. Idea: Collections Of Optimizations
 * (terracurio.skipIdleAggroScan), idea text only.
 *
 * <p>GameEvents.livingChangeTarget runs on every LivingChangeTargetEvent. When the mob changing target is an Enemy and the
 * new target (getNewAboutToBeSetTarget) is a Player, it reads the mob's follow range (getAttributeValue(FOLLOW_RANGE)),
 * then streams every player of the mob's level, keeps those within that range that the mob canAttack, takes the one with
 * the highest aggro and, only if that one is not the new target and has more aggro than it, makes it the new target.
 *
 * <p>skip() is asked right before that stream (TerraAggroSoloGateMixin injects before the handler's Level.players() call),
 * so the handler's own tests and its follow-range read have run as shipped (the same lazily created attribute instance, the
 * same exception for a mob without the attribute), and every event those tests end never reaches us. When the level's
 * player list holds exactly one player and that player IS the new target, the stream can only yield that player (or
 * nothing), and the consumer returns at once for it: the comparator is never called for one element, no aggro is read and
 * the target is never changed. The only work left is the filter's distance and canAttack calls, which change nothing:
 * vanilla's canAttack and its overrides only read, and Terra Curio 1.1.1 has no canAttack hook (its mob-ignoring accessory
 * value MOB$IGNORE works through TargetingConditions.test, which canAttack(LivingEntity) does not call).
 * skip() answers exactly that case; every other case runs the handler on.
 *
 * <p>Runtime flag: -Dbons_and_furious.terracurioAggroScanSoloGate=false.
 */
public final class TerraAggroGate {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.terracurioAggroScanSoloGate", "true"));
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** Statistics (plain, racy): handler runs answered by the gate. */
    public static long skipped;
    private static volatile boolean announced;

    private TerraAggroGate() {
    }

    /**
     * Asked right before the handler streams the level's players (the mob is an Enemy, the new target a Player, the follow
     * range read): true when the rest of the handler cannot change anything (see the class comment); the caller returns.
     */
    public static boolean skip(LivingChangeTargetEvent event) {
        if (!enabled) return false;
        LivingEntity self = event.getEntity();
        List<? extends Player> players = self.level().players();
        if (players.size() != 1 || players.get(0) != event.getNewAboutToBeSetTarget()) return false;
        skipped++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: terracurio_aggro_scan_solo_gate applies (Terra Curio's aggro retarget pass is left out while the target is the level's only player)");
        }
        return true;
    }
}
