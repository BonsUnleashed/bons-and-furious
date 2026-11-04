package bons.furious.mixin.sanguinenetworks_coo49;

import bons.furious.patch.sanguinenetworks_coo49.SacrificerTickStats;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.luna.sanguine_networks.block.entity.VSBlockEntity;
import com.luna.sanguine_networks.util.Pair;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * sanguinenetworks_tick_stats_reuse (Sanguine Neural Networks 2.0.1, server side; exact; no Sanguine code is carried).
 * VSBlockEntity.tick asks getRFTick() twice (the energy check, then the energy taken), and getRFTick works the data model's
 * stats out again each time (getModelFromStack(getModelStack()).second). The first question's own lookup is kept with the
 * slot-0 stack it is for and answers the second, for the same stack object. Nothing of ours runs before the first energy
 * question, so a tick without a model (or short of energy) runs the mod's code alone. See SacrificerTickStats.
 */
@SuppressWarnings({"rawtypes"})
@Mixin(value = VSBlockEntity.class, remap = false)
public abstract class SacrificerTickStatsMixin {
    /** The stats the first energy question of this tick worked out, and the slot-0 stack they are for. */
    @Unique
    private Pair bons$sanguineTickStats;
    @Unique
    private ItemStack bons$sanguineTickStatsStack;

    @Shadow
    public abstract ItemStack getModelStack();

    @Shadow
    public abstract Pair getModelFromStack(ItemStack stack);

    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE", target = "Lcom/luna/sanguine_networks/block/entity/VSBlockEntity;getRFTick()I", ordinal = 0),
            require = 1, allow = 1)
    private int bons$sanguineTickStatsFirst(VSBlockEntity self, Operation<Integer> original) {
        if (!SacrificerTickStats.enabled || self.getClass() != VSBlockEntity.class || !SacrificerTickStats.plainModels()) {
            this.bons$sanguineTickStats = null;
            return original.call(self);
        }
        // the mod's getRFTick is getModelFromStack(getModelStack()).second: the same lookup, its stats kept for the second question
        ItemStack stack = this.getModelStack();
        Pair stats = this.getModelFromStack(stack);
        this.bons$sanguineTickStats = stats;
        this.bons$sanguineTickStatsStack = stack;
        return (Integer) stats.second;
    }

    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE", target = "Lcom/luna/sanguine_networks/block/entity/VSBlockEntity;getRFTick()I", ordinal = 1),
            require = 1, allow = 1)
    private int bons$sanguineTickStatsEnergy(VSBlockEntity self, Operation<Integer> original) {
        Pair stats = this.bons$sanguineTickStats;
        this.bons$sanguineTickStats = null;   // this tick's only
        if (!SacrificerTickStats.enabled || stats == null || self.getClass() != VSBlockEntity.class || this.bons$sanguineTickStatsStack != this.getModelStack()
                || !SacrificerTickStats.plainModels()) return original.call(self);
        int kept = (Integer) stats.second;
        if (SacrificerTickStats.SHADOW) return SacrificerTickStats.shadow("sanguinenetworks_tick_stats_reuse", kept, original.call(self));
        SacrificerTickStats.reusedOne(false);
        return kept;
    }
}
