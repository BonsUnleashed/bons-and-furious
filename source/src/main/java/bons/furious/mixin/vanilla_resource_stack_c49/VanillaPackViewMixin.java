package bons.furious.mixin.vanilla_resource_stack_c49;

import bons.furious.patch.vanilla_resource_stack_c49.RsStackViews;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * vanilla_resource_stack_miss_cache (Minecraft 1.21.1, client): read access to the root paths of the built-in
 * vanilla pack. Nothing else changes.
 */
@Mixin(value = VanillaPackResources.class, remap = false)
public abstract class VanillaPackViewMixin implements RsStackViews.VanillaPack {
    @Shadow
    @Final
    private List<Path> rootPaths;

    @Shadow
    @Final
    private Map<PackType, List<Path>> pathsForType;

    @Override
    public List<Path> bons$rsStackC49Roots() {
        return this.rootPaths;
    }

    @Override
    public Map<PackType, List<Path>> bons$rsStackC49TypePaths() {
        return this.pathsForType;
    }
}
