package bons.furious.patch.chunk_tick;

import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_ticking_chunk_memo (Minecraft 1.20.1 on Forge 47.4.16, with or without ModernFix's
 * perf.ticking_chunk_alloc overwrite of the method; server side, including the integrated server). SRG member names.
 *
 * ServerChunkCache.tickChunks asks every chunk holder of the level for its ticking chunk every tick
 * (ChunkHolder.getTickingChunk): read the holder's volatile tickingChunkFuture, getNow(null), and take the left side of
 * the Either (the LevelChunk) or null. The future and the Either it completed with are two more objects per holder per
 * tick, both usually cache misses (CompletableFuture.reportJoin self time 4.2% of the integrated server thread in the
 * 1.0.26 recording; getTickingChunk 3.1-4.4%).
 *
 * The holder now keeps the last future it answered for and that answer in two fields of its own, so a repeated call reads
 * only the holder. A call whose holder still has that same future object answers from the fields; any other call runs
 * the method and then records its answer, but only when that is provably the method's answer for that future from now on:
 *  - the future is a plain CompletableFuture (not a subclass, whose methods could behave otherwise) that completed
 *    normally before the call; a completed CompletableFuture's result never changes (complete() on a done future is a
 *    no-op). Assumption: no mod calls obtrudeValue/obtrudeException on a chunk holder's futures (the only calls that
 *    rewrite a completed future; no jar in this pack, nor Minecraft or Forge, calls either at all);
 *  - the holder's field held that same future before and after the call;
 *  - the answer is the left side of that future's result, or null for a right side or a null result (re-read from the
 *    future itself), so the method computed it from that future (this also covers a field that changed and changed back
 *    during the call).
 * An incomplete or exceptionally completed future is never recorded: every call runs the method (and throws as it does).
 * A replaced future simply misses (identity check), so a record can never answer for another future; an old record stays
 * correct for its own future (that is how the shared UNLOADED future, which comes back, is answered).
 *
 * Threads: the two fields are read and written only by the server thread of the holder's level (bons$memoOwner, set
 * once by that thread, the first time it records an answer). Any other thread runs the method, so there is no data race
 * on the record; a non-server thread can never see itself as the owner.
 *
 * Stands down (the method runs on every call) when C2ME's no-tick view distance module is on (installed and
 * [noTickViewDistance] enabled not false in config/c2me.toml, default on): it mixes into getTickingChunk itself.
 *
 * -Dbons_and_furious.tickingChunkMemo=false runs the method on every call.
 * -Dbons_and_furious.tickingChunkMemo.shadow=true (verification runs only) runs the method on every call and compares
 * the recorded answer with it wherever the record would have answered (SHADOW_CHECKS / SHADOW_MISMATCHES, first 20
 * logged).
 */
public final class TickingChunkMemo {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = initiallyEnabled();
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.tickingChunkMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced, otherFutureNoted;

    private TickingChunkMemo() {
    }

    private static boolean initiallyEnabled() {
        if ("false".equalsIgnoreCase(System.getProperty("bons_and_furious.tickingChunkMemo", "true"))) return false;
        String why = c2meNoTickViewDistance();
        if (why != null) {
            LOGGER.info("Bons and Furious: vanilla_ticking_chunk_memo stands down: {}; getTickingChunk runs unchanged", why);
            return false;
        }
        return true;
    }

    /** Why C2ME's no-tick view distance module (which mixes into getTickingChunk) is on, or null when it is not. */
    static String c2meNoTickViewDistance() {
        try {
            var mods = net.minecraftforge.fml.loading.LoadingModList.get();
            if (mods == null || mods.getModFileById("c2me_notickvd") == null) return null;
            Path cfg = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("c2me.toml");
            String value = null;
            if (Files.isRegularFile(cfg)) {
                boolean inSection = false;
                for (String raw : Files.readAllLines(cfg, StandardCharsets.UTF_8)) {
                    String line = raw.replace("﻿", "").trim();
                    if (line.startsWith("[")) {
                        inSection = line.replace(" ", "").equals("[noTickViewDistance]");
                        continue;
                    }
                    if (!inSection || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq > 0 && line.substring(0, eq).trim().equals("enabled")) {
                        value = line.substring(eq + 1).trim();
                        int hash = value.indexOf('#');
                        if (hash >= 0) value = value.substring(0, hash).trim();
                        break;
                    }
                }
            }
            return "false".equals(value) ? null : "C2ME's no-tick view distance module is on (c2me.toml noTickViewDistance.enabled = "
                    + (value == null ? "default" : value) + ")";
        } catch (Throwable t) {
            return "could not check C2ME's no-tick view distance setting (" + t + ")";
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
        Object left = result instanceof Either<?, ?> either ? either.left().orElse(null) : null;
        if (left != answer) return false;
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
        MinecraftServer server = level.m_7654_();
        return server != null && server.m_6304_() == self;
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
