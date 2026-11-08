package bons.furious.mixin.cnc_coo49;

import bons.furious.patch.cnc_coo49.CncScans;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * cnc_encounter_client_lean (Critters n' Crawlers 2.2.5 for Minecraft 1.21.1, client): PlayerSquonkCryEffectProcedure.execute.
 * Its only effect (adding Sobbing) is behind "not a client level", so on a client level it returns at once for every player
 * (one profiler getEntities, as its search counts). Exact on a client level. See CncScans.squonkOnClient.
 */
@Mixin(targets = "net.imasillylittleguy.cnc.procedures.PlayerSquonkCryEffectProcedure", remap = false)
public abstract class SquonkCryClientMixin {
    @Inject(method = "execute(Lnet/neoforged/bus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$cncCoo49SquonkClient(Event event, LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        if (CncScans.squonkOnClient(world, entity)) ci.cancel();
    }
}
