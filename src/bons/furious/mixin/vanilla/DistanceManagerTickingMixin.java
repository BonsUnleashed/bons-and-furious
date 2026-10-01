package bons.furious.mixin.vanilla;

import bons.furious.patch.vanilla.TickingLevels;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.TickingTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * vanilla_block_ticking_range_memo (Minecraft 1.20.1), part 2 of 2: the ticking-level lookups of
 * DistanceManager.inBlockTickingRange (m_183916_) and inEntityTickingRange (m_183913_) go through TickingLevels, which
 * answers a repeat of the same chunk while the tracker is unchanged. A @Redirect: these run for every ticking block entity.
 */
@Mixin(value = DistanceManager.class, remap = false)
public abstract class DistanceManagerTickingMixin {
    @Redirect(method = {"m_183916_", "m_183913_"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/TickingTracker;m_6172_(J)I"))
    private int bons$rememberedLevel(TickingTracker tracker, long chunk) {
        return TickingLevels.level(tracker, chunk);
    }
}
