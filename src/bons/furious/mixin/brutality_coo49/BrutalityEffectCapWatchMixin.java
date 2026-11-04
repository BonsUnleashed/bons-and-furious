package bons.furious.mixin.brutality_coo49;

import bons.furious.patch.brutality_coo49.BrutalityBlight;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * brutality_blight_scan_idle_skip (Brutality 0.8.5, MIT; client; since 1.0.49): at the return of the only two methods that
 * write EntityEffectCap's private miracleBlighted field (setMiracleBlighted, and deserializeNBT, which also calls it) the
 * capability's current flag goes to BrutalityBlight's weak identity set. Reads only; the methods run as shipped.
 */
@Mixin(targets = "net.goo.brutality.entity.capabilities.EntityCapabilities$EntityEffectCap", remap = false)
public abstract class BrutalityEffectCapWatchMixin {
    @Shadow
    private boolean miracleBlighted;

    @Inject(method = "setMiracleBlighted(Z)V", at = @At("RETURN"), require = 1, allow = 1)
    private void bons$brutalityCoo49WatchSet(boolean active, CallbackInfo ci) {
        BrutalityBlight.watch(this, this.miracleBlighted);
    }

    @Inject(method = "deserializeNBT(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"), require = 1, allow = 1)
    private void bons$brutalityCoo49WatchLoad(CompoundTag tag, CallbackInfo ci) {
        BrutalityBlight.watch(this, this.miracleBlighted);
    }
}
