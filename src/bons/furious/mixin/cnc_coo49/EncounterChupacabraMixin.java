package bons.furious.mixin.cnc_coo49;

import bons.furious.patch.cnc_coo49.CncScans;
import net.imasillylittleguy.cnc.network.CncModVariables;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.eventbus.api.Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * cnc_encounter_flag_first (Critters n' Crawlers 2.2.5, both sides; exact variant, on by default):
 * ChupacabraadvanceprocProcedure.execute, the PlayerTickEvent handler that unlocks the Chupacabra field guide entry
 * (encountered_chupacabra; its test: the animal within a 4 block box, or its head item (CHUPACABRA_HEAD) in the
 * inventory). When the ticked player's PlayerVariables already say encountered_chupacabra = true, the handler's own
 * condition ends false whatever its search and second test find, so it returns at once (one profiler getEntities, as
 * its search counts); otherwise the original runs. See CncScans.
 */
@Mixin(targets = "net.imasillylittleguy.cnc.procedures.ChupacabraadvanceprocProcedure", remap = false)
public abstract class EncounterChupacabraMixin {
    @Inject(method = "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$cncCoo49MetChupacabra(Event event, LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        CncModVariables.PlayerVariables v = CncScans.metVariables(entity);
        if (v != null && v.encountered_chupacabra) {
            CncScans.leftOut(world);
            ci.cancel();
        }
    }
}
