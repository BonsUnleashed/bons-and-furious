package bons.furious.mixin.state_memo;

import bons.furious.patch.state_memo.ParticularWaterlogged;
import com.bawnorton.mixinsquared.TargetHandler;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * particular_chest_waterlogged_memo (Particular 1.2.7, MIT; client): the ender-chest twin of ChestBubbleWaterloggedMixin.
 * Particular's InjectEnderChestBlockEntity.randomlyOpen (end of EnderChestBlockEntity.lidAnimateTick) first reads
 * state.getValue(WATERLOGGED) after its config check; answered through ParticularWaterlogged. Enhanced Block Entities'
 * inject into the same lidAnimateTick is a separate handler and is untouched. No code of Particular or Minecraft.
 */
@Mixin(value = EnderChestBlockEntity.class, priority = 1500, remap = false)
public abstract class EnderChestBubbleWaterloggedMixin {
    @TargetHandler(mixin = "com.leclowndu93150.particular.mixin.InjectEnderChestBlockEntity", name = "randomlyOpen")
    @Redirect(method = "@MixinSquared:Handler", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;m_61143_(Lnet/minecraft/world/level/block/state/properties/Property;)Ljava/lang/Comparable;",
            ordinal = 0))
    private static Comparable<?> bons$waterlogged(BlockState state, Property<?> property) {
        return ParticularWaterlogged.waterlogged(state, property);
    }
}
