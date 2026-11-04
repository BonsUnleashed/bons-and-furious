package bons.furious.mixin.hexerei_coo49;

import bons.furious.patch.hexerei_coo49.HexLights;
import java.util.Set;
import net.joefoxe.hexerei.light.LambHexereiDynamicLight;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * hexerei_empty_light_scan_fast_path (Hexerei 1.20.1-0.4.2.3, MIT: no code carried; client; exact), part 1 of 2: right
 * before each of the three methods that change LightManager's light-source set releases the set's write lock, the set's
 * size is copied to HexLights (still inside the lock). See HexLights for why the scans can then answer an empty set at
 * once.
 */
@Mixin(targets = "net.joefoxe.hexerei.light.LightManager", remap = false)
public abstract class HexLightSourceCountMixin {
    @Shadow
    @Final
    private static Set<LambHexereiDynamicLight> dynamicLightSources;

    @Inject(method = "addLightSource(Lnet/joefoxe/hexerei/light/LambHexereiDynamicLight;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/locks/ReentrantReadWriteLock$WriteLock;unlock()V"), require = 1, allow = 1)
    private static void bons$hexereiCountAfterAdd(LambHexereiDynamicLight source, CallbackInfo ci) {
        HexLights.mirror(dynamicLightSources.size());
    }

    @Inject(method = "removeLightSource(Lnet/joefoxe/hexerei/light/LambHexereiDynamicLight;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/locks/ReentrantReadWriteLock$WriteLock;unlock()V"), require = 1, allow = 1)
    private static void bons$hexereiCountAfterRemove(LambHexereiDynamicLight source, CallbackInfo ci) {
        HexLights.mirror(dynamicLightSources.size());
    }

    @Inject(method = "clearLightSources()V",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/locks/ReentrantReadWriteLock$WriteLock;unlock()V"), require = 1, allow = 1)
    private static void bons$hexereiCountAfterClear(CallbackInfo ci) {
        HexLights.mirror(dynamicLightSources.size());
    }
}
