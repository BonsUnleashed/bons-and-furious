package bons.furious.mixin.sanguinenetworks_coo49;

import bons.furious.patch.sanguinenetworks_coo49.SacrificerTickStats;
import com.leo.sanguine_networks.block.entity.VSBlockEntity;
import com.leo.sanguine_networks.util.Pair;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * sanguinenetworks_leo_tick_stats_reuse (Sanguine Neural Networks 1.2.1, package com.leo, server side; exact; no Sanguine
 * code is carried). VSBlockEntity.tick keeps the data model stats its first getModelFromStack works out (a plain value hook:
 * the mod's call runs as it is) and answers the second and third getModelFromStack and both getRFTick() questions of the
 * same tick from them, for the same slot-0 stack object. A tick with an empty model slot reaches none of these calls. See
 * SacrificerTickStats.
 */
@SuppressWarnings({"rawtypes"})
@Mixin(value = VSBlockEntity.class, remap = false)
public abstract class LeoSacrificerTickStatsMixin {
    @Unique
    private Pair bons$sanguineLeoTickStats;
    @Unique
    private ItemStack bons$sanguineLeoTickStatsStack;

    @Shadow
    public abstract ItemStack getModelStack();

    @ModifyExpressionValue(method = "tick()V", at = @At(value = "INVOKE",
            target = "Lcom/leo/sanguine_networks/block/entity/VSBlockEntity;getModelFromStack(Lnet/minecraft/world/item/ItemStack;)Lcom/leo/sanguine_networks/util/Pair;",
            ordinal = 0), require = 1, allow = 1)
    private Pair bons$sanguineLeoTickStatsKeep(Pair stats) {
        // the stack the call was given: slot 0 right before it, unchanged by the call (it only reads, or adds the empty
        // data_model compound to that same stack)
        this.bons$sanguineLeoTickStats = stats;
        this.bons$sanguineLeoTickStatsStack = this.getModelStack();
        return stats;
    }

    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE",
            target = "Lcom/leo/sanguine_networks/block/entity/VSBlockEntity;getModelFromStack(Lnet/minecraft/world/item/ItemStack;)Lcom/leo/sanguine_networks/util/Pair;",
            ordinal = 1), require = 1, allow = 1)
    private Pair bons$sanguineLeoTickStatsSecond(VSBlockEntity self, ItemStack stack, Operation<Pair> original) {
        return this.bons$sanguineLeoTickStatsAgain(self, stack, original);
    }

    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE",
            target = "Lcom/leo/sanguine_networks/block/entity/VSBlockEntity;getModelFromStack(Lnet/minecraft/world/item/ItemStack;)Lcom/leo/sanguine_networks/util/Pair;",
            ordinal = 2), require = 1, allow = 1)
    private Pair bons$sanguineLeoTickStatsThird(VSBlockEntity self, ItemStack stack, Operation<Pair> original) {
        return this.bons$sanguineLeoTickStatsAgain(self, stack, original);
    }

    @Unique
    private Pair bons$sanguineLeoTickStatsAgain(VSBlockEntity self, ItemStack stack, Operation<Pair> original) {
        Pair stats = this.bons$sanguineLeoTickStats;
        if (!SacrificerTickStats.leoEnabled || stats == null || self.getClass() != VSBlockEntity.class || stack != this.bons$sanguineLeoTickStatsStack)
            return original.call(self, stack);
        if (SacrificerTickStats.LEO_SHADOW) {
            Pair fresh = original.call(self, stack);
            return SacrificerTickStats.shadowPair("sanguinenetworks_leo_tick_stats_reuse", stats.first, stats.second, fresh.first, fresh.second, fresh);
        }
        SacrificerTickStats.reusedOne(true);
        return stats;
    }

    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE", target = "Lcom/leo/sanguine_networks/block/entity/VSBlockEntity;getRFTick()I"),
            require = 2, allow = 2)
    private int bons$sanguineLeoTickStatsEnergy(VSBlockEntity self, Operation<Integer> original) {
        Pair stats = this.bons$sanguineLeoTickStats;
        if (!SacrificerTickStats.leoEnabled || stats == null || self.getClass() != VSBlockEntity.class || this.bons$sanguineLeoTickStatsStack != this.getModelStack())
            return original.call(self);
        int kept = (Integer) stats.second;
        if (SacrificerTickStats.LEO_SHADOW) return SacrificerTickStats.shadow("sanguinenetworks_leo_tick_stats_reuse", kept, original.call(self));
        SacrificerTickStats.reusedOne(true);
        return kept;
    }
}
