package bons.furious.mixin.vanilla_raycast;

import net.minecraft.world.level.ClipContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * vanilla_raycast_fluid_none (Minecraft 1.20.1 on Forge 47.4.16; both sides): read access to ClipContext.fluid (f_45685_),
 * the context's fluid mode. Nothing else.
 */
@Mixin(value = ClipContext.class, remap = false)
public interface ClipContextAccessor {
    @Accessor(value = "f_45685_", remap = false)
    ClipContext.Fluid bons$fluid();
}
