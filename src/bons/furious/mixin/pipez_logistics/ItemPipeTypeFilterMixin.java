package bons.furious.mixin.pipez_logistics;

import bons.furious.patch.pipez_logistics.EmptyFilters;
import de.maxhenkel.pipez.Filter;
import de.maxhenkel.pipez.blocks.tileentity.PipeTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.types.ItemPipeType;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * pipez_empty_filter_fast_path (Pipez 1.20.1-1.2.26, All Rights Reserved; server side): ItemPipeType.canInsert answers
 * true at once for an empty filter list (see EmptyFilters for why that is the original's answer); in shadow mode the
 * original runs as a nested call and must answer true. No Pipez code is carried.
 */
@Mixin(value = ItemPipeType.class, remap = false)
public abstract class ItemPipeTypeFilterMixin {
    @Unique
    private static boolean bons$nested;

    @Shadow
    private boolean canInsert(PipeTileEntity.Connection connection, ItemStack stack, List<Filter<?>> filters) {
        throw new AssertionError();
    }

    @Inject(method = "canInsert(Lde/maxhenkel/pipez/blocks/tileentity/PipeTileEntity$Connection;Lnet/minecraft/world/item/ItemStack;Ljava/util/List;)Z",
            at = @At("HEAD"), cancellable = true)
    private void bons$unfiltered(PipeTileEntity.Connection connection, ItemStack stack, List<Filter<?>> filters, CallbackInfoReturnable<Boolean> cir) {
        if (bons$nested || !EmptyFilters.unfiltered(filters)) {
            return;
        }
        if (EmptyFilters.SHADOW) {
            bons$nested = true;
            try {
                EmptyFilters.check(this.canInsert(connection, stack, filters), "ItemPipeType");
            } finally {
                bons$nested = false;
            }
        }
        cir.setReturnValue(true);
    }
}
