package bons.furious.patch.slashblade_motion;

import jp.nyatla.nymmd.MmdPmdModel_BasicClass;
import jp.nyatla.nymmd.MmdVmdMotion_BasicClass;
import jp.nyatla.nymmd.core.PmdFace;
import jp.nyatla.nymmd.types.MmdMatrix;

/**
 * slashblade_motion_update_memo (SlashBlade: Resharped 1.9.65, client): what MmdMotionPlayerMemoMixin adds to
 * jp.nyatla.nymmd.MmdMotionPlayer - read access to the private inputs of updateMotion, the record of the player's last
 * completed update (motion object, time bits, skinning-matrix array) and the pending record of the call that is running
 * (set at its start, turned into the record when it returns normally). No SlashBlade code is carried.
 */
public interface MotionMemoPlayer {
    MmdPmdModel_BasicClass bons$memoModel();

    MmdVmdMotion_BasicClass bons$memoMotion();

    /** The model faces the current motion's face tracks map to (MmdMotionPlayer.m_ppFaceList; null entries = unmapped). */
    PmdFace[] bons$memoFaceList();

    /** MmdMotionPlayer._lookme_enabled (the neck look-at step of updateMotion). */
    boolean bons$memoLookMe();

    MmdVmdMotion_BasicClass bons$memoLastMotion();

    int bons$memoLastTime();

    MmdMatrix[] bons$memoLastSkin();

    void bons$memoRecord(MmdVmdMotion_BasicClass motion, int timeBits, MmdMatrix[] skin);

    /** The running call: its model, motion, time bits and array when it may be recorded (model null = not recordable). */
    void bons$memoPending(MmdPmdModel_BasicClass model, MmdVmdMotion_BasicClass motion, int timeBits, MmdMatrix[] skin, Object shadow);

    MmdPmdModel_BasicClass bons$memoPendingModel();

    MmdVmdMotion_BasicClass bons$memoPendingMotion();

    int bons$memoPendingTime();

    MmdMatrix[] bons$memoPendingSkin();

    Object bons$memoPendingShadow();
}
