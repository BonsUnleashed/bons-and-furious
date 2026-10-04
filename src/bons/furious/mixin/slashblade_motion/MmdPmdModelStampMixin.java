package bons.furious.mixin.slashblade_motion;

import bons.furious.patch.slashblade_motion.MotionMemoModel;
import jp.nyatla.nymmd.MmdPmdModel_BasicClass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * slashblade_motion_update_memo (SlashBlade: Resharped 1.9.65, MIT; client): adds to jp.nyatla.nymmd.MmdPmdModel_BasicClass
 * the motion player whose updateMotion call last completed on this model (MotionMemo keeps it; a model shared by two
 * players therefore never answers one player's repeat from the other player's update). Nothing else changes; no
 * SlashBlade code is carried.
 */
@Mixin(value = MmdPmdModel_BasicClass.class, remap = false)
public abstract class MmdPmdModelStampMixin implements MotionMemoModel {
    @Unique
    private Object bons$lastWriter;

    @Override
    public Object bons$memoLastWriter() {
        return this.bons$lastWriter;
    }

    @Override
    public void bons$memoLastWriter(Object player) {
        this.bons$lastWriter = player;
    }
}
