package bons.furious.mixin.elysium_sound_memo;

import bons.furious.patch.elysium_sound_memo.ElysiumMemoHolder;
import bons.furious.patch.elysium_sound_memo.ElysiumSoundMemo;
import com.bawnorton.mixinsquared.TargetHandler;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * elysium_sound_transformer_memo (ElysiumAPI; 1.21.1 tested build: ElysiumAPI 1.21.1-2.0.1, ElysiumAPI-1.21.1-2.0.1.jar;
 * both sides).
 *
 * A MixinSquared @TargetHandler HEAD injection (cancellable) into ElysiumAPI's BlockBehaviorMixin.elysium_api$soundTransformer
 * handler, merged into BlockBehaviour.getSoundType(BlockState): ElysiumSoundMemo answers the call from the block's
 * remembered transformer, or runs Elysium's search once and remembers it, and the handler's body is skipped; with the switch
 * off, for block sets it does not know, without a running server or in shadow mode, it returns at once and Elysium's handler
 * runs as shipped (see ElysiumSoundMemo for why the answer is the same). Also the per-block memo slot (a volatile field,
 * ElysiumMemoHolder). Priority 70000: applied after ElysiumAPI's mixin (default priority), whose handler it targets.
 * require = 0, expect = 0: if another mod removed Elysium's mixin (or Elysium's own mixin plugin left it out), nothing is
 * injected.
 *
 * Ported to 1.21.1: ElysiumAPI 2.0.1 moved the sound transformer hook from BlockMixin (Block.getSoundType, 1.1.3) to
 * BlockBehaviorMixin (BlockBehaviour.getSoundType, where 1.21.1 declares it); its transformers are BlockSoundTransformer
 * records in the datapack registry ElysiumRegistries.Keys.BLOCK_SOUND_TRANSFORMERS, read from
 * RegistryAccessHelper.getServer() (the current server's registry access).
 */
@Mixin(value = BlockBehaviour.class, priority = 70000, remap = false)
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

    @TargetHandler(mixin = "net.jadenxgamer.elysium_api.impl.mixin.block.BlockBehaviorMixin", name = "elysium_api$soundTransformer")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void bons$elySoundMemo(BlockState state, CallbackInfoReturnable<SoundType> theirs, CallbackInfo ci) {
        if (ElysiumSoundMemo.answer(this, state, theirs)) ci.cancel();
    }
}
