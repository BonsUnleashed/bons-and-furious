package bons.furious.mixin.pipez_logistics;

import bons.furious.patch.pipez_logistics.SortedConnections;
import bons.furious.patch.pipez_logistics.SortedConnectionsState;
import de.maxhenkel.pipez.blocks.tileentity.PipeTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.UpgradeTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.types.PipeType;
import java.util.List;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * pipez_sorted_connections_memo (Pipez, All Rights Reserved; 1.21.1 tested build: pipez-neoforge-1.21.1-1.2.31; server
 * side): UpgradeTileEntity.getSortedConnections returns the order SortedConnections keeps for the tile's unchanged
 * connection list instead of sorting it again through a stream (see SortedConnections for why the list is the original's);
 * RANDOM and anything the helper does not recognise run the original. In shadow mode the original runs as a nested call
 * and both lists are compared. No Pipez code is carried: the injection only adds the per-tile record.
 * Ported to 1.21.1: same method and descriptor in Pipez 1.2.31; only PipeType's generics changed.
 */
@Mixin(value = UpgradeTileEntity.class, remap = false)
public abstract class UpgradeTileEntitySortMixin implements SortedConnectionsState {
    @Unique
    private List<PipeTileEntity.Connection> bons$sortSource;
    @Unique
    private PipeTileEntity.Connection[] bons$sortElements;
    @Unique
    private UpgradeTileEntity.Distribution bons$sortDistribution;
    @Unique
    private PipeTileEntity.Connection[] bons$sortOrder;
    @Unique
    private boolean bons$sortNested;

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "getSortedConnections(Lnet/minecraft/core/Direction;Lde/maxhenkel/pipez/blocks/tileentity/types/PipeType;)Ljava/util/List;",
            at = @At("HEAD"), cancellable = true)
    private void bons$keptOrder(Direction side, PipeType pipeType, CallbackInfoReturnable<List<PipeTileEntity.Connection>> cir) {
        if (this.bons$sortNested) {
            return;
        }
        UpgradeTileEntity self = (UpgradeTileEntity) (Object) this;
        List<PipeTileEntity.Connection> ours = SortedConnections.sorted(self, this, side, pipeType);
        if (ours == null) {
            return;
        }
        if (SortedConnections.SHADOW) {
            List<PipeTileEntity.Connection> original;
            this.bons$sortNested = true;
            try {
                original = self.getSortedConnections(side, pipeType);
            } finally {
                this.bons$sortNested = false;
            }
            SortedConnections.compare(original, ours);
            cir.setReturnValue(original);
            return;
        }
        cir.setReturnValue(ours);
    }

    @Override
    public List<PipeTileEntity.Connection> bons$sortSource() {
        return this.bons$sortSource;
    }

    @Override
    public PipeTileEntity.Connection[] bons$sortSourceElements() {
        return this.bons$sortElements;
    }

    @Override
    public UpgradeTileEntity.Distribution bons$sortDistribution() {
        return this.bons$sortDistribution;
    }

    @Override
    public PipeTileEntity.Connection[] bons$sortResult() {
        return this.bons$sortOrder;
    }

    @Override
    public void bons$sortRemember(List<PipeTileEntity.Connection> source, PipeTileEntity.Connection[] elements,
                                  UpgradeTileEntity.Distribution distribution, PipeTileEntity.Connection[] sorted) {
        this.bons$sortSource = source;
        this.bons$sortElements = elements;
        this.bons$sortDistribution = distribution;
        this.bons$sortOrder = sorted;
    }
}
