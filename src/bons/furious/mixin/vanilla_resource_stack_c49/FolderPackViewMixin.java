package bons.furious.mixin.vanilla_resource_stack_c49;

import bons.furious.patch.vanilla_resource_stack_c49.RsStackViews;
import java.nio.file.Path;
import net.minecraft.server.packs.PathPackResources;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * vanilla_resource_stack_miss_cache (Minecraft 1.20.1; client side): read access to the root of a
 * net.minecraft.server.packs.PathPackResources (a folder pack, or a built-in pack inside the game jar). Nothing else changes.
 */
@Mixin(value = PathPackResources.class, remap = false)
public abstract class FolderPackViewMixin implements RsStackViews.FolderPack {
    @Shadow
    @Final
    private Path f_243919_;

    @Override
    public Path bons$rsStackC49Root() {
        return this.f_243919_;
    }
}
