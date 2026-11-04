package bons.furious.patch.brutality_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.goo.brutality.entity.projectile.generic.BlackHole;
import net.goo.brutality.entity.projectile.ray.ExplosionRay;
import net.goo.brutality.registry.BrutalityModEntities;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch brutality_proximity_scan_presence_gate (Brutality 0.8.5, MIT; client; since 1.0.49). Idea:
 * Collections Of Optimizations (brutality.proximityScanInterval), idea text only.
 *
 * ForgeClientPlayerStateHandler.onClientTick (END of every client tick) streams over EVERY entity of the client level
 * (ClientLevel.entitiesForRendering) up to three times: walk 1 "an entity of type BLACK_HOLE_ENTITY within 10 blocks"
 * (when the black hole sky colour option is on), walk 2 "a Player holding the Blade of the Ruined King within 10 blocks"
 * (bork sky colour option), walk 3 "an entity of type EXPLOSION_RAY within 50 blocks" (always); each answer goes to
 * EnvironmentColorManager.apply. Each walk is an anyMatch with a pure test (type compare, isHolding, distance).
 *
 * What changes (the entitiesForRendering call of each walk is wrapped; the stream, the test and every colour-manager call
 * run as shipped over what the call answers):
 * - walks 1 and 3: while vanilla_entity_class_count_layer says the level's entity sections hold no BlackHole (no
 *   ExplosionRay), the call answers an empty list, so anyMatch answers false without a walk. Every entity of those types is
 *   an instance of that class (the types are built with BlackHole::new / ExplosionRay::new, Brutality creates both with
 *   their own constructors, a client creates them from the type), and the client level's lookup holds exactly the
 *   entities of its sections, so no entity can pass the test.
 * - walk 2: while the layer's Player count equals ClientLevel.players().size() (every Player of the level is in that list;
 *   a Player that is not an AbstractClientPlayer would make the counts differ), the call answers players(): the test can
 *   only pass for a Player, and it is pure, so anyMatch over the players gives the walk's answer.
 * Otherwise (counts unknown, layer off, another thread) the call is made as shipped.
 *
 * -Dbons_and_furious.brutalityProximityScanPresenceGate=false runs every walk as shipped; shadow mode
 * (-Dbons_and_furious.brutalityProximityScanPresenceGate.shadow=true) never changes an answer: it walks as shipped and checks
 * the layer's answer against the level's entities (SHADOW_CHECKS / SHADOW_MISMATCHES, at most 20 WARN lines).
 */
public final class BrutalityProximity {
    public static final String KEY = "brutality_proximity_scan_presence_gate";
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.brutalityProximityScanPresenceGate", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.brutalityProximityScanPresenceGate.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong();
    public static final AtomicLong SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;

    /** The layer's indices of this group's registered classes, resolved on first use (-1 = not counted here). */
    private static final class Idx {
        static final int BLACK_HOLE = EntityPresenceLink.trackedIndex("net.goo.brutality.entity.projectile.generic.BlackHole");
        static final int RAY = EntityPresenceLink.trackedIndex("net.goo.brutality.entity.projectile.ray.ExplosionRay");
        static final int PLAYER = EntityPresenceLink.trackedIndex("net.minecraft.world.entity.player.Player");
    }

    private BrutalityProximity() {
    }

    /** Walk 1 (ray = false: black holes) or walk 3 (ray = true: explosion rays): the wrapped entitiesForRendering call. */
    public static Iterable<Entity> typeWalk(ClientLevel level, boolean ray, Operation<Iterable<Entity>> original) {
        if (!enabled || !EntityPresenceLink.none(level, ray ? Idx.RAY : Idx.BLACK_HOLE)) return original.call(level);
        if (SHADOW) {
            Iterable<Entity> all = original.call(level);
            for (Entity e : all) {
                SHADOW_CHECKS.incrementAndGet();
                boolean hit = ray ? e instanceof ExplosionRay || e.m_6095_() == BrutalityModEntities.EXPLOSION_RAY.get()
                        : e instanceof BlackHole || e.m_6095_() == BrutalityModEntities.BLACK_HOLE_ENTITY.get();
                if (hit) mismatch((ray ? "an explosion ray " : "a black hole ") + e + " while the layer counted none");
            }
            return all;
        }
        announce();
        return Collections.emptyList();
    }

    /** Walk 2 (players holding the Blade of the Ruined King): the wrapped entitiesForRendering call. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Iterable<Entity> playerWalk(ClientLevel level, Operation<Iterable<Entity>> original) {
        if (!enabled) return original.call(level);
        List<AbstractClientPlayer> players = level.m_6907_();
        int n = EntityPresenceLink.count(level, Idx.PLAYER);
        if (n < 0 || n != players.size()) return original.call(level);
        if (SHADOW) {
            Iterable<Entity> all = original.call(level);
            Map<Object, Boolean> inList = new IdentityHashMap<>();
            for (AbstractClientPlayer p : players) inList.put(p, Boolean.TRUE);
            int found = 0;
            for (Entity e : all) {
                if (!(e instanceof Player)) continue;
                SHADOW_CHECKS.incrementAndGet();
                found++;
                if (!inList.containsKey(e)) mismatch("player " + e + " is not in ClientLevel.players() while the counts matched");
            }
            if (found != players.size()) mismatch("the level holds " + found + " players, players() " + players.size());
            return all;
        }
        announce();
        return (Iterable) players;
    }

    private static void mismatch(String what) {
        if (SHADOW_MISMATCHES.incrementAndGet() <= 20) LOGGER.warn("Bons and Furious: {} shadow mismatch: {}", KEY, what);
    }

    private static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (Brutality's sky colour proximity walks skip the level's entities while no black hole or explosion ray exists, and look at its players only)", KEY);
        }
    }
}
