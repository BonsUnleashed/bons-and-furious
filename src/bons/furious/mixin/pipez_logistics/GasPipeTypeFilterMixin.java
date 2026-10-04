package bons.furious.mixin.pipez_logistics;

import bons.furious.patch.pipez_logistics.EmptyFilters;
import de.maxhenkel.pipez.Filter;
import de.maxhenkel.pipez.blocks.tileentity.PipeTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.types.GasPipeType;
import java.util.List;
import mekanism.api.chemical.ChemicalStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * pipez_empty_filter_fast_path (Pipez 1.20.1-1.2.26, All Rights Reserved; server side): GasPipeType.canInsert (Mekanism
 * chemicals; the class only loads with Mekanism present) answers true at once for an empty filter list (see
 * EmptyFilters); in shadow mode the original runs as a nested call and must answer true. No Pipez code is carried.
 */
@Mixin(value = GasPipeType.class, remap = false)
public abstract class GasPipeTypeFilterMixin {
    @Unique
    private static boolean bons$nested;

    @Shadow
    private boolean canInsert(PipeTileEntity.Connection connection, ChemicalStack stack, List<Filter<?>> filters) {
        throw new AssertionError();
    }

    @Inject(method = "canInsert(Lde/maxhenkel/pipez/blocks/tileentity/PipeTileEntity$Connection;Lmekanism/api/chemical/ChemicalStack;Ljava/util/List;)Z",
            at = @At("HEAD"), cancellable = true)
    private void bons$unfiltered(PipeTileEntity.Connection connection, ChemicalStack stack, List<Filter<?>> filters, CallbackInfoReturnable<Boolean> cir) {
        if (bons$nested || !EmptyFilters.unfiltered(filters)) {
            return;
        }
        if (EmptyFilters.SHADOW) {
            bons$nested = true;
            try {
                EmptyFilters.check(this.canInsert(connection, stack, filters), "GasPipeType");
            } finally {
                bons$nested = false;
            }
        }
        cir.setReturnValue(true);
    }
}
