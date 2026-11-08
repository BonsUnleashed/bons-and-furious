package bons.furious.mixin.cnb_coo49;

import bons.furious.patch.cnb_coo49.CnbScans;
import com.cgessinger.creaturesandbeasts.entities.CactemEntity;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * cnb_cactem_trade_scan_interval (Creatures and Beasts: Continued 1.7.17, server; trade-off, off by default): a MixinExtras
 * @WrapMethod on CactemEntity$TradeGoal.canUse. The original searches only while the elder holds no item to walk to and has
 * no target (itemInstance == null, getTarget() == null): every item entity in a 33 x 7 x 33 box, the first totem of undying
 * item is walked to. When such a search found none (itemInstance still null afterwards), the next N - 1 checks that would
 * search answer false without searching; a search that finds one is never throttled, and checks that would not search run
 * the original. What differs: a totem dropped near an elder can be noticed up to N - 1 goal checks later.
 */
@Mixin(targets = "com.cgessinger.creaturesandbeasts.entities.CactemEntity$TradeGoal", remap = false)
public abstract class CactemTradeScanMixin {
    @Shadow
    protected ItemEntity itemInstance;
    @Shadow
    @Final
    protected CactemEntity entityIn;

    /** Searching checks left that answer false without the search. */
    @Unique
    private int bons$cnbCoo49TradeSkips;

    @WrapMethod(method = "canUse()Z")
    private boolean bons$cnbCoo49TradeCanUse(Operation<Boolean> original) {
        if (!CnbScans.trade) return original.call();
        boolean searches = this.itemInstance == null && this.entityIn.getTarget() == null;
        if (searches && this.bons$cnbCoo49TradeSkips > 0) {
            this.bons$cnbCoo49TradeSkips--;
            CnbScans.countSkipped(this.entityIn.level());
            return false;
        }
        boolean use = original.call();
        if (searches && this.itemInstance == null) {
            this.bons$cnbCoo49TradeSkips = CnbScans.Trade.N - 1;
            if (!CnbScans.announcedTrade()) CnbScans.announce(4);
        }
        return use;
    }
}
