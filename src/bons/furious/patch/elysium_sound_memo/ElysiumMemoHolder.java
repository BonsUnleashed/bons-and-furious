package bons.furious.patch.elysium_sound_memo;

/**
 * elysium_sound_transformer_memo (ElysiumAPI 1.1.3, both sides): the per-Block memo slot our Block mixin adds
 * (bons.furious.mixin.elysium_sound_memo.ElysiumSoundMemoMixin implements this interface on net.minecraft.world.level.block.Block).
 * The slot holds an immutable ElysiumSoundMemo.Memo, published through a volatile field.
 */
public interface ElysiumMemoHolder {
    Object bons$elyMemo();

    void bons$setElyMemo(Object memo);
}
