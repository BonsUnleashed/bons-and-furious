package bons.furious.mixin.distanthorizons_level_wrappers;

import bons.furious.patch.distanthorizons_level_wrappers.WeakFolders;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.seibel.distanthorizons.core.file.structure.ClientOnlySaveStructure;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * distanthorizons_save_folder_weak_keys (Distant Horizons 3.3.2), client only. [FIX]
 *
 * ClientOnlySaveStructure.getSaveFolder is levelWrapperToFileMap.computeIfAbsent(wrapper, DH's function) and toString
 * lists levelWrapperToFileMap.values(); nothing else reads or writes that private map. Both calls are wrapped: they go to
 * this structure's own table of weak wrapper keys and strong File values (helper
 * bons.furious.patch.distanthorizons_level_wrappers.WeakFolders), which answers every call with the same object DH's map
 * would and keeps DH's entry count, hashes and iteration order, but no longer keeps the wrappers (and their ClientLevels)
 * alive. With the switch off both calls go to DH's map unchanged. No Distant Horizons code is carried.
 */
@Mixin(value = ClientOnlySaveStructure.class, remap = false)
public abstract class SaveStructureWeakKeysMixin implements WeakFolders.Owner {
    @Unique
    private volatile Object bons$folders;

    @Override
    public Object bons$folders() {
        return this.bons$folders;
    }

    @Override
    public void bons$setFolders(Object store) {
        this.bons$folders = store;
    }

    @WrapOperation(method = "getSaveFolder", at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/ConcurrentHashMap;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
    @SuppressWarnings({"rawtypes"})
    private Object bons$weakFolder(ConcurrentHashMap map, Object wrapper, Function function, Operation<Object> original) {
        return WeakFolders.computeIfAbsent(this, map, wrapper, function, original);
    }

    @WrapOperation(method = "toString", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/ConcurrentHashMap;values()Ljava/util/Collection;"))
    @SuppressWarnings({"rawtypes"})
    private Collection<?> bons$folderValues(ConcurrentHashMap map, Operation<Collection<?>> original) {
        return WeakFolders.values(this, map, original);
    }
}
