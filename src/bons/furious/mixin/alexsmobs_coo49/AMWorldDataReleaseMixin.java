package bons.furious.mixin.alexsmobs_coo49;

import bons.furious.patch.alexsmobs_coo49.LevelMapRelease;
import com.github.alexthe666.alexsmobs.world.AMWorldData;
import java.util.Map;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * alexsmobs_release_level_maps (Alex's Mobs 1.22.9): at the end of AMWorldData's static initializer its dataMap is handed
 * to LevelMapRelease, whose LevelEvent.Unload listener removes unloading levels from it. See LevelMapRelease. COO 5.8 has
 * the same idea without a mixin here (option alexsmobs.releaseLevelMaps).
 */
@Mixin(value = AMWorldData.class, remap = false)
public abstract class AMWorldDataReleaseMixin {
    @Shadow
    @Final
    private static Map<Level, AMWorldData> dataMap;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void bons$handOverWorldData(CallbackInfo ci) {
        LevelMapRelease.worldData(dataMap);
    }
}
