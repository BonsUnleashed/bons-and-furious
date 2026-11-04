package bons.furious.mixin.vanilla_resource_stack_c49;

import bons.furious.patch.vanilla_resource_stack_c49.RsStackViews;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * vanilla_resource_stack_miss_cache (Minecraft 1.20.1; client side): read access to the pack and the filter of a
 * FallbackResourceManager$PackEntry (a package-private record). Nothing else changes.
 */
@Mixin(targets = "net.minecraft.server.packs.resources.FallbackResourceManager$PackEntry", remap = false)
public abstract class PackEntryViewMixin implements RsStackViews.Entry {
    @Shadow
    @Final
    private PackResources f_215433_;

    @Shadow
    @Final
    private Predicate<ResourceLocation> f_215434_;

    @Override
    public PackResources bons$rsStackC49Pack() {
        return this.f_215433_;
    }

    @Override
    public Object bons$rsStackC49Filter() {
        return this.f_215434_;
    }
}
