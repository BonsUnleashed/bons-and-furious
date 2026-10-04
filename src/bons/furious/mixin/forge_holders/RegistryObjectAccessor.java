package bons.furious.mixin.forge_holders;

import net.minecraftforge.registries.RegistryObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * forge_object_holder_pass_index (Forge 47.4.16, both sides): read access to RegistryObject.optionalRegistry (final,
 * set in the constructor), which decides whether its holder handler ever checks that its registry exists. Read only.
 */
@Mixin(value = RegistryObject.class, remap = false)
public interface RegistryObjectAccessor {
    @Accessor(value = "optionalRegistry", remap = false)
    boolean bons$optionalRegistry();
}
