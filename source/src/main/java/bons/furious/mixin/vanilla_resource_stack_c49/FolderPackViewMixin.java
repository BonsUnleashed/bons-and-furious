package bons.furious.mixin.vanilla_resource_stack_c49;

import bons.furious.patch.vanilla_resource_stack_c49.RsStackViews;
import java.nio.file.Path;
import net.minecraft.server.packs.PathPackResources;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * vanilla_resource_stack_miss_cache (Minecraft 1.21.1, client): read access to the root of a
 * net.minecraft.server.packs.PathPackResources (a folder pack, a mod jar's pack over its union file system root, or a
 * built-in pack inside the game's jars). Nothing else changes.
 */
@Mixin(value = PathPackResources.class, remap = false)
public abstract class FolderPackViewMixin implements RsStackViews.FolderPack {
    @Shadow
    @Final
    private Path root;

    @Override
    public Path bons$rsStackC49Root() {
        return this.root;
    }
}
