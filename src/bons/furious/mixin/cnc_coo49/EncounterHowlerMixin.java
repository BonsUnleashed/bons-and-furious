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
 * HowlerAdvanceprocProcedure.execute, the PlayerTickEvent handler that unlocks the Howler field guide entry
 * (encountered_howler; its test: the animal within a 4 block box, or its head item (HOWLER_HEAD) in the inventory).
 * When the ticked player's PlayerVariables already say encountered_howler = true, the handler's own condition ends
 * false whatever its search and second test find, so it returns at once (one profiler getEntities, as its search
 * counts); otherwise the original runs. See CncScans.
 */
@Mixin(targets = "net.imasillylittleguy.cnc.procedures.HowlerAdvanceprocProcedure", remap = false)
public abstract class EncounterHowlerMixin {
    @Inject(method = "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$cncCoo49MetHowler(Event event, LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        CncModVariables.PlayerVariables v = CncScans.metVariables(entity);
        if (v != null && v.encountered_howler) {
            CncScans.leftOut(world);
            ci.cancel();
        }
    }
}
