package bons.furious.mixin.mesh_air;

import bons.furious.patch.mesh_air.BlockStateAirFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * vanilla_block_state_air_flag (Minecraft 1.20.1 with Forge 47.4.16; both sides).
 *
 * Inside BlockStateBase.isAir() (m_60795_, Forge's getBlock().isAir(asState())) the call Block.isAir(BlockState) goes
 * through {@link #bons$rememberAir}: the state keeps the call's first answer in a byte when it cannot change
 * ({@link BlockStateAirFlag#flagFor}) and answers from the byte afterwards, without touching the Block. Unremembered states,
 * the runtime switch off and the shadow check make the original virtual call through {@link BlockBehaviourAirInvoker} (same
 * dispatch, overrides included, no allocation). A plain redirect rather than a WrapOperation: the latter's original call
 * allocates an argument array, and isAir is among the game's hottest methods. No Minecraft code is carried; no other mod's
 * mixin touches this method or call (18 mixins on the class, none on isAir: data/client_mixins.json, neighbours check).
 */
@Mixin(value = BlockBehaviour.BlockStateBase.class, remap = false)
public abstract class BlockStateAirFlagMixin {
    /** 0 = not asked yet, 1 = not air, 2 = air (both remembered), -1 = asked every time (BlockStateAirFlag constants). */
    @Unique
    private byte bons$airFlag;

    @Redirect(method = "m_60795_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;isAir(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean bons$rememberAir(Block block, BlockState state) {
        byte flag = this.bons$airFlag;
        if (flag > 0 && BlockStateAirFlag.enabled) {
            if (!BlockStateAirFlag.SHADOW) return flag == BlockStateAirFlag.AIR;
            boolean asked = ((BlockBehaviourAirInvoker) block).bons$callIsAir(state);      // shadow mode answers with the original call
            BlockStateAirFlag.shadow(state, flag == BlockStateAirFlag.AIR, asked);
            return asked;
        }
        boolean air = ((BlockBehaviourAirInvoker) block).bons$callIsAir(state);
        if (flag == BlockStateAirFlag.UNKNOWN && BlockStateAirFlag.enabled) this.bons$airFlag = BlockStateAirFlag.flagFor(block, state, air);
        return air;
    }
}
