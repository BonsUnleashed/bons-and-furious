package bons.furious.mixin.state_memo;

import bons.furious.patch.state_memo.ParticularWaterlogged;
import com.bawnorton.mixinsquared.TargetHandler;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * particular_chest_waterlogged_memo (Particular 1.2.7, MIT; client): Particular's InjectChestBlockEntity adds the handler
 * randomlyOpen at the end of ChestBlockEntity.lidAnimateTick; after its config check the handler's first block-state
 * read is state.getValue(WATERLOGGED). This redirect (MixinSquared, into the merged handler, after Particular's mixin)
 * answers it through ParticularWaterlogged, the value getValue returned for the same state. The rest of the handler is
 * unchanged. A plain @Redirect (no allocation); no other mixin targets the handler. No code of Particular or Minecraft.
 */
@Mixin(value = ChestBlockEntity.class, priority = 1500, remap = false)
public abstract class ChestBubbleWaterloggedMixin {
    @TargetHandler(mixin = "com.leclowndu93150.particular.mixin.InjectChestBlockEntity", name = "randomlyOpen")
    @Redirect(method = "@MixinSquared:Handler", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;m_61143_(Lnet/minecraft/world/level/block/state/properties/Property;)Ljava/lang/Comparable;",
            ordinal = 0))
    private static Comparable<?> bons$waterlogged(BlockState state, Property<?> property) {
        return ParticularWaterlogged.waterlogged(state, property);
    }
}
