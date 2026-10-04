package bons.furious.patch.slashblade_motion;

/**
 * slashblade_motion_update_memo (SlashBlade: Resharped 1.9.65, client): what MmdPmdModelStampMixin adds to
 * jp.nyatla.nymmd.MmdPmdModel_BasicClass - the motion player whose updateMotion call last completed on this model (its
 * bones hold that call's result), or null while an update is running, after one failed, or before the first one.
 */
public interface MotionMemoModel {
    Object bons$memoLastWriter();

    void bons$memoLastWriter(Object player);
}
