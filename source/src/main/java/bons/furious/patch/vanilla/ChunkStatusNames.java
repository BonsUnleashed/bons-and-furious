package bons.furious.patch.vanilla;

/**
 * vanilla_chunk_status_name_memo (Minecraft 1.20.1, both sides): runtime switch for ChunkStatusNameMixin.
 *
 * ChunkStatus.toString() looks the status up in the chunk-status registry (through Forge's registry wrapper, a
 * HashBiMap reverse lookup) and builds a new string on every call. C2ME's profiling hook on
 * ChunkHolder.getOrScheduleFuture passes that string as an argument for every chunk request that is not finished yet,
 * before its profiler decides whether it records anything. A registered status keeps its registry key forever, so the
 * mixin keeps the first string the original produced and returns it again.
 */
public final class ChunkStatusNames {
    /** -Dbons_and_furious.chunkStatusNameMemo=false runs the original toString on every call. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.chunkStatusNameMemo", "true"));

    private ChunkStatusNames() {
    }
}
