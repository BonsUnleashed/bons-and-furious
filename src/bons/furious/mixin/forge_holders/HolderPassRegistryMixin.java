package bons.furious.mixin.forge_holders;

import bons.furious.patch.forge_holders.HolderPassIndex;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ObjectHolderRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * forge_object_holder_pass_index (Forge 47.4.16, both sides).
 *
 * applyObjectHolders(Predicate) runs objectHolders.forEach(holder -> try accept(filter) catch -> aggregate) over every
 * object holder. Its predicate is noted at the start of the method; when it is the very predicate object that
 * HolderPassGameDataMixin announced for a registry pass of GameData.postRegisterEvents, the forEach is replaced by
 * HolderPassIndex.forEach, which calls the same per-holder lambda for exactly the holders whose accept could act for
 * that registry, in the set's iteration order (see HolderPassIndex). Every other call runs the original forEach.
 * addHandler / removeHandler report successful structural changes so the snapshot is rebuilt. Forge is LGPL-2.1; no
 * Forge code is carried.
 */
@Mixin(value = ObjectHolderRegistry.class, remap = false)
public abstract class HolderPassRegistryMixin {
    @Inject(method = "applyObjectHolders(Ljava/util/function/Predicate;)V", at = @At("HEAD"))
    private static void bons$noteFilter(Predicate<ResourceLocation> filter, CallbackInfo ci) {
        HolderPassIndex.noteApply(filter);
    }

    @WrapOperation(method = "applyObjectHolders(Ljava/util/function/Predicate;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Set;forEach(Ljava/util/function/Consumer;)V"))
    private static void bons$indexedPass(Set<?> holders, Consumer<?> action, Operation<Void> original) {
        if (HolderPassIndex.isRegistryPass()) HolderPassIndex.forEach(holders, action);
        else original.call(holders, action);
    }

    @WrapOperation(method = "addHandler", at = @At(value = "INVOKE", target = "Ljava/util/Set;add(Ljava/lang/Object;)Z"))
    private static boolean bons$addedHolder(Set<?> holders, Object holder, Operation<Boolean> original) {
        boolean changed = original.call(holders, holder);
        if (changed) HolderPassIndex.structureChanged();
        return changed;
    }

    @WrapOperation(method = "removeHandler", at = @At(value = "INVOKE", target = "Ljava/util/Set;remove(Ljava/lang/Object;)Z"))
    private static boolean bons$removedHolder(Set<?> holders, Object holder, Operation<Boolean> original) {
        boolean changed = original.call(holders, holder);
        if (changed) HolderPassIndex.structureChanged();
        return changed;
    }
}
