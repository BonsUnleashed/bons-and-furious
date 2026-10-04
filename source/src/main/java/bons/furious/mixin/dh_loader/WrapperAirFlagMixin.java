package bons.furious.mixin.dh_loader;

import bons.furious.patch.dh_loader.WrapperAirFlag;
import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper_neoforge;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * distanthorizons_wrapper_air_flag (Distant Horizons 3.3.3 for Minecraft 1.21.1 / NeoForge, LGPL-3.0; both sides).
 *
 * BlockStateWrapper_neoforge.isAir() is DH's {@code return isAir(this.blockState)} (the static helper: null -> true, else
 * BlockState.isAir()) with one change: the wrapper keeps the first answer in a byte when the answer cannot change
 * (see {@link WrapperAirFlag#flagFor}) and returns it from then on, so the data-point loops that ask every wrapper no longer
 * touch the BlockState and Block objects. The blockState field is final; the answer for a remembered wrapper is the
 * state's final isAir field (its block keeps BlockBehaviour.isAir) or the constant true of the AIR wrapper.
 * With the runtime switch off, or for a wrapper whose block overrides isAir, the original expression runs every time.
 * DH is LGPL-3.0, so its one-line method body is carried here. No other mod mixes into this class.
 *
 * Ported to 1.21.1: BlockStateWrapper_forge is BlockStateWrapper_neoforge; DH 3.3.3's isAir() body is byte-identical
 * and the wrapper's constructor still assigns the public final blockState once before its own first isAir() call.
 */
@Mixin(value = BlockStateWrapper_neoforge.class, remap = false)
public abstract class WrapperAirFlagMixin {
    @Shadow
    @Final
    public BlockState blockState;

    /** 0 = not asked yet, 1 = not air, 2 = air (both remembered), -1 = asked every time (WrapperAirFlag constants). */
    @Unique
    private byte bons$airFlag;

    /**
     * @author BonsUnleashed
     * @reason Remember the wrapper's constant isAir answer instead of asking the BlockState (and through it the Block) for
     * every LOD data point.
     */
    @Overwrite
    public boolean isAir() {
        if (WrapperAirFlag.enabled) {
            byte flag = this.bons$airFlag;
            if (flag > 0) {
                if (!WrapperAirFlag.SHADOW) return flag == WrapperAirFlag.AIR;
                boolean asked = BlockStateWrapper_neoforge.isAir(this.blockState);     // shadow mode answers with the original expression
                WrapperAirFlag.shadow(this.blockState, flag == WrapperAirFlag.AIR, asked);
                return asked;
            }
            if (flag == WrapperAirFlag.UNKNOWN) {
                boolean air = BlockStateWrapper_neoforge.isAir(this.blockState);
                this.bons$airFlag = WrapperAirFlag.flagFor(this.blockState, air);
                return air;
            }
        }
        return BlockStateWrapper_neoforge.isAir(this.blockState);
    }
}
