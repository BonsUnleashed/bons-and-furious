package bons.furious.mixin.gtbcsgeomancy_coo49;

import bons.furious.patch.gtbcsgeomancy_coo49.GeomancyGates;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * gtbcs_geomancy_grandmaster_spawn_presence_gate (GTBC's Geomancy Plus 2.0.0, All Rights Reserved; server levels):
 * MixinExtras @WrapOperation on the ServerLevel.getEntitiesOfClass (m_45976_) call in
 * GeoGrandmasterEntity.checkGrandmasterSpawnRules (the grandmaster's spawn placement rule: no other grandmaster within 64
 * blocks): while vanilla_entity_class_count_layer says the level holds no GeoGrandmasterEntity, the search is marked EMPTY
 * for vanilla_entity_walk_skip (its walk over the entity sections of a 129-block cube is left out; the call itself and the
 * rest of the rule run). See GeomancyGates. Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "com.gametechbc.gtbcs_geomancy_plus.entity.mobs.geo_grandmaster.GeoGrandmasterEntity", remap = false)
public abstract class GeomancyGrandmasterSpawnGateMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "checkGrandmasterSpawnRules(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$gtbcsgeomancyCoo49GrandmasterScan(ServerLevel level, Class<?> searched, AABB box, Operation<List> original) {
        return GeomancyGates.grandmasterSearch(level, searched, box, original);
    }
}
