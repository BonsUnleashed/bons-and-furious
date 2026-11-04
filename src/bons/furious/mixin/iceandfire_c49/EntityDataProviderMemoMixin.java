package bons.furious.mixin.iceandfire_c49;

import bons.furious.patch.iceandfire_c49.EntityDataMemo;
import com.github.alexthe666.iceandfire.entity.props.EntityData;
import com.github.alexthe666.iceandfire.entity.props.EntityDataProvider;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * iceandfire_entity_data_memo (Ice and Fire 2.1.13-1.20.1-beta-5; both sides; since 1.0.49).
 *
 * getCapability(Entity) is wrapped: a living entity whose memo is valid for its id and side map gets the memoised
 * LazyOptional, every other call runs the original (bons.furious.patch.iceandfire_c49.EntityDataMemo.lookup). The three
 * Map.remove calls (removeCachedEntry x2, the invalidation listener lambda$getCapability$3) and the Map.clear call
 * (removeCachedEntry, local player leaving) invalidate the memo of that id / map first and then run unchanged. Why the
 * answers are identical: EntityDataMemo. Our own logic only; no Ice and Fire code is carried.
 */
@Mixin(value = EntityDataProvider.class, remap = false)
public abstract class EntityDataProviderMemoMixin {
    @WrapMethod(method = "getCapability(Lnet/minecraft/world/entity/Entity;)Lnet/minecraftforge/common/util/LazyOptional;", remap = false)
    private static LazyOptional<EntityData> bons$memoLookup(Entity entity, Operation<LazyOptional<EntityData>> original) {
        return EntityDataMemo.lookup(entity, original);
    }

    @WrapOperation(method = "removeCachedEntry(Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;", remap = false),
            require = 2, allow = 2, remap = false)
    private static Object bons$memoRemoved(Map<Object, Object> map, Object key, Operation<Object> original) {
        EntityDataMemo.removed(map, key);
        return original.call(map, key);
    }

    @WrapOperation(method = "removeCachedEntry(Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;clear()V", remap = false), require = 1, allow = 1, remap = false)
    private static void bons$memoCleared(Map<Object, Object> map, Operation<Void> original) {
        EntityDataMemo.cleared(map);
        original.call(map);
    }

    @WrapOperation(method = "lambda$getCapability$3(Ljava/util/Map;ILnet/minecraftforge/common/util/LazyOptional;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;", remap = false),
            require = 1, allow = 1, remap = false)
    private static Object bons$memoListenerRemoved(Map<Object, Object> map, Object key, Operation<Object> original) {
        EntityDataMemo.removed(map, key);
        return original.call(map, key);
    }
}
