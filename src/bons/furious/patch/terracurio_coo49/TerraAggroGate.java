package bons.furious.patch.terracurio_coo49;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch terracurio_aggro_scan_solo_gate (Terra Curio 0.2.0, org.confluence.terra_curio, LGPL-3.0;
 * server side). Ours; no Terra Curio code is carried. Idea: Collections Of Optimizations (terracurio.skipIdleAggroScan),
 * idea text only.
 *
 * <p>ForgeEvents.livingChangeTarget runs on every LivingChangeTargetEvent. When the mob changing target is an Enemy and the
 * new target is a Player, it reads the mob's follow range (getAttributeValue(FOLLOW_RANGE)), then streams every player of
 * the mob's level, keeps those within that range that the mob canAttack, takes the one with the highest aggro and, only if
 * that one is not the new target and has more aggro than it, makes it the new target.
 *
 * <p>skip() is asked right before that stream (TerraAggroSoloGateMixin injects before the handler's Level.players() call),
 * so the handler's own tests and its follow-range read have run as shipped (the same lazily created attribute instance, the
 * same exception for a mob without the attribute), and every event those tests end never reaches us. When the level's
 * player list holds exactly one player and that player IS the new target, the stream can only yield that player (or
 * nothing), and the consumer returns at once for it: the comparator is never called for one element, no aggro is read and
 * the target is never changed. The only work left is the filter's distance and canAttack calls, which change nothing
 * (vanilla's canAttack and Terra Curio's own canAttack hook only read; that hook walks the player's Curios for slimes only,
 * through RoyalGel.apply, and a Curios walk can apply a pending slot update: so for a slime the skip needs the shared Curios
 * layer to say the walk would only read). skip() answers exactly that case; every other case runs the handler on.
 *
 * <p>Runtime flag: -Dbons_and_furious.terracurioAggroScanSoloGate=false.
 */
public final class TerraAggroGate {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.terracurioAggroScanSoloGate", "true"));
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** Statistics (plain, racy): handler runs answered by the gate. */
    public static long skipped;
    private static final MethodHandle QUIET = find("bons.furious.patch.curios_presence.CuriosPresence", "quiet",
            MethodType.methodType(boolean.class, LivingEntity.class));
    private static volatile boolean announced;

    private TerraAggroGate() {
    }

    private static MethodHandle find(String owner, String name, MethodType type) {
        try {
            return MethodHandles.lookup().findStatic(Class.forName(owner, false, TerraAggroGate.class.getClassLoader()), name, type);
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.warn("Bons and Furious: terracurio_aggro_scan_solo_gate cannot reach {}.{} ({}); slimes' target changes run the stock code", owner, name, e.toString());
            return null;
        }
    }

    /** A Curios walk over the entity now would only read (the shared Curios layer's answer; false = unknown). */
    static boolean quiet(LivingEntity entity) {
        if (QUIET == null) return false;
        try {
            return (boolean) QUIET.invokeExact(entity);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Asked right before the handler streams the level's players (the mob is an Enemy, the new target a Player, the follow
     * range read): true when the rest of the handler cannot change anything (see the class comment); the caller returns.
     */
    public static boolean skip(LivingChangeTargetEvent event) {
        if (!enabled) return false;
        LivingEntity self = event.getEntity();
        List<? extends Player> players = self.m_9236_().m_6907_();
        if (players.size() != 1 || players.get(0) != event.getNewTarget()) return false;
        if (self instanceof Slime && !quiet(players.get(0))) return false;
        skipped++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: terracurio_aggro_scan_solo_gate applies (Terra Curio's aggro retarget pass is left out while the target is the level's only player)");
        }
        return true;
    }
}
