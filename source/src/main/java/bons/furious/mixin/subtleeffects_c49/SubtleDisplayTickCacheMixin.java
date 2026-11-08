package bons.furious.mixin.subtleeffects_c49;

import bons.furious.patch.subtleeffects_c49.DisplayTickStateCache;
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * subtleeffects_display_tick_state_cache (Subtle Effects 1.15.1 for NeoForge 1.21.1, client; off by default): three
 * MixinSquared @TargetHandler wraps inside Subtle Effects' ClientLevelMixin.animateTick handler (merged into
 * ClientLevel.doAnimateTick): its air test passes the state it works on to DisplayTickStateCache, its walk over
 * ModBlockTickers.REGISTERED_SPECIAL is left out when no special-ticker predicate holds for that state (kept per state
 * until ModBlockTickers.init), and its ChestBlockEntityTicker.trySpawn call is left out for states without a block entity
 * (DisplayTickStateCache says what changes). Priority 1500: after Subtle Effects' mixin. Our own logic only (Subtle Effects
 * is All Rights Reserved).
 */
@Mixin(value = ClientLevel.class, priority = 1500, remap = false)
public abstract class SubtleDisplayTickCacheMixin {
    @TargetHandler(mixin = "einstein.subtle_effects.mixin.client.ClientLevelMixin", name = "animateTick")
    @WrapOperation(method = "@MixinSquared:Handler", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;isAir()Z"), require = 0, expect = 0, allow = 1)
    private boolean bons$seCacheState(BlockState state, Operation<Boolean> original) {
        DisplayTickStateCache.state(state);
        return original.call(state);
    }

    @TargetHandler(mixin = "einstein.subtle_effects.mixin.client.ClientLevelMixin", name = "animateTick")
    @WrapOperation(method = "@MixinSquared:Handler", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;forEach(Ljava/util/function/BiConsumer;)V"), require = 0, expect = 0, allow = 1)
    private void bons$seCacheSpecialWalk(Map<?, ?> map, BiConsumer<?, ?> action, Operation<Void> original) {
        if (!DisplayTickStateCache.noSpecial(map)) original.call(map, action);
    }

    @TargetHandler(mixin = "einstein.subtle_effects.mixin.client.ClientLevelMixin", name = "animateTick")
    @WrapOperation(method = "@MixinSquared:Handler", at = @At(value = "INVOKE",
            target = "Leinstein/subtle_effects/ticking/tickers/ChestBlockEntityTicker;trySpawn(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"),
            require = 0, expect = 0, allow = 1)
    private void bons$seCacheChest(Level level, BlockPos pos, BlockState state, Operation<Void> original) {
        if (!DisplayTickStateCache.noChest(state)) original.call(level, pos, state);
    }
}
