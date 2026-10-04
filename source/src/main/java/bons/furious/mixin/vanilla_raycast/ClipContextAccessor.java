package bons.furious.mixin.vanilla_raycast;

import net.minecraft.world.level.ClipContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * vanilla_raycast_fluid_none (Minecraft 1.21.1 with NeoForge 21.1.252; both sides): read access to ClipContext.fluid,
 * the context's fluid mode. Nothing else.
 *
 * Ported to 1.21.1: Mojang names only; the field is unchanged.
 */
@Mixin(value = ClipContext.class, remap = false)
public interface ClipContextAccessor {
    @Accessor(value = "fluid", remap = false)
    ClipContext.Fluid bons$fluid();
}
