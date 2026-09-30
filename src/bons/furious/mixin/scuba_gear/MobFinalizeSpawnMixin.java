package bons.furious.mixin.scuba_gear;

import bons.furious.patch.scuba_gear.DrownedSpawnContext;
import com.bawnorton.mixinsquared.TargetHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * scuba_gear_generation_context (Scuba Gear 1.0.6), part 2 of 2; part 1 is ScubaEventsMixin.
 *
 * Scuba Gear's MobEntityMixin (a RETURN hook on Mob.finalizeSpawn) receives the level the mob is spawned into, but
 * handed only the drowned to ScubaEvents.onDrownedSpawn. This redirect passes the level along as well, through
 * DrownedSpawnContext, so the equipment decision can stay inside a generation region.
 */
@Mixin(value = Mob.class, priority = 1500, remap = false)
public abstract class MobFinalizeSpawnMixin {
    @TargetHandler(mixin = "com.legacy.scuba_gear.mixin.MobEntityMixin", name = "finalizeSpawn")
    @Redirect(method = "@MixinSquared:Handler",
            at = @At(value = "INVOKE", target = "Lcom/legacy/scuba_gear/ScubaEvents;onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V"))
    private void bons$passSpawnLevel(Drowned drowned, ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                     SpawnGroupData data, CompoundTag nbt, CallbackInfoReturnable<SpawnGroupData> callback) {
        DrownedSpawnContext.onDrownedSpawn(drowned, level);
    }
}
