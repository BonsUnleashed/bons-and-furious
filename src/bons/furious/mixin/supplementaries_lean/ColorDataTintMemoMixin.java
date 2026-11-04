package bons.furious.mixin.supplementaries_lean;

import bons.furious.patch.supplementaries_lean.MapTints;
import net.mehvahdjukaar.supplementaries.common.misc.ColoredMapHandler;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * supplementaries_map_tint_memo (Supplementaries 1.20-3.1.41, server side incl. the integrated server):
 * ColoredMapHandler.ColorData.markColored, its one ColoredMapHandler.getCustomColor(Block) call (once per updated map
 * pixel) is answered by MapTints' per-block memo (the same answer for the same holder tag set object; see MapTints). A
 * plain @Redirect: the call runs about a thousand times per tick per held map, and a WrapOperation's original call would
 * allocate. No other mod's mixin targets this class (both mixin indexes, 2026-10-28). No Supplementaries code is carried:
 * the redirect calls Supplementaries' own getCustomColor whenever it does not reuse that call's earlier answer.
 */
@Mixin(value = ColoredMapHandler.ColorData.class, remap = false)
public abstract class ColorDataTintMemoMixin {
    @Redirect(method = "markColored", at = @At(value = "INVOKE",
            target = "Lnet/mehvahdjukaar/supplementaries/common/misc/ColoredMapHandler;getCustomColor(Lnet/minecraft/world/level/block/Block;)Lnet/minecraft/world/level/block/Block;"))
    private Block bons$tintMemo(Block block) {
        return MapTints.customColor(block);
    }
}
