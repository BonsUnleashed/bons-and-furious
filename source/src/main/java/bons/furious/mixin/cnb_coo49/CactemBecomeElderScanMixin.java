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
 * cnb_cactem_become_elder_scan_interval (Creatures and Beasts: Continued 1.7.17, server; trade-off, off by default): a
 * MixinExtras @WrapMethod on CactemEntity$BecomeElderGoal.canUse (canUse = !isElderNear(), a search for every cactem in a
 * 65 x 33 x 65 box). After a check whose search found an elder (canUse false), the next N - 1 checks answer false without
 * the search; any other check runs the original. What differs: a cactem whose elder has just died or walked off can take
 * up to N - 1 goal checks longer to become an elder. See CnbScans.
 */
@Mixin(targets = "com.cgessinger.creaturesandbeasts.entities.CactemEntity$BecomeElderGoal", remap = false)
public abstract class CactemBecomeElderScanMixin {
    @Shadow
    @Final
    private CactemEntity cactem;

    /** Checks left that answer "an elder is near" without a search. */
    @Unique
    private int bons$cnbCoo49ElderSkips;

    @WrapMethod(method = "canUse()Z")
    private boolean bons$cnbCoo49BecomeElderCanUse(Operation<Boolean> original) {
        if (!CnbScans.becomeElder) return original.call();
        if (this.bons$cnbCoo49ElderSkips > 0) {
            this.bons$cnbCoo49ElderSkips--;
            CnbScans.countSkipped(this.cactem.level());
            return false;
        }
        boolean use = original.call();
        if (!use) {
            this.bons$cnbCoo49ElderSkips = CnbScans.BecomeElder.N - 1;
            if (!CnbScans.announcedElder()) CnbScans.announce(1);
        }
        return use;
    }
}
