package bons.furious.patch.pipez_logistics;

import de.maxhenkel.pipez.blocks.tileentity.PipeTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.UpgradeTileEntity;
import java.util.List;

/**
 * pipez_sorted_connections_memo (Pipez 1.20.1-1.2.26): what UpgradeTileEntitySortMixin adds to
 * de.maxhenkel.pipez.blocks.tileentity.UpgradeTileEntity - the last connection list the tile sorted (its identity and its
 * elements in their order), the distribution it was sorted for and the sorted order. No Pipez code is carried.
 */
public interface SortedConnectionsState {
    List<PipeTileEntity.Connection> bons$sortSource();

    PipeTileEntity.Connection[] bons$sortSourceElements();

    UpgradeTileEntity.Distribution bons$sortDistribution();

    PipeTileEntity.Connection[] bons$sortResult();

    void bons$sortRemember(List<PipeTileEntity.Connection> source, PipeTileEntity.Connection[] elements,
                           UpgradeTileEntity.Distribution distribution, PipeTileEntity.Connection[] sorted);
}
