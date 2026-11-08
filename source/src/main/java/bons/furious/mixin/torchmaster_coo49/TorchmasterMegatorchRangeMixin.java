package bons.furious.mixin.torchmaster_coo49;

import bons.furious.patch.torchmaster_coo49.TorchmasterMegatorchRange;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.xalcon.torchmaster.logic.entityblocking.megatorch.MegatorchEntityBlockingLight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * torchmaster_megatorch_range_first (Torchmaster 21.1.13 for NeoForge 1.21.1, server; kind change, off by default (exact
 * answers; slower only for a torch in range asked about an unlisted type); no Torchmaster code carried):
 * MegatorchEntityBlockingLight.shouldBlockEntityType answers with the distance test first and the entity block list only in
 * range (the same two pure tests in the other order). See TorchmasterMegatorchRange.
 */
@Mixin(value = MegatorchEntityBlockingLight.class, remap = false)
public abstract class TorchmasterMegatorchRangeMixin {
    @Shadow
    private BlockPos pos;

    @Inject(method = "shouldBlockEntityType(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/MobSpawnType;)Z",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void bons$torchmasterMegatorchRangeFirst(EntityType<?> entityType, Level level, Vec3 at, MobSpawnType spawnType, CallbackInfoReturnable<Boolean> cir) {
        if (TorchmasterMegatorchRange.enabled) cir.setReturnValue(TorchmasterMegatorchRange.shouldBlock(entityType, at, this.pos));
    }
}
