package bons.furious.mixin.supplementaries_lean;

import bons.furious.patch.supplementaries_lean.MapTints;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * supplementaries_map_tint_memo (Supplementaries 1.21.1-4.0.2, server side incl. the integrated server):
 * MapTintColorsHandler.ColorData.markColored, its one MapTintColorsHandler.getSimilarColoredBlock(Block) call (once per
 * updated map pixel, from Supplementaries' MapItemMixin) is answered by MapTints' per-block memo (the same answer for the
 * same holder tag set object; see MapTints). A plain @Redirect: the call runs about a thousand times per tick per held
 * map, and a WrapOperation's original call would allocate. No Supplementaries code is carried: the redirect calls
 * Supplementaries' own getSimilarColoredBlock whenever it does not reuse that call's earlier answer.
 */
@Mixin(targets = "net.mehvahdjukaar.supplementaries.common.misc.map_data.MapTintColorsHandler$ColorData", remap = false)
public abstract class ColorDataTintMemoMixin {
    @Redirect(method = "markColored", at = @At(value = "INVOKE",
            target = "Lnet/mehvahdjukaar/supplementaries/common/misc/map_data/MapTintColorsHandler;getSimilarColoredBlock(Lnet/minecraft/world/level/block/Block;)Lnet/minecraft/world/level/block/Block;"))
    private Block bons$tintMemo(Block block) {
        return MapTints.customColor(block);
    }
}
