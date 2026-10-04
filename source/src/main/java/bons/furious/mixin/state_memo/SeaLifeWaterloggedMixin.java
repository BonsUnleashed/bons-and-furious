package bons.furious.mixin.state_memo;

import bons.furious.patch.state_memo.SeaLifeWaterlogged;
import com.ninni.spawn.server.block.entity.SeaLifeDecoBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * spawn_sealife_waterlogged_memo (1.21.1 tested build: Spawn 4.0.8 for NeoForge 1.21.1, All Rights Reserved; both sides,
 * the ticker runs on both): SeaLifeDecoBlockEntity.tick opens with
 * be.getBlockState().getValue(SeaLifeDecoBlock.WATERLOGGED) (its first getValue call). This redirect answers it through
 * SeaLifeWaterlogged, the value getValue returned for the same state. Everything else in tick is unchanged; the five
 * redirects of spawn_sealife_tick (Random, getInterval, the two move vectors, setMovePos) target other calls and compose.
 * A plain @Redirect (no allocation). Only our own logic; no code of Spawn or Minecraft.
 *
 * Ported to 1.21.1: unchanged. Spawn 4.0.8's tick(SeaLifeDecoBlockEntity) still opens with
 * be.getBlockState().getValue(SeaLifeDecoBlock.WATERLOGGED) (ordinal 0; WATERLOGGED = BlockStateProperties.WATERLOGGED)
 * and then returns unless waterlogged, with a level, on the client; the method is not overloaded. Selector with descriptor.
 */
@Mixin(value = SeaLifeDecoBlockEntity.class, remap = false)
public abstract class SeaLifeWaterloggedMixin {
    @Redirect(method = "tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getValue(Lnet/minecraft/world/level/block/state/properties/Property;)Ljava/lang/Comparable;",
            ordinal = 0))
    private static Comparable<?> bons$waterlogged(BlockState state, Property<?> property) {
        return SeaLifeWaterlogged.waterlogged(state, property);
    }
}
