package bons.furious.patch.elysium_sound_memo;

/**
 * elysium_sound_transformer_memo (ElysiumAPI 1.21.1-2.0.1, both sides): the per-block memo slot our BlockBehaviour mixin adds
 * (bons.furious.mixin.elysium_sound_memo.ElysiumSoundMemoMixin implements this interface on
 * net.minecraft.world.level.block.state.BlockBehaviour, so every Block has it). The slot holds an immutable
 * ElysiumSoundMemo.Memo, published through a volatile field.
 */
public interface ElysiumMemoHolder {
    Object bons$elyMemo();

    void bons$setElyMemo(Object memo);
}
