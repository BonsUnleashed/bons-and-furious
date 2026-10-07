package bons.furious.mixin.embeddium_meshing;

import bons.furious.patch.embeddium_meshing.WeightedPickTable;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.WeightedBakedModel;
import net.minecraft.util.random.WeightedEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * embeddium_weighted_pick_table (Minecraft 1.20.1 WeightedBakedModel with Embeddium 0.3.31's WeightedBakedModelMixin,
 * client).
 *
 * Embeddium overwrites getQuads and getRenderTypes and adds embeddium$getInnerModel; all three pick the variant with its
 * merged static getAt(list, value) walk. Applied after Embeddium (priority 1100), this mixin builds a cumulative-weight
 * table at the end of the constructor and answers those getAt calls from it (WeightedPickTable: same entry for every
 * value, including zero weights, the negative Math.abs(Integer.MIN_VALUE) case and values past the total). The random
 * draw stays in Embeddium's code. With the runtime flag off, without a table, or for a list other than the model's own,
 * Embeddium's getAt runs. Minecraft is Mojang's, Embeddium LGPL-3.0: only its call is wrapped, none of its code copied.
 * 1.0.34: require = 0, expect = 0 (was require = 3): Embeddium's WeightedBakedModelMixin can be switched off
 * (mixin.features.model in embeddium-mixins.properties, or a mod's sodium:options override); the model then keeps vanilla's
 * getQuads and getRenderTypes, which have no getAt call, and this wrap finds nothing to change instead of failing the class
 * at load. Each wrapped call is exact on its own, so any number of them may apply.
 */
@Mixin(value = WeightedBakedModel.class, priority = 1100, remap = false)
public abstract class WeightedPickTableMixin {
    @Shadow
    @Final
    private List<WeightedEntry.Wrapper<BakedModel>> list;

    @Shadow
    @Final
    private int totalWeight;

    @Unique
    private WeightedPickTable bons$pickTable;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bons$buildPickTable(List<WeightedEntry.Wrapper<BakedModel>> list, CallbackInfo ci) {
        this.bons$pickTable = WeightedPickTable.build(this.list, this.totalWeight);
    }

    @WrapOperation(method = {"getQuads", "getRenderTypes", "embeddium$getInnerModel"}, require = 0, expect = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/WeightedBakedModel;getAt(Ljava/util/List;I)Lnet/minecraft/util/random/WeightedEntry;"))
    private WeightedEntry bons$tablePick(List<?> pool, int value, Operation<WeightedEntry> original) {
        WeightedPickTable table = this.bons$pickTable;
        if (WeightedPickTable.enabled && table != null && table.list == pool) return table.pick(value);
        return original.call(pool, value);
    }
}
