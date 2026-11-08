package bons.furious.mixin.emf_lean;

import java.nio.file.Path;
import net.minecraft.server.packs.PathPackResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * emf_listed_resource_misses (Minecraft 1.21.1, client): read access to PathPackResources' private root, the folder or the
 * mod jar's root path a pack serves from (NeoForge's ResourcePackLoader.createPackForMod gives each mod jar a
 * PathPackResources over its SecureJar root path). EmfListedMisses only asks which file system that root lies in. Adds no
 * behaviour and carries no Minecraft code.
 */
@Mixin(value = PathPackResources.class, remap = false)
public interface PathPackResourcesRootAccessor {
    @Accessor(value = "root", remap = false)
    Path bons$root();
}
