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
 * cnc_encounter_flag_first (Critters n' Crawlers 2.2.5 for Minecraft 1.21.1, both sides): PlayerSquonkCryEffectProcedure.execute,
 * the PlayerTickEvent.Post handler that gives a player Sobbing next to a squonk. The original searches a 4 block box for
 * squonks and only then tests "living entity, no Sobbing yet, not a client level"; when that test is already false the
 * search cannot change the outcome, so the handler returns at once (one profiler getEntities, as its search counts). A
 * pure reorder of side-effect-free tests: exact. See CncScans.squonkLeftOut.
 */
@Mixin(targets = "net.imasillylittleguy.cnc.procedures.PlayerSquonkCryEffectProcedure", remap = false)
public abstract class SquonkCryFirstTestsMixin {
    @Inject(method = "execute(Lnet/neoforged/bus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$cncCoo49SquonkFirstTests(Event event, LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        if (CncScans.squonkLeftOut(entity)) {
            CncScans.leftOut(world);
            ci.cancel();
        }
    }
}
