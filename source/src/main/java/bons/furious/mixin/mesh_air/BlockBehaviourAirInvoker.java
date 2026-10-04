package bons.furious.mixin.mesh_air;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * vanilla_block_state_air_flag (Minecraft 1.21.1 with NeoForge 21.1.252; both sides).
 *
 * Invoker for the loader's protected BlockBehaviour.isAir(BlockState), so that {@link BlockStateAirFlagMixin}'s redirect
 * can make the original virtual call (overrides included) itself. Generated bridge only; no Minecraft code.
 *
 * Ported to 1.21.1: unchanged (NeoForge keeps the protected isAir(BlockState)).
 */
@Mixin(value = BlockBehaviour.class, remap = false)
public interface BlockBehaviourAirInvoker {
    @Invoker("isAir")
    boolean bons$callIsAir(BlockState state);
}
