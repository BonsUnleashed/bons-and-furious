package bons.furious.mixin.forge_mod_resources_index;

import bons.furious.patch.forge_mod_resources_index.ModResourcesIndex;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraftforge.resource.DelegatingPackResources;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * forge_mod_resources_namespace_index (Forge 47.4.16 on Minecraft 1.20.1, client only: Forge builds the "mod_resources" pack only
 * for client resource packs; servers register every mod file as its own data pack): DelegatingPackResources.listResources
 * (m_8031_), the delegates.iterator() call of its loop. ModResourcesIndex hands the loop an iterator over the namespace's own
 * delegates (namespacesAssets / namespacesData, Forge's own map, in delegate order) when every delegate is a Forge mod-file pack
 * and the path is valid; the loop body (delegate.listResources with the same arguments and output) is unchanged. Our
 * TrackworkResources @ModifyVariable of the output at HEAD and CreativeCore's invoker are untouched. Priority 1500: a mod that
 * replaced listResources with an @Overwrite would otherwise stop the game at start; with require = 0 and that mod's mixin
 * untested (Neighbours) the switch then steps aside.
 */
@Mixin(value = DelegatingPackResources.class, remap = false, priority = 1500)
public abstract class DelegatingPackResourcesIndexMixin {
    @Shadow
    @Final
    private List<PackResources> delegates;
    @Shadow
    @Final
    private Map<String, List<PackResources>> namespacesAssets;
    @Shadow
    @Final
    private Map<String, List<PackResources>> namespacesData;
    /** 0 = not checked yet, 1 = every delegate is a Forge mod-file pack, 2 = not. */
    @Unique
    private volatile int bons$modFilePacksOnly;

    /** The target method's own parameters follow the Operation (no MixinExtras sugar, so no LocalRef objects per call). */
    @WrapOperation(method = "m_8031_", require = 0, at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"))
    private Iterator<PackResources> bons$namespaceDelegates(List<PackResources> list, Operation<Iterator<PackResources>> original,
                                                          PackType type, String namespace, String path, PackResources.ResourceOutput output) {
        int only = this.bons$modFilePacksOnly;
        if (only == 0) {
            this.bons$modFilePacksOnly = only = ModResourcesIndex.modFilePacksOnly(this.delegates) ? 1 : 2;
            if (only == 2) ModResourcesIndex.notEligible(this, this.delegates);
        }
        return ModResourcesIndex.delegates(list, this.delegates, this.namespacesAssets, this.namespacesData, only == 1, type, namespace, path, original);
    }
}
