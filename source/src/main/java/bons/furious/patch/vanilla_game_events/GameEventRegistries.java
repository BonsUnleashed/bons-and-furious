package bons.furious.patch.vanilla_game_events;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_game_event_registry_lookup (Minecraft 1.21.1 with NeoForge 21.1.252; server side,
 * including the integrated server).
 *
 * What vanilla does. GameEventDispatcher.post delivers every game event (steps, landings, splashes, eating,
 * block changes, container use, projectile hits...) by asking every chunk section within the event's notification radius
 * (16 for most events: 3 x 3 chunks x 3 sections = 27 sections) for its listener registry, LevelChunk.getListenerRegistry
 * (getListenerRegistry), and visiting that registry's listeners. getListenerRegistry is computeIfAbsent on the chunk's per-section
 * map: the first event near an empty section creates an EuclideanGameEventListenerRegistry (with its three collections)
 * for it and keeps it; every later event visits that empty registry again. Registries only disappear when their last
 * listener unregisters, so sections that never had a listener keep an empty registry for as long as the chunk is loaded.
 *
 * What the switch does. Inside post only, the section's registry is read from the chunk's map without creating one; a
 * section without a registry is visited as GameEventListenerRegistry.NOOP (no listeners, returns false). Every other
 * caller of getListenerRegistry (block entities registering and unregistering their listeners, mods) still gets
 * vanilla's computeIfAbsent.
 *
 * Why the result is identical. A registry that post itself would have created is empty: visiting it iterates no
 * listener, toggles its processing flag back, finds both pending lists empty and returns false, exactly what NOOP
 * returns, and visits no listener. Creating or not creating it is not visible to listeners: register on a registry
 * created later behaves as on one created earlier (both empty, not processing), and unregister removes the map entry
 * either way once the registry is empty. Sections that have a registry are visited as before, in the same chunk and
 * section order, so listener visits, immediate deliveries, the BY_DISTANCE queue and the debug packet are unchanged.
 * Only LevelChunk itself is served this way (a subclass with its own getListenerRegistry keeps the original call), and
 * Valkyrien Skies' @WrapMethod around post still wraps the whole method (its own ship-space visit is untouched).
 *
 * -Dbons_and_furious.gameEventRegistryLookup=false switches it off at run time (vanilla computeIfAbsent).
 * -Dbons_and_furious.gameEventRegistryLookup.shadow=true (verification runs only): every lookup is also answered the
 * vanilla way afterwards and compared (an existing registry must be the one vanilla returns; a missing one must come back
 * empty from vanilla); SHADOW_CHECKS / SHADOW_MISMATCHES, the first 20 mismatches logged. In shadow mode the empty
 * registries are created as in vanilla.
 *
 * Ported to 1.21.1: the game event travels as a Holder<GameEvent> (post and visitInRangeListeners take the holder; the
 * radius is holder.value().notificationRadius()); the section loop, LevelChunk.getListenerRegistry's computeIfAbsent,
 * EuclideanGameEventListenerRegistry's visit/register/unregister and NOOP are unchanged, so the same argument holds.
 * Radium 0.13.1's optional mixin.world.game_events.dispatch (off by default) redirects the same call; the switch yields
 * to it (patches/vanilla_game_events.json).
 */
public final class GameEventRegistries {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.gameEventRegistryLookup", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.gameEventRegistryLookup.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong(), SHADOW_ABSENT = new AtomicLong();
    private static volatile boolean announced;

    /** Implemented by the LevelChunk accessor mixin: the chunk's per-section registry map (gameEventListenerRegistrySections). */
    public interface ChunkRegistries {
        Int2ObjectMap<GameEventListenerRegistry> bons$listenerRegistries();
    }

    private GameEventRegistries() {
    }

    /** GameEventDispatcher.post: its ChunkAccess.getListenerRegistry(sectionY) call (redirected). */
    public static GameEventListenerRegistry lookup(ChunkAccess chunk, int sectionY) {
        if (!enabled || chunk.getClass() != LevelChunk.class || SHADOW) {
            return slow(chunk, sectionY);
        }
        if (!announced) announce();
        GameEventListenerRegistry existing = ((ChunkRegistries) chunk).bons$listenerRegistries().get(sectionY);
        return existing != null ? existing : GameEventListenerRegistry.NOOP;
    }

    private static void announce() {
        announced = true;
        LOGGER.info("Bons and Furious: vanilla_game_event_registry_lookup applies (game events skip chunk sections without listeners instead of creating empty registries){}",
                SHADOW ? " - shadow verification on" : "");
    }

    private static final java.util.Set<Class<?>> OTHER_CHUNK_CLASSES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** The original call (switch off, another chunk class), or shadow mode. */
    private static GameEventListenerRegistry slow(ChunkAccess chunk, int sectionY) {
        if (!enabled) {
            return chunk.getListenerRegistry(sectionY);
        }
        if (chunk.getClass() != LevelChunk.class) {
            if (OTHER_CHUNK_CLASSES.add(chunk.getClass())) {
                LOGGER.info("Bons and Furious: vanilla_game_event_registry_lookup leaves the game-event registries of {} to its own lookup", chunk.getClass().getName());
            }
            return chunk.getListenerRegistry(sectionY);
        }
        if (!announced) announce();
        return shadow(chunk, sectionY, ((ChunkRegistries) chunk).bons$listenerRegistries().get(sectionY));
    }

    private static GameEventListenerRegistry shadow(ChunkAccess chunk, int sectionY, GameEventListenerRegistry existing) {
        GameEventListenerRegistry vanilla = chunk.getListenerRegistry(sectionY);
        SHADOW_CHECKS.incrementAndGet();
        if (existing != null) {
            if (existing != vanilla) mismatch("section " + sectionY + " of " + chunk.getPos() + ": the existing registry is not the one vanilla returns");
        } else {
            SHADOW_ABSENT.incrementAndGet();
            if (!vanilla.isEmpty()) mismatch("section " + sectionY + " of " + chunk.getPos() + ": no registry, but vanilla's has listeners");
        }
        return vanilla;
    }

    private static void mismatch(String what) {
        long m = SHADOW_MISMATCHES.incrementAndGet();
        if (m <= 20) LOGGER.warn("Bons and Furious: game event registry shadow mismatch #{}: {}", m, what);
    }
}
