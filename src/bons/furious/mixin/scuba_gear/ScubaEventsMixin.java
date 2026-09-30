package bons.furious.mixin.scuba_gear;

import bons.furious.patch.scuba_gear.DrownedSpawnContext;
import com.legacy.scuba_gear.ScubaEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * scuba_gear_generation_context (Scuba Gear 1.0.6), part 1 of 2; part 2 is MobFinalizeSpawnMixin.
 *
 * When a drowned spawns near an ocean ruin or shipwreck, Scuba Gear may equip it. The structure search and the random
 * draw used the live world even while the drowned was being created inside a generation region, which stalled the
 * server under Distant Horizons and crashed C2ME workers. Scuba Gear's code stays as it is: the two redirects below
 * only swap the structure manager and the random source, and only for a drowned whose spawn level DrownedSpawnContext
 * recorded (a WorldGenRegion makes them region-bound, anything else leaves them as before).
 */
@Mixin(value = ScubaEvents.class, remap = false)
public abstract class ScubaEventsMixin {
    @Shadow
    private static boolean isRuinOrWreckNearby(Entity entity) {
        throw new AssertionError();
    }

    /**
     * Scuba Gear's onDrownedSpawn with the level the drowned spawns into. Added under the 1.0.19 name and descriptor;
     * Mixin 0.8.5 merges no non-private static method, so it is private here and the public entry point is
     * DrownedSpawnContext.onDrownedSpawn, which the finalizeSpawn hook calls.
     */
    @Unique
    private static void onDrownedSpawn(Drowned drowned, ServerLevelAccessor serverLevelAccessor) {
        DrownedSpawnContext.onDrownedSpawn(drowned, serverLevelAccessor);
    }

    /** Scuba Gear's structure search around the entity, for an entity that spawns into the given level. */
    @Unique
    private static boolean isRuinOrWreckNearby(Entity entity, ServerLevelAccessor serverLevelAccessor) {
        DrownedSpawnContext.enter(entity, serverLevelAccessor);
        try {
            return isRuinOrWreckNearby(entity);
        } finally {
            DrownedSpawnContext.exit();
        }
    }

    /** The structure manager to search: bound to the generation region when the drowned spawns into one. */
    @Unique
    private static StructureManager ac$spawnStructureManager(ServerLevel serverLevel, ServerLevelAccessor serverLevelAccessor) {
        StructureManager structureManager = serverLevel.m_215010_();
        if (serverLevelAccessor instanceof WorldGenRegion) {
            return structureManager.m_220468_((WorldGenRegion) serverLevelAccessor);
        }
        return structureManager;
    }

    /** The random source for the equipment roll: the generation region's own random when the drowned spawns into one. */
    @Unique
    private static RandomSource ac$spawnRandom(Drowned drowned, ServerLevelAccessor serverLevelAccessor) {
        if (serverLevelAccessor instanceof WorldGenRegion) {
            return ((WorldGenRegion) serverLevelAccessor).m_213780_();
        }
        return drowned.m_9236_().f_46441_;
    }

    /** Each of the four structure lookups in isRuinOrWreckNearby asks the level-aware structure manager. */
    @Redirect(method = "isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;m_215010_()Lnet/minecraft/world/level/StructureManager;"))
    private static StructureManager bons$spawnStructureManager(ServerLevel world, Entity entity) {
        return ac$spawnStructureManager(world, DrownedSpawnContext.levelFor(entity));
    }

    /** The equipment roll in onDrownedSpawn reads the region's random during generation, the world's random otherwise. */
    @Redirect(method = "onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/Level;f_46441_:Lnet/minecraft/util/RandomSource;", opcode = Opcodes.GETFIELD))
    private static RandomSource bons$spawnRandom(Level level, Drowned drowned) {
        ServerLevelAccessor spawnLevel = DrownedSpawnContext.levelFor(drowned);
        return spawnLevel instanceof WorldGenRegion ? ac$spawnRandom(drowned, spawnLevel) : level.f_46441_;
    }
}
