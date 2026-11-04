package bons.furious.mixin.elysium_sound_memo;

import bons.furious.patch.elysium_sound_memo.ElysiumMemoHolder;
import bons.furious.patch.elysium_sound_memo.ElysiumSoundMemo;
import com.bawnorton.mixinsquared.TargetHandler;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * elysium_sound_transformer_memo (ElysiumAPI 1.1.3 on Minecraft 1.20.1 / Forge 47.4.28, both sides). Since 1.0.49.
 *
 * A MixinSquared @TargetHandler HEAD injection (cancellable) into ElysiumAPI's BlockMixin.elysium$soundTransformer handler,
 * merged into Block.getSoundType(BlockState) (m_49962_): ElysiumSoundMemo answers the call from the block's remembered
 * transformer, or runs Elysium's search once and remembers it, and the handler's body is skipped; with the switch off, for
 * block sets it does not know, or in shadow mode, it returns at once and Elysium's handler runs as shipped (see
 * ElysiumSoundMemo for why the answer is the same). Also the per-Block memo slot (a volatile field, ElysiumMemoHolder).
 * Priority 70000: applied after ElysiumAPI's BlockMixin (priority 69420), whose handler it targets. require = 0, expect = 0:
 * if another mod removed Elysium's mixin (Collections Of Optimizations 4.6 once cancelled another Elysium mixin), nothing is
 * injected; the switch's guard steps aside first when it sees such a canceller (Guards.cancelledDecision). Dungeons
 * Delight's and Sounds' injections into getSoundType are untouched.
 */
@Mixin(value = Block.class, priority = 70000, remap = false)
public abstract class ElysiumSoundMemoMixin implements ElysiumMemoHolder {
    @Unique
    private volatile Object bons$elyMemo;

    @Override
    public Object bons$elyMemo() {
        return this.bons$elyMemo;
    }

    @Override
    public void bons$setElyMemo(Object memo) {
        this.bons$elyMemo = memo;
    }

    @TargetHandler(mixin = "net.jadenxgamer.elysium_api.impl.mixin.block.BlockMixin", name = "elysium$soundTransformer")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void bons$elySoundMemo(BlockState state, CallbackInfoReturnable<SoundType> theirs, CallbackInfo ci) {
        if (ElysiumSoundMemo.answer((Block) (Object) this, state, theirs)) ci.cancel();
    }
}
