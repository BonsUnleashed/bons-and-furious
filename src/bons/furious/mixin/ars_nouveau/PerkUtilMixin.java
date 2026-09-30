package bons.furious.mixin.ars_nouveau;

import com.hollingsworth.arsnouveau.api.perk.IPerkHolder;
import com.hollingsworth.arsnouveau.api.perk.PerkInstance;
import com.hollingsworth.arsnouveau.api.util.PerkUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * ars_direct_perk_snapshots (Ars Nouveau 4.12.7).
 *
 * getPerksFromLiving copied each armour piece's perks into a throw-away list (getPerksFromItem) and then into the
 * result. It now adds each piece's holder.getPerkInstances() straight to the result: the same pieces in the same
 * order, one getPerkInstances call per holder, pieces without a perk holder skipped as before, and the caller still
 * gets a fresh ArrayList.
 */
@Mixin(value = PerkUtil.class, remap = false)
public abstract class PerkUtilMixin {
    @Shadow
    public static IPerkHolder<ItemStack> getPerkHolder(ItemStack stack) {
        throw new AssertionError();
    }

    /**
     * @author BonsUnleashed
     * @reason Build the perk snapshot directly instead of through one temporary list per armour piece.
     */
    @Overwrite
    public static List<PerkInstance> getPerksFromLiving(LivingEntity entity) {
        ArrayList<PerkInstance> perkInstances = new ArrayList<>();
        for (ItemStack stack : entity.m_6168_()) {
            IPerkHolder<ItemStack> holder = getPerkHolder(stack);
            if (holder != null) {
                perkInstances.addAll(holder.getPerkInstances());
            }
        }
        return perkInstances;
    }
}
