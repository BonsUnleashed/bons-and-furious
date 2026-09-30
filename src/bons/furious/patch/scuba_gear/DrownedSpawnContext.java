package bons.furious.patch.scuba_gear;

import com.legacy.scuba_gear.ScubaEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * scuba_gear_generation_context: the level a drowned is being spawned into, remembered per thread while Scuba Gear
 * decides the drowned's equipment.
 *
 * Scuba Gear's Mob.finalizeSpawn hook receives that ServerLevelAccessor but calls ScubaEvents.onDrownedSpawn(Drowned),
 * which only sees the entity, so it searched the live world's structures and drew from the live world's random even
 * while the drowned was created inside a WorldGenRegion on a Distant Horizons or C2ME worker thread. Bons and Furious
 * routes that call through {@link #onDrownedSpawn}: Scuba Gear's own method runs unchanged while the level is recorded
 * here, and ScubaEventsMixin reads it back for that same drowned when Scuba Gear asks for a structure manager or a
 * random source. Entries nest and are removed in a finally block.
 */
public final class DrownedSpawnContext {
    private static final ThreadLocal<DrownedSpawnContext> CURRENT = new ThreadLocal<>();

    private final Entity entity;
    private final ServerLevelAccessor level;
    private final DrownedSpawnContext outer;

    private DrownedSpawnContext(Entity entity, ServerLevelAccessor level, DrownedSpawnContext outer) {
        this.entity = entity;
        this.level = level;
        this.outer = outer;
    }

    /** Scuba Gear's ScubaEvents.onDrownedSpawn for a drowned that is being spawned into the given level. */
    public static void onDrownedSpawn(Drowned drowned, ServerLevelAccessor level) {
        enter(drowned, level);
        try {
            ScubaEvents.onDrownedSpawn(drowned);
        } finally {
            exit();
        }
    }

    /** Records the level the entity is being spawned into, until the matching {@link #exit()}. */
    public static void enter(Entity entity, ServerLevelAccessor level) {
        CURRENT.set(new DrownedSpawnContext(entity, level, CURRENT.get()));
    }

    /** Removes the innermost entry recorded by {@link #enter}. */
    public static void exit() {
        DrownedSpawnContext outer = CURRENT.get().outer;
        if (outer == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(outer);
        }
    }

    /** The level the entity is being spawned into, or null when this thread is not equipping that entity. */
    public static ServerLevelAccessor levelFor(Entity entity) {
        DrownedSpawnContext current = CURRENT.get();
        return current != null && current.entity == entity ? current.level : null;
    }
}
