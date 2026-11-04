package bons.furious.mixin.cnc_coo49;

import bons.furious.patch.cnc_coo49.CncScans;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.eventbus.api.Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * cnc_encounter_client_lean (Critters n' Crawlers 2.2.5, client; exact variant, on by default): the 26 field guide
 * PlayerTickEvent handlers (*AdvanceprocProcedure.execute). The client fires PlayerTickEvent for every player it ticks;
 * for a player other than the local one these handlers can only set encountered_* flags in that player's client copy of
 * PlayerVariables, which nothing reads or sends (the field guide reads the local player's copy; the sync and toast
 * packets are sent only for server players). On a client level the handler therefore returns at once for those players
 * (one profiler getEntities, as its search counts). Exact variant: other players' client copies of PlayerVariables keep
 * their defaults. See CncScans.remotePlayerOnClient.
 */
@Mixin(targets = {
        "net.imasillylittleguy.cnc.procedures.BeaverAdvanceprocProcedure",
        "net.imasillylittleguy.cnc.procedures.BlackbearAdvanceprocProcedure",
        "net.imasillylittleguy.cnc.procedures.CaribouadvanceprocProcedure",
        "net.imasillylittleguy.cnc.procedures.ChupacabraadvanceprocProcedure",
        "net.imasillylittleguy.cnc.procedures.CougaradvanceprocProcedure",
        "net.imasillylittleguy.cnc.procedures.CoyoteadvanceprocProcedure",
        "net.imasillylittleguy.cnc.procedures.ElkadvanceprocProcedure",
        "net.imasillylittleguy.cnc.procedures.GiantBoaradvanceprocProcedure",
        "net.imasillylittleguy.cnc.procedures.GooseAdvanceprocProcedure",
        "net.imasillylittleguy.cnc.procedures.GreywolfadvanceprocProcedure",
        "net.imasillylittleguy.cnc.procedures.HarmlessSnakeAdvanceprocProcedure",
        "net.imasillylittleguy.cnc.procedures.HowlerAdvanceprocProcedure",
        "net.imasillylittleguy.cnc.procedures.MarmotAdvanceProcProcedure",
        "net.imasillylittleguy.cnc.procedures.MouseAdvanceprocProcedure",
        "net.imasillylittleguy.cnc.procedures.PitViperAdvanceprocProcedure",
        "net.imasillylittleguy.cnc.procedures.RattlesnakeAdvanceprocProcedure",
        "net.imasillylittleguy.cnc.procedures.RingtailadvanceprocProcedure",
        "net.imasillylittleguy.cnc.procedures.SasquatchAdvanceprocProcedure",
        "net.imasillylittleguy.cnc.procedures.SkinwalkerAdvanceProcProcedure",
        "net.imasillylittleguy.cnc.procedures.SkunkAdvanceProcProcedure",
        "net.imasillylittleguy.cnc.procedures.SquonkAdvanceprocProcedure",
        "net.imasillylittleguy.cnc.procedures.TurkeyadvanceprocProcedure",
        "net.imasillylittleguy.cnc.procedures.WechugeadvanceprocProcedure",
        "net.imasillylittleguy.cnc.procedures.WendigoadvanceprocProcedure",
        "net.imasillylittleguy.cnc.procedures.WhitetailAdvanceProcProcedure",
        "net.imasillylittleguy.cnc.procedures.WolverineadvanceprocProcedure"
}, remap = false)
public abstract class EncounterClientLeanMixin {
    @Inject(method = "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$cncCoo49OnlyLocalPlayer(Event event, LevelAccessor world, double x, double y, double z, Entity entity, CallbackInfo ci) {
        if (CncScans.remotePlayerOnClient(world, entity)) ci.cancel();
    }
}
