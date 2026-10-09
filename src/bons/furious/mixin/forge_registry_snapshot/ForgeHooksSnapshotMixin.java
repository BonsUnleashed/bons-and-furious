package bons.furious.mixin.forge_registry_snapshot;

import bons.furious.patch.forge_registry_snapshot.RegistrySnapshots;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.registries.RegistryManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * forge_registry_snapshot_reuse (Forge 47.4.16, both sides): ForgeHooks.writeAdditionalLevelSaveData's
 * RegistryManager.takeSnapshot(true) call runs with a thread-local "saving" mark (RegistrySnapshots.takeSnapshotForSave),
 * so ForgeRegistry.makeSnapshot knows its snapshot is only written into level.dat. The call itself is the original. No
 * Forge code is carried.
 */
@Mixin(value = ForgeHooks.class, remap = false)
public abstract class ForgeHooksSnapshotMixin {
    @WrapOperation(method = "writeAdditionalLevelSaveData", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/registries/RegistryManager;takeSnapshot(Z)Ljava/util/Map;"))
    @SuppressWarnings("rawtypes")
    private static Map bons$markSave(RegistryManager manager, boolean savingToDisc, Operation<Map> original) {
        return RegistrySnapshots.takeSnapshotForSave(manager, savingToDisc, original);
    }
}
