package bons.furious.mixin.vanilla_item_merge;

import bons.furious.patch.vanilla_item_merge.MergeCandidates;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_item_merge_candidates (Minecraft 1.20.1 on Forge 47.4.16, server side incl. the integrated server):
 * ItemEntity.mergeWithNeighbours (m_32069_) hands Level.getEntitiesOfClass (m_6443_) the switch's candidate filter
 * around vanilla's own predicate (MergeCandidates.Filter): neighbours of another item, and neighbours whose count plus
 * this one's exceeds their maximum, are rejected before vanilla's predicate; everything else is vanilla's predicate.
 * Same list order, same loop. The second wrapper only acts in shadow mode: it checks every real merge
 * (m_32017_ inside tryToMerge, m_32015_) against the entries the filter left out. Both are require = 0: a mod that
 * overwrites these methods leaves the switch out instead of failing the start.
 */
@Mixin(value = ItemEntity.class, remap = false)
public abstract class ItemEntityMergeMixin implements MergeCandidates.ShadowHolder {
    @Unique
    private Set<ItemEntity> bons$rejected;

    @WrapOperation(method = "m_32069_", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"))
    private List<ItemEntity> bons$mergeCandidates(Level level, Class<ItemEntity> type, AABB box, Predicate<? super ItemEntity> vanilla,
                                                  Operation<List<ItemEntity>> original) {
        return MergeCandidates.query((ItemEntity) (Object) this, level, type, box, vanilla, original, this);
    }

    @WrapOperation(method = "m_32015_", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/item/ItemEntity;m_32017_(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;)V"))
    private void bons$shadowMerge(ItemEntity dest, ItemStack destStack, ItemEntity origin, ItemStack originStack, Operation<Void> original) {
        if (MergeCandidates.SHADOW) {
            MergeCandidates.shadowMerge(this, (ItemEntity) (Object) this, dest, origin);
        }
        original.call(dest, destStack, origin, originStack);
    }

    @Override
    public Set<ItemEntity> bons$shadowRejected() {
        return this.bons$rejected;
    }

    @Override
    public void bons$shadowRejected(Set<ItemEntity> rejected) {
        this.bons$rejected = rejected;
    }
}
