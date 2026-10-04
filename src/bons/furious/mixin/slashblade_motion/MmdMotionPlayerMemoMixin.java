package bons.furious.mixin.slashblade_motion;

import bons.furious.patch.slashblade_motion.MotionMemo;
import bons.furious.patch.slashblade_motion.MotionMemoPlayer;
import jp.nyatla.nymmd.MmdMotionPlayer;
import jp.nyatla.nymmd.MmdPmdModel_BasicClass;
import jp.nyatla.nymmd.MmdVmdMotion_BasicClass;
import jp.nyatla.nymmd.core.PmdFace;
import jp.nyatla.nymmd.types.MmdMatrix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * slashblade_motion_update_memo (SlashBlade: Resharped 1.9.65, MIT; client): jp.nyatla.nymmd.MmdMotionPlayer.updateMotion
 * (the MMD bone, IK and skinning update SlashBlade's PlayerAnimator layer runs about 20 times per frame per animated
 * player with the same time) asks MotionMemo at its start whether the call would repeat the player's last completed
 * update of the same model with the same motion, time and arrays; such a call returns at once (see MotionMemo for why the
 * result is identical), every other call runs unchanged and is recorded when it returns normally. HEAD/RETURN injections
 * rather than a method wrapper: a wrapper boxes the float argument and allocates an argument array on every call that
 * runs. The mixin only adds read access to the method's private inputs and the player's records; no SlashBlade code is
 * carried.
 */
@Mixin(value = MmdMotionPlayer.class, remap = false)
public abstract class MmdMotionPlayerMemoMixin implements MotionMemoPlayer {
    @Shadow
    protected MmdPmdModel_BasicClass _ref_pmd_model;
    @Shadow
    protected MmdVmdMotion_BasicClass _ref_vmd_motion;
    @Shadow
    private PmdFace[] m_ppFaceList;
    @Shadow
    private boolean _lookme_enabled;

    @Unique
    private MmdVmdMotion_BasicClass bons$lastMotion, bons$pendingMotion;
    @Unique
    private int bons$lastTime, bons$pendingTime;
    @Unique
    private MmdMatrix[] bons$lastSkin, bons$pendingSkin;
    @Unique
    private MmdPmdModel_BasicClass bons$pendingModel;
    @Unique
    private Object bons$pendingShadow;

    @Inject(method = "updateMotion(F)V", at = @At("HEAD"), cancellable = true)
    private void bons$repeatedUpdate(float positionInMsec, CallbackInfo ci) {
        if (MotionMemo.head((MmdMotionPlayer) (Object) this, this, positionInMsec)) {
            ci.cancel();
        }
    }

    @Inject(method = "updateMotion(F)V", at = @At("RETURN"))
    private void bons$completedUpdate(float positionInMsec, CallbackInfo ci) {
        MotionMemo.tail((MmdMotionPlayer) (Object) this, this);
    }

    @Override
    public MmdPmdModel_BasicClass bons$memoModel() {
        return this._ref_pmd_model;
    }

    @Override
    public MmdVmdMotion_BasicClass bons$memoMotion() {
        return this._ref_vmd_motion;
    }

    @Override
    public PmdFace[] bons$memoFaceList() {
        return this.m_ppFaceList;
    }

    @Override
    public boolean bons$memoLookMe() {
        return this._lookme_enabled;
    }

    @Override
    public MmdVmdMotion_BasicClass bons$memoLastMotion() {
        return this.bons$lastMotion;
    }

    @Override
    public int bons$memoLastTime() {
        return this.bons$lastTime;
    }

    @Override
    public MmdMatrix[] bons$memoLastSkin() {
        return this.bons$lastSkin;
    }

    @Override
    public void bons$memoRecord(MmdVmdMotion_BasicClass motion, int timeBits, MmdMatrix[] skin) {
        this.bons$lastMotion = motion;
        this.bons$lastTime = timeBits;
        this.bons$lastSkin = skin;
    }

    @Override
    public void bons$memoPending(MmdPmdModel_BasicClass model, MmdVmdMotion_BasicClass motion, int timeBits, MmdMatrix[] skin, Object shadow) {
        this.bons$pendingModel = model;
        this.bons$pendingMotion = motion;
        this.bons$pendingTime = timeBits;
        this.bons$pendingSkin = skin;
        this.bons$pendingShadow = shadow;
    }

    @Override
    public MmdPmdModel_BasicClass bons$memoPendingModel() {
        return this.bons$pendingModel;
    }

    @Override
    public MmdVmdMotion_BasicClass bons$memoPendingMotion() {
        return this.bons$pendingMotion;
    }

    @Override
    public int bons$memoPendingTime() {
        return this.bons$pendingTime;
    }

    @Override
    public MmdMatrix[] bons$memoPendingSkin() {
        return this.bons$pendingSkin;
    }

    @Override
    public Object bons$memoPendingShadow() {
        return this.bons$pendingShadow;
    }
}
