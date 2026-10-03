package bons.furious.mixin.vanilla;

import bons.furious.patch.vanilla.TickingLevels;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.TickingTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * vanilla_block_ticking_range_memo (Minecraft 1.20.1), part 2 of 2: the ticking-level lookups of
 * DistanceManager.inBlockTickingRange (inBlockTickingRange) and inEntityTickingRange (inEntityTickingRange) go through TickingLevels, which
 * answers a repeat of the same chunk while the tracker is unchanged. A @Redirect: these run for every ticking block entity.
 */
@Mixin(value = DistanceManager.class, remap = false)
public abstract class DistanceManagerTickingMixin {
    @Redirect(method = {"inBlockTickingRange", "inEntityTickingRange"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/TickingTracker;getLevel(J)I"))
    private int bons$rememberedLevel(TickingTracker tracker, long chunk) {
        return TickingLevels.level(tracker, chunk);
    }
}
