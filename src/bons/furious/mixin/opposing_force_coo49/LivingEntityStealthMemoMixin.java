package bons.furious.mixin.opposing_force_coo49;

import bons.furious.patch.opposing_force_coo49.StealthMemo;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * opposing_force_stealth_tick_memo: a field on every LivingEntity for the tick's kept stealth answers (StealthMemo.Holder).
 * Adds a field and two accessors; no method of LivingEntity is changed.
 */
@Mixin(value = LivingEntity.class, remap = false)
public abstract class LivingEntityStealthMemoMixin implements StealthMemo.Holder {
    @Unique
    private Object bons$opposingForceCoo49MemoField;

    @Override
    public Object bons$opposingForceCoo49Memo() {
        return this.bons$opposingForceCoo49MemoField;
    }

    @Override
    public void bons$opposingForceCoo49SetMemo(Object memo) {
        this.bons$opposingForceCoo49MemoField = memo;
    }
}
