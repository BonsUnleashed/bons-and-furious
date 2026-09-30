package bons.furious.mixin.valkyrienskies_mod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * valkyrien_entity_base_tick: write access to vs$lastCheckedSealedPos, the sealed-area cache field that VS's
 * entity.MixinEntity adds to Entity. EntityBaseTickMixin resets it as the 1.0.19 patch did; an accessor is located
 * after all mixins' fields are merged, so it can reach a field another mixin added.
 */
@Mixin(value = Entity.class, remap = false)
public interface EntitySealedPosAccess {
    @Accessor("vs$lastCheckedSealedPos")
    void bons$setLastCheckedSealedPos(BlockPos pos);
}
