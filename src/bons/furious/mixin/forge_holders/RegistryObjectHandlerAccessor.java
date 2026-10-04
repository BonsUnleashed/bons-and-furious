package bons.furious.mixin.forge_holders;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.RegistryObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * forge_object_holder_pass_index (Forge 47.4.16, both sides): read access to the object-holder handler every
 * RegistryObject made by RegistryObject.create(name, registryName, modid) registers (the anonymous class
 * RegistryObject$1, i.e. every DeferredRegister entry). Only reads its fields; nothing in the class changes.
 * The fields: registryExists / invalidRegistry (set to true once, inside accept), the captured registry name and the
 * owning RegistryObject (both final). Used by bons.furious.patch.forge_holders.HolderPassIndex to decide when the
 * handler's accept is a no-op for a registry pass.
 */
@Mixin(targets = "net.minecraftforge.registries.RegistryObject$1", remap = false)
public interface RegistryObjectHandlerAccessor {
    @Accessor(value = "registryExists", remap = false)
    boolean bons$registryExists();

    @Accessor(value = "invalidRegistry", remap = false)
    boolean bons$invalidRegistry();

    @Accessor(value = "val$registryName", remap = false)
    ResourceLocation bons$registryName();

    @Accessor(value = "this$0", remap = false)
    RegistryObject<?> bons$owner();
}
