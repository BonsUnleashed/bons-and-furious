package bons.furious.patch.sanguinenetworks_coo49;

import org.apache.logging.log4j.LogManager;

/**
 * Bons and Furious switch sanguinenetworks_client_sync_no_remesh (Sanguine Neural Networks 1.21.1-2.0.1), since 1.0.49,
 * client side in effect (exact variant). Helper of SacrificerClientSyncMixin; no Sanguine Neural Networks code is carried.
 *
 * What the mod does. VSBlockEntity.sync() marks the block entity changed and calls level.sendBlockUpdated(pos, state, state,
 * 3) with the block's own unchanged state. The item handler calls sync() from onContentsChanged on both sides; on the
 * client (every slot update the server sends while the sacrificer's screen is open) ClientLevel.sendBlockUpdated asks the
 * level renderer to mark the 3 x 3 x 3 blocks around it for a rebuild (LevelRenderer.blockChanged -> setBlockDirty): up to
 * eight chunk sections are meshed again.
 *
 * What the switch does (exact variant). On a client level only, sync() keeps setChanged() and leaves the sendBlockUpdated
 * call out. The block state handed over is the block's current one for both old and new, VSBlockEntity supplies no model
 * data (no getModelData override) and the sacrificer's block model does not read the block entity, so the sections the call
 * would rebuild are identical to the ones on screen. Labelled difference: identical sections are not meshed again. On the
 * server (and the integrated server) nothing changes. Runtime flag bons_and_furious.sanguinenetworksClientSyncNoRemesh.
 */
public final class SacrificerClientSync {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.sanguinenetworksClientSyncNoRemesh", "true"));
    /** Statistics for probes and the harness (plain field): client section rebuilds left out. */
    public static long skipped;
    private static volatile boolean announced;

    private SacrificerClientSync() {
    }

    /** One client sendBlockUpdated left out. */
    public static void skipped() {
        skipped++;
        if (!announced) {
            announced = true;
            LogManager.getLogger("Bons and Furious").info("Bons and Furious: sanguinenetworks_client_sync_no_remesh applies (a Virtual Sacrificer's slot change no longer rebuilds the client's chunk sections)");
        }
    }
}
