package bons.furious.mixin.snuffles_coo49;

import bons.furious.patch.snuffles_coo49.SnufflesCoo49;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * snuffles_fluff_regrow_server_only (Snuffles 1.1.2 for NeoForge 1.21.1, MIT; client): MixinExtras @ModifyExpressionValue on
 * the hasFluff() call that guards the fluff regrow block of Snuffle.tick: on a client level it answers true, so the client
 * copy of a snuffle neither counts its own (never synced) regrow timer down nor writes DATA_FLUFF itself; it shows what
 * the server sends. Server levels (dedicated or integrated) run the original. Our own logic only.
 */
@Mixin(targets = "mod.schnappdragon.snuffles.common.entity.animal.Snuffle", remap = false)
public abstract class SnuffleFluffRegrowMixin {
    @ModifyExpressionValue(method = "tick()V",
            at = @At(value = "INVOKE", target = "Lmod/schnappdragon/snuffles/common/entity/animal/Snuffle;hasFluff()Z"),
            require = 1, allow = 1)
    private boolean bons$snufflesCoo49ServerRegrow(boolean original) {
        return original || SnufflesCoo49.clientSkipsRegrow((Entity) (Object) this);
    }
}
