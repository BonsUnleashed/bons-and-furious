package bons.furious.patch.vanilla_chunk_codecs;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.slf4j.Logger;

/**
 * Group vanilla_chunk_codecs, since 1.0.49: what the group keeps about a server's biome registry (BiomeCodecMemo's last
 * codec, PaletteDirectNbt's verified biome names) is let go when that server has stopped (ServerStoppedEvent: after its
 * last chunk was written). In single player every world opened builds its own biome registry, and the kept objects held
 * the closed world's registries (biomes, features, carvers) in memory: until another world wrote a chunk (the codec
 * memo), or for the whole session (the biome names, one table for every world). A dedicated server stops once; nothing
 * changes there. Registered once, on the first use of either memo.
 */
final class CodecMemoLifecycle {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile boolean hooked;

    private CodecMemoLifecycle() {
    }

    static void hook() {
        if (hooked) return;
        synchronized (CodecMemoLifecycle.class) {
            if (hooked) return;
            hooked = true;
            try {
                MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ServerStoppedEvent.class, CodecMemoLifecycle::onServerStopped);
            } catch (Throwable t) {
                LOGGER.warn("Bons and Furious: vanilla_chunk_codecs could not listen for the server's stop ({}); a closed world's biome registry "
                        + "may stay in memory", t.toString());
            }
        }
    }

    static void onServerStopped(ServerStoppedEvent event) {
        BiomeCodecMemo.forget();
        PaletteDirectNbt.forget();
    }
}
