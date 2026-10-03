package bons.furious.mixin.jei;

import bons.furious.patch.jei.TypedStackCache;
import com.google.common.cache.LoadingCache;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mezz.jei.common.ingredients.itemStacks.TypedItemStack;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * jei_typed_stack_cache (JEI 15.59.0.212, client only).
 *
 * getIngredient() asked one static Guava cache (expireAfterAccess 1 s, identity keys) for the ingredient's ItemStack;
 * while JEI starts nearly every call is a miss for a new ingredient and every insert walks the cache's expiry queue. The
 * call now goes to TypedStackCache, which keeps the same contract per ingredient in a field added here (see
 * TypedStackCache for the rule-by-rule correspondence with Guava 31.1). With the runtime switch off the original Guava
 * call runs. JEI is MIT; no JEI code is carried.
 */
@Mixin(value = TypedItemStack.class, remap = false)
public abstract class TypedStackCacheMixin implements TypedStackCache.Holder {
    @Unique
    private volatile Object bons$state;

    @Shadow
    protected abstract ItemStack createItemStackUncached();

    @Override
    public Object bons$cacheState() {
        return this.bons$state;
    }

    @Override
    public void bons$cacheState(Object state) {
        this.bons$state = state;
    }

    @Override
    public Object bons$loadUncached() {
        return this.createItemStackUncached();
    }

    @WrapOperation(method = "getIngredient", at = @At(value = "INVOKE", target = "Lcom/google/common/cache/LoadingCache;getUnchecked(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object bons$perIngredient(LoadingCache<Object, Object> cache, Object key, Operation<Object> original) {
        return TypedStackCache.enabled ? TypedStackCache.get(this) : original.call(cache, key);
    }
}
