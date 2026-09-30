package bons.furious.mixin.alexscaves;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * alexscaves_magnet_query (Alex's Caves 2.0.2): read access to LivingEntity.jumping for MagnetUtilQueryMixin.
 *
 * Alex's Caves reads the protected field directly because its access transformer makes it public at runtime. Our
 * compiler does not see that access transformer, so the overwritten tickMagnetism reads the same field through this
 * accessor instead.
 */
@Mixin(value = LivingEntity.class, remap = false)
public interface LivingEntityJumpingAccessor {
    @Accessor("f_20899_")
    boolean bons$isJumping();
}
