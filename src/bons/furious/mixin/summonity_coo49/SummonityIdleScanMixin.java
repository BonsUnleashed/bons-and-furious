package bons.furious.mixin.summonity_coo49;

import bons.furious.patch.summonity_coo49.IdleScans;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * summonity_idle_target_scan_interval (Summonity 0.1.7.1, server side; change, off by default, value line; All Rights
 * Reserved: no code of the mod is carried). The target choice of 20 summoned minion classes (getActiveTarget /
 * findTarget) returns the commanded target or the current valid target first and only then, for an idle minion, searches
 * every Mob in the owner's minion range (Level.getEntitiesOfClass with the minion's own predicate) and takes the nearest.
 * MixinExtras @WrapOperation on that search call: with the switch on, the search runs only on the minion's scan ticks
 * ((game time + entity id) a multiple of N, IdleScans) and answers an empty list on the other ticks, so the idle minion
 * keeps no target until its next scan tick. What differs: an idle minion reacts to a new target up to N-1 ticks later.
 */
@Mixin(targets = {
        "com.fevzi.summonity.entity.SummonedCherryBlossomSwarmEntity", "com.fevzi.summonity.entity.SummonedCopperGolemEntity",
        "com.fevzi.summonity.entity.SummonedCrystalWispEntity", "com.fevzi.summonity.entity.SummonedGnomeEntity",
        "com.fevzi.summonity.entity.SummonedImpEntity", "com.fevzi.summonity.entity.SummonedMagicMirrorEntity",
        "com.fevzi.summonity.entity.SummonedMiniatureSunEntity", "com.fevzi.summonity.entity.SummonedParrotEntity",
        "com.fevzi.summonity.entity.SummonedPrismaticLanternEntity", "com.fevzi.summonity.entity.SummonedSanguineBatEntity",
        "com.fevzi.summonity.entity.SummonedSculkMiteEntity", "com.fevzi.summonity.entity.SummonedShulklingEntity",
        "com.fevzi.summonity.entity.SummonedSimpleTurretEntity", "com.fevzi.summonity.entity.SummonedSlimeEntity",
        "com.fevzi.summonity.entity.SummonedSpiderEntity", "com.fevzi.summonity.entity.SummonedStardustDragonEntity",
        "com.fevzi.summonity.entity.SummonedTarotCardEntity", "com.fevzi.summonity.entity.SummonedUltraSnifferEntity",
        "com.fevzi.summonity.entity.SummonedVirentiumCubeEntity", "com.fevzi.summonity.entity.SummonedWarriorsArsenalEntity"},
        remap = false)
public abstract class SummonityIdleScanMixin {
    @WrapOperation(method = {
            "getActiveTarget(Lnet/minecraft/world/entity/player/Player;D)Lnet/minecraft/world/entity/LivingEntity;",
            "getActiveTarget(Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/entity/LivingEntity;",
            "findTarget(Lnet/minecraft/world/entity/player/Player;D)Lnet/minecraft/world/entity/LivingEntity;"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 1, allow = 1)
    private List<?> bons$summonityCoo49IdleScan(Level level, Class<?> type, AABB box, Predicate<?> test, Operation<List<?>> original) {
        return IdleScans.search((Entity) (Object) this, level, type, box, test, original);
    }
}
