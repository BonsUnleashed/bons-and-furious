package bons.furious.mixin.mesh_air;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * vanilla_block_state_air_flag (Minecraft 1.20.1 with Forge 47.4.16; both sides).
 *
 * Invoker for Forge's protected BlockBehaviour.isAir(BlockState), so that {@link BlockStateAirFlagMixin}'s redirect can make
 * the original virtual call (overrides included) itself. Generated bridge only; no Minecraft code.
 */
@Mixin(value = BlockBehaviour.class, remap = false)
public interface BlockBehaviourAirInvoker {
    @Invoker("isAir")
    boolean bons$callIsAir(BlockState state);
}
