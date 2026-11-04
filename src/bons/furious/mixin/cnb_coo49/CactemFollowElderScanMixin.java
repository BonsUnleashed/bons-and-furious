package bons.furious.mixin.cnb_coo49;

import bons.furious.patch.cnb_coo49.CnbScans;
import com.cgessinger.creaturesandbeasts.entities.CactemEntity;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * cnb_cactem_follow_elder_scan_interval (Creatures and Beasts: Continued 1.7.10, server; trade-off, off by default): a
 * MixinExtras @WrapMethod on CactemEntity$FollowElderGoal.canUse (m_8036_: a search for every cactem in a 65 x 9 x 65 box,
 * true only for an elder at least 20 blocks away, which it then follows). After a check that answered false (no elder,
 * or the closest one nearer than 20 blocks), the next N - 1 checks answer false without the search (the false path of
 * the original changes nothing, so neither does a skipped check); any other check runs the original. What differs: a
 * cactem can start following an elder up to N - 1 goal checks later. See CnbScans.
 */
@Mixin(targets = "com.cgessinger.creaturesandbeasts.entities.CactemEntity$FollowElderGoal", remap = false)
public abstract class CactemFollowElderScanMixin {
    @Shadow
    @Final
    private CactemEntity cactem;

    /** Checks left that answer false without a search. */
    @Unique
    private int bons$cnbCoo49FollowSkips;

    @WrapMethod(method = "m_8036_()Z")
    private boolean bons$cnbCoo49FollowElderCanUse(Operation<Boolean> original) {
        if (!CnbScans.followElder) return original.call();
        if (this.bons$cnbCoo49FollowSkips > 0) {
            this.bons$cnbCoo49FollowSkips--;
            CnbScans.countSkipped(this.cactem.m_9236_());
            return false;
        }
        boolean use = original.call();
        if (!use) {
            this.bons$cnbCoo49FollowSkips = CnbScans.FollowElder.N - 1;
            if (!CnbScans.announcedFollow()) CnbScans.announce(2);
        }
        return use;
    }
}
