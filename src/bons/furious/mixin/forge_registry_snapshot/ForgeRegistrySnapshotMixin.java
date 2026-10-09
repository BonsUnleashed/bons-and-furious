package bons.furious.mixin.forge_registry_snapshot;

import bons.furious.patch.forge_registry_snapshot.RegistrySnapshots;
import com.google.common.collect.BiMap;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * forge_registry_snapshot_reuse (Forge 47.4.16, both sides): ForgeRegistry.makeSnapshot during a level save (the mark
 * ForgeHooksSnapshotMixin sets) hands back the Snapshot it built last time while every input of makeSnapshot is unchanged
 * (RegistrySnapshots); at any other time, and on any difference, the original runs. One @Unique slot per registry holds
 * the kept snapshot with the inputs it was built from. Reads the registry's own fields like makeSnapshot does; calls its
 * own getOverrideOwners() once per save, like makeSnapshot. No Forge code is carried.
 */
@Mixin(value = ForgeRegistry.class, remap = false)
public abstract class ForgeRegistrySnapshotMixin<V> {
    @Shadow
    @Final
    private BiMap<Integer, V> ids;
    @Shadow
    @Final
    private Map<ResourceLocation, ResourceLocation> aliases;
    @Shadow
    @Final
    private IntSet blocked;
    @Unique
    private volatile RegistrySnapshots.Kept bons$keptSnapshot;

    @Shadow
    abstract Map<ResourceLocation, String> getOverrideOwners();

    @WrapMethod(method = "makeSnapshot")
    @SuppressWarnings("unchecked")
    private ForgeRegistry.Snapshot bons$reuseSnapshot(Operation<ForgeRegistry.Snapshot> original) {
        if (!RegistrySnapshots.saving()) return original.call();
        return RegistrySnapshots.makeSnapshot((ForgeRegistry<?>) (Object) this, this.ids, this.aliases, this.blocked,
                this.getOverrideOwners(), this.bons$keptSnapshot, k -> this.bons$keptSnapshot = k, original);
    }
}
