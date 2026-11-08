package bons.furious.mixin.vanilla_resource_stack_c49;

import bons.furious.patch.vanilla_resource_stack_c49.RsStackViews;
import java.util.List;
import net.minecraft.server.packs.CompositePackResources;
import net.minecraft.server.packs.PackResources;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * vanilla_resource_stack_miss_cache (Minecraft 1.21.1, client): read access to the packs a CompositePackResources (a pack
 * with overlays) asks, in its order: the overlays, then the primary pack (packResourcesStack, fixed at construction).
 * Nothing else changes.
 */
@Mixin(value = CompositePackResources.class, remap = false)
public abstract class CompositePackViewMixin implements RsStackViews.CompositePack {
    @Shadow
    @Final
    private List<PackResources> packResourcesStack;

    @Override
    public List<PackResources> bons$rsStackC49Stack() {
        return this.packResourcesStack;
    }
}
