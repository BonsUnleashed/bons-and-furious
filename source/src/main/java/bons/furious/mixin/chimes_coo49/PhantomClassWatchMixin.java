package bons.furious.mixin.chimes_coo49;

import bons.furious.patch.chimes_coo49.ChimesScans;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * chimes_client_phantom_scan_skip (Minecraft 1.21.1 Phantom, client; part of the Chimes 2.1.1 client skip). At the end of
 * Phantom's constructor this hands the new phantom's class to ChimesScans, which checks once per class that no class
 * between it and Mob declares getTarget: the client skip relies on getTarget being Mob's plain field read. Reads only;
 * nothing the game sees changes.
 */
@Mixin(value = Phantom.class, remap = false)
public abstract class PhantomClassWatchMixin {
    @Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V", at = @At("RETURN"), require = 1)
    private void bons$chimesWatchPhantomClass(EntityType<?> type, Level level, CallbackInfo ci) {
        ChimesScans.phantomConstructed(this.getClass());
    }
}
