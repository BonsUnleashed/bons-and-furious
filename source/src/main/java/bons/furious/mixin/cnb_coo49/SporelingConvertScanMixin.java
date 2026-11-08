package bons.furious.mixin.cnb_coo49;

import bons.furious.patch.cnb_coo49.CnbScans;
import com.cgessinger.creaturesandbeasts.entities.SporelingEntity;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * cnb_sporeling_convert_scan_interval (Creatures and Beasts: Continued 1.7.17, server; trade-off, off by default): a
 * MixinExtras @WrapMethod on ConvertItemGoal.canUse (friendly sporelings). The original searches only while the sporeling
 * is not sitting and holds no item to walk to: every item entity in a 33 x 7 x 33 box, the first dirt or cursed enchanted
 * item is walked to. When such a search found nothing (itemInstance still null afterwards), the next N - 1 checks that
 * would search answer false without searching; a search that finds an item is never throttled, and checks that would not
 * search run the original. What differs: an item dropped near a sporeling can be noticed up to N - 1 goal checks later.
 */
@Mixin(targets = "com.cgessinger.creaturesandbeasts.entities.ai.ConvertItemGoal", remap = false)
public abstract class SporelingConvertScanMixin {
    @Shadow
    protected ItemEntity itemInstance;
    @Shadow
    @Final
    protected SporelingEntity entityIn;

    /** Searching checks left that answer false without the search. */
    @Unique
    private int bons$cnbCoo49ConvertSkips;

    @WrapMethod(method = "canUse()Z")
    private boolean bons$cnbCoo49ConvertCanUse(Operation<Boolean> original) {
        if (!CnbScans.convert) return original.call();
        boolean searches = !this.entityIn.isInSittingPose() && this.itemInstance == null;
        if (searches && this.bons$cnbCoo49ConvertSkips > 0) {
            this.bons$cnbCoo49ConvertSkips--;
            CnbScans.countSkipped(this.entityIn.level());
            return false;
        }
        boolean use = original.call();
        if (searches && this.itemInstance == null) {
            this.bons$cnbCoo49ConvertSkips = CnbScans.Convert.N - 1;
            if (!CnbScans.announcedConvert()) CnbScans.announce(5);
        }
        return use;
    }
}
