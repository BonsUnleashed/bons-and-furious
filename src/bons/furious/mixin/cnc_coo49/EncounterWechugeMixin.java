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
 * WechugeadvanceprocProcedure.execute, the PlayerTickEvent handler that unlocks the Wechuge field guide entry
 * (encountered_wechuge; its test: the animal within a 4 block box, or the advancement cnc:wechuge_killed done). When
 * the ticked player's PlayerVariables already say encountered_wechuge = true, the handler's own condition ends false
 * whatever its search and second test find, so it returns at once (one profiler getEntities, as its search counts);
 * the advancement test of the original throws when cnc:wechuge_killed is not loaded, so then the original runs (and
 * throws as it does); otherwise the original runs. See CncScans.
 */
@Mixin(targets = "net.imasillylittleguy.cnc.procedures.WechugeadvanceprocProcedure", remap = false)
public abstract class EncounterWechugeMixin {
    @Inject(method = "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$cncCoo49MetWechuge(Event event, LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        CncModVariables.PlayerVariables v = CncScans.metVariables(entity);
        if (v != null && v.encountered_wechuge && CncScans.advancementLoaded(entity, CncScans.WECHUGE_ADVANCEMENT)) {
            CncScans.leftOut(world);
            ci.cancel();
        }
    }
}
