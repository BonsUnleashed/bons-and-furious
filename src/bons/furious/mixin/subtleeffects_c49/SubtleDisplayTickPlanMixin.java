package bons.furious.mixin.subtleeffects_c49;

import bons.furious.patch.subtleeffects_c49.DisplayTickPlan;
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * subtleeffects_display_tick_state_plan (Subtle Effects 1.15.1-hotfix.1, client): two MixinSquared @TargetHandler wraps
 * inside Subtle Effects' ClientLevelMixin.animateTick handler (merged into ClientLevel.doAnimateTick): its getBlockState
 * of the sampled position answers the state doAnimateTick just read (DisplayTickPlan.reuse), and its GeyserManager.tick
 * call is left out when no geyser type can use the block or fluid there (DisplayTickPlan.noGeyser). Priority 1500: after
 * Subtle Effects' mixin. require = 0: without that handler (Subtle Effects absent, or its mixin cancelled) nothing applies.
 * Our own logic only (Subtle Effects is All Rights Reserved).
 */
@Mixin(value = ClientLevel.class, priority = 1500, remap = false)
public abstract class SubtleDisplayTickPlanMixin {
    @TargetHandler(mixin = "einstein.subtle_effects.mixin.client.ClientLevelMixin", name = "animateTick")
    @WrapOperation(method = "@MixinSquared:Handler", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;m_8055_(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"),
            require = 0, expect = 0, allow = 1)
    private BlockState bons$seSampledState(ClientLevel level, BlockPos pos, Operation<BlockState> original) {
        BlockState kept = DisplayTickPlan.reuse(level, pos);
        if (kept == null) return original.call(level, pos);
        if (DisplayTickPlan.SHADOW) {
            BlockState fresh = original.call(level, pos);
            DisplayTickPlan.SHADOW_CHECKS.incrementAndGet();
            if (fresh != kept) DisplayTickPlan.shadowMismatch("remembered " + kept + " but the level holds " + fresh + " at " + pos);
            return fresh;
        }
        return kept;
    }

    @TargetHandler(mixin = "einstein.subtle_effects.mixin.client.ClientLevelMixin", name = "animateTick")
    @WrapOperation(method = "@MixinSquared:Handler", at = @At(value = "INVOKE",
            target = "Leinstein/subtle_effects/ticking/GeyserManager;tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V"),
            require = 0, expect = 0, allow = 1)
    private void bons$seGeyserCheck(Level level, BlockState state, BlockPos pos, Operation<Void> original) {
        if (!DisplayTickPlan.noGeyser(level, pos)) {
            original.call(level, state, pos);
            return;
        }
        if (DisplayTickPlan.SHADOW) {
            int before = DisplayTickPlan.geyserRecords();
            original.call(level, state, pos);
            DisplayTickPlan.SHADOW_CHECKS.incrementAndGet();
            if (DisplayTickPlan.geyserRecords() != before) DisplayTickPlan.shadowMismatch("a skipped geyser check changed the geyser records at " + pos);
        }
    }
}
