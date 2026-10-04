package bons.furious.patch.chunk_tick;

import com.mojang.logging.LogUtils;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_ticking_chunk_memo (Minecraft 1.21.1 with NeoForge 21.1.252; server side, including
 * the integrated server). Mojang member names.
 *
 * ServerChunkCache.tickChunks asks every chunk holder of the level for its ticking chunk every tick
 * (ChunkHolder.getTickingChunk): read the holder's volatile tickingChunkFuture, getNow(UNLOADED_LEVEL_CHUNK), and take
 * the chunk out of the ChunkResult (orElse(null)). The future and the result it completed with are two more objects per
 * holder per tick, both usually cache misses (in the 1.20.1 recording CompletableFuture.reportJoin was 4.2% self time of
 * the integrated server thread, getTickingChunk 3.1-4.4%).
 *
 * The holder keeps the last future it answered for and that answer in two fields of its own, so a repeated call reads
 * only the holder. A call whose holder still has that same future object answers from the fields; any other call runs
 * the method and then records its answer, but only when that is provably the method's answer for that future from now on:
 *  - the future is a plain CompletableFuture (not a subclass, whose methods could behave otherwise) that completed
 *    normally before the call; a completed CompletableFuture's result never changes (complete() on a done future is a
 *    no-op). Assumption: no mod calls obtrudeValue/obtrudeException on a chunk holder's futures (the only calls that
 *    rewrite a completed future; neither Minecraft, NeoForge nor any pinned 1.21.1 target jar or its jar-in-jar calls
 *    either, except the bundled Caffeine 3.2.1 library, on its own cache futures);
 *  - its result is one of the game's own ChunkResult records (ChunkResult.Success or ChunkResult.Fail; records are
 *    final), whose orElse answers the same every time: Success its final value, Fail the argument (null). Any other
 *    ChunkResult implementation, or a null result (the method then throws a NullPointerException), is never recorded;
 *  - the holder's field held that same future before and after the call;
 *  - the answer is that result's orElse(null), re-read from the future itself, so the method computed it from that
 *    future (this also covers a field that changed and changed back during the call).
 * An incomplete or exceptionally completed future is never recorded: every call runs the method (and throws as it does).
 * A replaced future simply misses (identity check), so a record can never answer for another future; an old record stays
 * correct for its own future (that is how the shared UNLOADED_LEVEL_CHUNK_FUTURE, which comes back, is answered).
 *
 * Threads: the two fields are read and written only by the server thread of the holder's level (bons$memoOwner, set
 * once by that thread, the first time it records an answer). Any other thread runs the method, so there is no data race
 * on the record; a non-server thread can never see itself as the owner.
 *
 * Holders of any class other than ChunkHolder itself run the method on every call (the mixin checks the exact class):
 * a subclass can override getTickingChunkFuture, and then the method's future is not the field the record is keyed on.
 *
 * Stands down (the method runs on every call) when C2ME's chunk system rewrite is installed (mod id
 * c2me_rewrites_chunk_system, or the all-in-one c2me jar that bundles it; its ModuleEntryPoint.enabled is the
 * compile-time constant true in the pinned 0.4.0-alpha.0.122, so in practice whenever C2ME is installed): its chunk
 * holders are NewChunkHolderVanillaInterface, a ChunkHolder subclass with its own getTickingChunk and
 * getTickingChunkFuture. Presence alone decides; the module's flag is not read (reading it reflectively could run
 * C2ME's module initializer, which registers its config).
 *
 * -Dbons_and_furious.tickingChunkMemo=false runs the method on every call.
 * -Dbons_and_furious.tickingChunkMemo.shadow=true (verification runs only) runs the method on every call and compares
 * the recorded answer with it wherever the record would have answered (SHADOW_CHECKS / SHADOW_MISMATCHES, first 20
 * logged).
 *
 * Ported to 1.21.1: the future holds a ChunkResult instead of an Either (getNow(UNLOADED_LEVEL_CHUNK).orElse(null)
 * instead of getNow(null) and left()); only the game's two final ChunkResult records are recorded (Either could not be
 * subclassed, ChunkResult is an interface); holders that are not exactly ChunkHolder run the method; the C2ME stand-down
 * was re-derived for C2ME 0.4.0-alpha.0.122 (identifier and annotation scan of its 19 module jars, its code not opened):
 * no module mixes into getTickingChunk or getTickingChunkFuture any more; the no-tick view distance module (the 1.20.1
 * reason) now only redirects the calls of getTickingChunk in ServerChunkCache.tickChunks, blockChanged,
 * sectionLightChanged and getChunkToSend, which cannot change what the method answers, so it no longer stands the switch
 * down; the chunk system rewrite (above) does. ModernFix 5.27.24 for 1.21.1 no longer overwrites the method.
 */
public final class TickingChunkMemo {
    private static final Logger LOGGER = LogUtils.getLogger();
    /**
     * C2ME mod ids whose presence stands the switch down: mod id, why. The module's own id first; the all-in-one jar's id
     * as well (the pinned jar bundles that module; an untested C2ME build is treated the same way, conservatively).
     */
    private static final String[][] C2ME_MODULES = {
            {"c2me_rewrites_chunk_system",
                    "C2ME's chunk system rewrite (always on in C2ME 0.4.0-alpha.0.122) replaces the chunk holders with a ChunkHolder subclass that has its own getTickingChunk"},
            {"c2me",
                    "C2ME is installed, and its chunk system rewrite (bundled and always on in 0.4.0-alpha.0.122) replaces the chunk holders with a ChunkHolder subclass that has its own getTickingChunk"}};
    public static volatile boolean enabled = initiallyEnabled();
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.tickingChunkMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced, otherFutureNoted, otherResultNoted;

    private TickingChunkMemo() {
    }

    private static boolean initiallyEnabled() {
        if ("false".equalsIgnoreCase(System.getProperty("bons_and_furious.tickingChunkMemo", "true"))) return false;
        String why = c2meStandDown();
        if (why != null) {
            LOGGER.info("Bons and Furious: vanilla_ticking_chunk_memo stands down: {}; getTickingChunk runs unchanged", why);
            return false;
        }
        return true;
    }

    /** Why an installed C2ME module stands the switch down, or null when none does. */
    static String c2meStandDown() {
        try {
            var mods = net.neoforged.fml.loading.LoadingModList.get();
            if (mods == null) return null;
            for (String[] m : C2ME_MODULES) {
                if (mods.getModFileById(m[0]) != null) return m[1] + " (" + m[0] + " is installed)";
            }
            return null;
        } catch (Throwable t) {
            return "could not check whether C2ME's chunk system rewrite is installed (" + t + ")";
        }
    }

    /**
     * After the method ran: true when its answer may be recorded for {@code before}. before/after = the holder's future
     * read before and after the call, answer = what the method returned.
     */
    public static boolean provable(CompletableFuture<?> before, CompletableFuture<?> after, LevelChunk answer) {
        if (before != after || before == null) return false;
        if (before.getClass() != CompletableFuture.class) {
            if (!otherFutureNoted) {
                otherFutureNoted = true;
                LOGGER.info("Bons and Furious: vanilla_ticking_chunk_memo: a ticking-chunk future of class {} is never recorded (that holder runs getTickingChunk as usual)",
                        before.getClass().getName());
            }
            return false;
        }
        if (!before.isDone() || before.isCompletedExceptionally()) return false;
        Object result = before.getNow(null);
        if (!(result instanceof ChunkResult.Success<?>) && !(result instanceof ChunkResult.Fail<?>)) {
            if (result != null && !otherResultNoted) {
                otherResultNoted = true;
                LOGGER.info("Bons and Furious: vanilla_ticking_chunk_memo: a ticking-chunk result of class {} is never recorded (that holder runs getTickingChunk as usual)",
                        result.getClass().getName());
            }
            return false;
        }
        if (((ChunkResult<?>) result).orElse(null) != answer) return false;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_ticking_chunk_memo applies (each chunk holder remembers its ticking chunk until its future changes){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return true;
    }

    /** True when {@code self} is the server thread of the holder's level (the only thread that may own its record). */
    public static boolean isServerThread(LevelHeightAccessor accessor, Thread self) {
        if (!(accessor instanceof ServerLevel level)) return false;
        MinecraftServer server = level.getServer();
        return server != null && server.getRunningThread() == self;
    }

    /** Shadow mode: the method's answer where the record would have answered. */
    public static void shadow(LevelChunk recorded, LevelChunk answer) {
        SHADOW_CHECKS.incrementAndGet();
        if (recorded == answer) return;
        long m = SHADOW_MISMATCHES.incrementAndGet();
        if (m <= 20) {
            LOGGER.warn("Bons and Furious: vanilla_ticking_chunk_memo shadow mismatch #{}: the record holds {} where getTickingChunk returned {}",
                    m, recorded, answer);
        }
    }
}
