package bons.furious.patch.mutantmonsters_c49;

import bons.furious.mixin.mutantmonsters_c49.LevelEntityGetterAdapterAccessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch mutantmonsters_spawn_limit_index (Mutant Monsters; 1.21.1 tested build: Mutant Monsters v21.1.1 for
 * NeoForge 1.21.1; server; since 1.0.49). Idea: Collections Of Optimizations (mutantmonsters.leanSpawnLimitScan), idea text
 * only. Our own logic; no Mutant Monsters code (AGPL-3.0 target, injectors only).
 *
 * SpawningPreventionHandler.onEntitySpawn caps body parts (128), endersoul fragments (128) and creeper minion eggs (32) per
 * level: for every spawn of such a type it streams ServerLevel.getAllEntities() (EntityLookup.byId, every entity of the
 * dimension) and counts the entities of the same type.
 *
 * What changes: the getAllEntities() call of that handler answers the entities of the spawning entity's type, plus the
 * entities whose class has its own getType, from the level's TypeCount (kept in step with byId by every add and remove,
 * rebuilt from byId whenever it lost step). Mutant Monsters' own filter and count run over them: an entity of byId has
 * the type or it does not, and those without the type pass no element the filter would have counted, so the count and
 * the spawn decision are stock's. Removals inside the same tick count at once, as in stock.
 *
 * -Dbons_and_furious.mutantmonstersSpawnLimitIndex=false walks every entity (stock). .shadow=true runs stock's walk too,
 * returns it (stock behaviour) and counts every query whose count differs.
 */
public final class SpawnLimitIndex {
    public static final String KEY = "mutantmonsters_spawn_limit_index";
    public static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mutantmonstersSpawnLimitIndex", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.mutantmonstersSpawnLimitIndex.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;

    private SpawnLimitIndex() {
    }

    /** The wrapped ServerLevel.getAllEntities() of SpawningPreventionHandler.onEntitySpawn. */
    public static Iterable<?> entities(ServerLevel level, Entity spawning, Operation<Iterable<?>> original) {
        if (!enabled || !(level.getEntities() instanceof LevelEntityGetterAdapterAccessor adapter)
                || !(adapter.bons$mutantVisibleEntities() instanceof TypeCountHolder holder)) return original.call(level);
        Iterable<Object> ours = holder.bons$mutantTypeCount(true).query(spawning.getType(), holder.bons$mutantById());
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (Mutant Monsters' spawn limits count from a per-type index instead of walking every entity)", KEY);
        }
        if (SHADOW) {
            Iterable<?> stock = original.call(level);
            long a = count(stock, spawning), b = count(ours, spawning);
            SHADOW_CHECKS.incrementAndGet();
            if (a != b && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                LOGGER.warn("Bons and Furious: {} shadow mismatch for {}: stock counts {}, the index {}", KEY, spawning.getType(), a, b);
            return stock;
        }
        return ours;
    }

    /** The count Mutant Monsters takes over an iterable: entities with the spawning entity's type. */
    public static long count(Iterable<?> it, Entity spawning) {
        long n = 0;
        for (Object o : it) if (((Entity) o).getType() == spawning.getType()) n++;
        return n;
    }
}
