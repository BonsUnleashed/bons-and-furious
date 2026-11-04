package bons.furious.mixin.cnc_coo49;

import bons.furious.patch.cnc_coo49.CncScans;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cnc_sasquatch_knock_client_skip (Critters n' Crawlers 2.2.5, client; exact variant, on by default):
 * SasquatchWoodKnockProcedure.execute, the LivingTickEvent handler that, for a sasquatch, tests the 216 block states of a
 * 6 x 6 x 6 box for logs and on a 1 in 151 roll plays a knock sound with no player. A client level never plays a sound
 * that has no player, so on a client level the handler's "is a sasquatch" test answers false (MixinExtras
 * ModifyExpressionValue on its one instanceof) and the scan is left out. Exact variant: the client no longer makes a new
 * RandomSource for the roll (advancing the seed uniquifier) and, when the roll hits, no longer draws one seed from the
 * level's random for the silent sound. The server side runs as shipped (the knock is heard through the server's sound
 * packet). Our constant tag key redirects in the same method (cnc_constant_tag_keys) are untouched.
 * See CncScans.knockOnClient.
 */
@Mixin(targets = "net.imasillylittleguy.cnc.procedures.SasquatchWoodKnockProcedure", remap = false)
public abstract class SasquatchKnockClientMixin {
    @ModifyExpressionValue(method = "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "CONSTANT", args = "classValue=net/imasillylittleguy/cnc/entity/SasquatchEntity"), require = 1, allow = 1)
    private static boolean bons$cncCoo49KnockServerOnly(boolean isSasquatch, @Local(argsOnly = true) LevelAccessor world) {
        return isSasquatch && !CncScans.knockOnClient(world);
    }
}
