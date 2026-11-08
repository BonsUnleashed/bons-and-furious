package bons.furious.mixin.cnc_coo49;

import bons.furious.patch.cnc_coo49.CncScans;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * cnc_sasquatch_texture_scan_interval (Critters n' Crawlers 2.2.5 for Minecraft 1.21.1, both sides; trade-off, off by
 * default): SasquatchOnEntityTickUpdateProcedure.execute, called from SasquatchEntity.baseTick every tick: two 16 block
 * player searches choose the visible or hidden texture (synced data, saved). With the switch the procedure runs only on
 * every N-th tick of the sasquatch (phase from its entity id) and returns at once in between, so the texture keeps its
 * last choice. What differs: the texture can switch up to N - 1 ticks after a player comes or goes. See
 * CncScans.textureHeld.
 */
@Mixin(targets = "net.imasillylittleguy.cnc.procedures.SasquatchOnEntityTickUpdateProcedure", remap = false)
public abstract class SasquatchTextureIntervalMixin {
    @Inject(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$cncCoo49TextureEveryN(LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        if (CncScans.textureHeld(entity)) ci.cancel();
    }
}
