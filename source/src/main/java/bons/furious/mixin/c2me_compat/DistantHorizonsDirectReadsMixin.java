package bons.furious.mixin.c2me_compat;

import com.seibel.distanthorizons.common.wrappers.worldGeneration.chunkFileHandling.ChunkFileReader_neoforge;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IModChecker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Switch distanthorizons_c2me_direct_reads (Distant Horizons 3.3.2 with C2ME 0.2.0+alpha.12).
 *
 * Distant Horizons reads saved chunks for its level-of-detail generation itself, from many of its own threads. Whenever
 * the mod "c2me" is installed it switches, for every level, to asking Minecraft's single chunk IO thread instead, and the
 * chunk parsing that follows then also runs on that thread, in the same queue as the game's own chunk loads and saves.
 * The reason is C2ME's replacement chunk IO (ioSystem.replaceImpl), which takes Minecraft's region storage away, and its
 * async chunk IO. This pack's c2me.toml switches both off (and the reduced-allocation serializer), so the IO is
 * Minecraft's own, exactly as without C2ME. C2meCompatPlugin applies this mixin only after reading those three C2ME
 * modules' own enabled flags as false; the constructor then sees "c2me" as not installed and keeps the direct reads.
 * Distant Horizons' own fallback to the IO thread (when the direct path fails) is unchanged.
 */
@Mixin(value = ChunkFileReader_neoforge.class, remap = false)
public abstract class DistantHorizonsDirectReadsMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/core/wrapperInterfaces/modAccessor/IModChecker;isModLoaded(Ljava/lang/String;)Z"))
    private boolean bons$c2meIoIsMinecrafts(IModChecker checker, String modId) {
        return !"c2me".equals(modId) && checker.isModLoaded(modId);
    }
}
