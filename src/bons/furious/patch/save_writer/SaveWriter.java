package bons.furious.patch.save_writer;

import com.mojang.logging.LogUtils;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import net.minecraft.FileUtil;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * Bons and Furious switches vanilla_background_saves and vanilla_background_level_dat (Minecraft 1.20.1 on Forge 47.4.16,
 * both sides; acts on the server thread of a running server, dedicated or integrated). SRG member names.
 *
 * An autosave on the server thread builds every dirty saved-data file (DimensionDataStorage.save -> SavedData.save(File)),
 * every player's .dat, advancements and stats file, and level.dat, and also gzips and writes each of them before the tick
 * can go on. On a reference server with one player one autosave held the server thread 588 ms, of which the Deflater and
 * the file system took about 165 ms for the saved data, about 80 ms for level.dat and about 10 ms for the player files.
 *
 * With the switch, the server thread still builds every file's content exactly as before (the NBT tag, serialized to its
 * uncompressed bytes by NbtIo.write; the advancements JSON text; the stats JSON text), at the same moment, and hands it
 * to one writer thread, which does what Minecraft's own code would have done with it: gzip it with the same stream stack
 * (NbtRecording), create the same temporary file and replace the old file through Util.safeReplaceFile, or write the
 * text with the same writer. Jobs run strictly one after the other in submission order, so a later save of a file always
 * lands after an earlier one. Files get the same bytes; they reach the disk a moment later.
 *
 * Waits (drain): every read through Minecraft's loaders (player data, advancements, stats, saved data, NbtIo's file
 * readers, level.dat readers of LevelStorageAccess) and every save that stays synchronous first waits until all
 * submitted jobs are written, so nothing in the game can read an older file than it would have. saveAllChunks with flush
 * (/save-all flush, server stop) and ServerLevel.save with flush wait too, and so do LevelStorageAccess.close (the world
 * lock), deleteLevel, makeWorldBackup and renameLevel. A JVM shutdown hook writes what is still queued (up to 60 s).
 *
 * Synchronous (Minecraft's code, after a wait) whenever: the switch is off; the call is not on the thread of a running
 * server (server stop runs after the server stopped running, so everything it saves is written at once); a SavedData
 * class declares its own save(File) (Mekanism, Create, Refined Storage, Structure Gel in this pack: custom file
 * handling around the write); PlayerEvent.SaveToFile has a listener (Forge fires it after the player file is written;
 * none in this pack).
 *
 * Failures: the writer catches what Minecraft's method catches and logs it with Minecraft's logger, level, message and
 * exception (from the writer thread). Where Minecraft would let an unchecked exception escape a save (an I/O error in the
 * middle of an advancements file surfaces there as JsonIOException), the writer logs it instead.
 *
 * -Dbons_and_furious.backgroundSaves=false / -Dbons_and_furious.backgroundLevelDat=false: Minecraft's code on every save.
 * -Dbons_and_furious.backgroundSaves.shadow=true (verification runs): each deferred NBT file is also written the vanilla
 * way into memory on the server thread and compared with the writer's bytes (SHADOW_CHECKS / SHADOW_MISMATCHES, first 20
 * logged).
 */
public final class SaveWriter {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The logger LevelStorageSource and its LevelStorageAccess write to (same name as Minecraft's, so the same logger). */
    public static final Logger LEVEL_STORAGE_LOGGER = org.slf4j.LoggerFactory.getLogger(net.minecraft.world.level.storage.LevelStorageSource.class);
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.backgroundSaves", "true"));
    public static volatile boolean levelDatEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.backgroundLevelDat", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.backgroundSaves.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Counters for rigs: jobs written, uncompressed bytes handed over, drains that had to wait. */
    public static final AtomicLong JOBS = new AtomicLong(), BYTES = new AtomicLong(), WAITS = new AtomicLong();

    private static final Object LOCK = new Object();
    private static final ArrayDeque<Job> QUEUE = new ArrayDeque<>();
    private static long submitted, completed;          // guarded by LOCK
    private static volatile long pending;              // submitted - completed, read without the lock by drain()
    private static volatile long queuedBytes;          // uncompressed bytes waiting (written under LOCK)
    private static final long QUEUE_LIMIT = 256L << 20;
    private static volatile Thread worker;
    private static volatile boolean announced, hookAdded;
    private static final ClassValue<Boolean> OWN_SAVE_FILE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> c = type; c != null && c != SavedData.class; c = c.getSuperclass()) {
                try {
                    c.getDeclaredMethod("m_77757_", File.class);
                    return true;
                } catch (NoSuchMethodException ignored) {
                } catch (Throwable t) {
                    return true;
                }
            }
            return false;
        }
    };
    private static final ClassValue<Boolean> PLAIN_TO_STRING = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("toString").getDeclaringClass() == Object.class && type.getMethod("hashCode").getDeclaringClass() == Object.class;
            } catch (Throwable t) {
                return false;
            }
        }
    };

    private SaveWriter() {
    }

    /** A unit of work for the writer thread. */
    abstract static class Job {
        long bytes;

        abstract void write() throws Exception;

        /** Minecraft's handling of what its own method catches (logging); anything else is passed on. */
        abstract void failed(Throwable t);
    }

    // ------------------------------------------------------------------------------------------------ eligibility

    /** Forge's current server (offline harnesses, which cannot initialise ServerLifecycleHooks, put their own here). */
    public static volatile java.util.function.Supplier<MinecraftServer> currentServer = ServerLifecycleHooks::getCurrentServer;

    /** True when the current thread is the thread of a running server (the only place saves are deferred). */
    public static boolean onRunningServerThread() {
        MinecraftServer server = currentServer.get();
        return server != null && server.m_130010_() && server.m_18695_();
    }

    public static boolean deferSaves() {
        return enabled && onRunningServerThread();
    }

    public static boolean deferLevelDat() {
        return levelDatEnabled && onRunningServerThread();
    }

    /** SavedData.save(File): deferred unless its class declares its own save(File) (custom file handling). */
    public static boolean deferSavedData(SavedData data) {
        return deferSaves() && !OWN_SAVE_FILE.get(data.getClass());
    }

    /**
     * Whether Forge's PlayerEvent.SaveToFile has a listener (offline harnesses, where Forge's event classes lack the
     * no-argument constructor its transformer adds, put their own here).
     */
    public static volatile java.util.function.BooleanSupplier playerSaveEventListened = PlayerSaveEvent::hasListeners;

    /** PlayerDataStorage.save: deferred only while PlayerEvent.SaveToFile has no listener (it is fired after the write). */
    public static boolean deferPlayerData() {
        return deferSaves() && !playerSaveEventListened.getAsBoolean();
    }

    // ------------------------------------------------------------------------------------------------ submissions

    /** SavedData.save(File) -> NbtIo.writeCompressed(tag, file), deferred. */
    public static void submitSavedData(CompoundTag tag, File file, SavedData data, Logger savedDataLogger) {
        NbtRecording rec = NbtRecording.record(tag);
        if (rec.failure != null) {
            writeNow(rec, file);   // Minecraft's result for a tag that cannot be written: partial file, the throwable propagates
            return;
        }
        shadow(tag, rec, file);
        Object label = PLAIN_TO_STRING.get(data.getClass()) ? data : String.valueOf(data);
        submit(new Job() {
            @Override
            void write() throws Exception {
                rec.writeTo(file);
            }

            @Override
            void failed(Throwable t) {
                if (t instanceof IOException) savedDataLogger.error("Could not save data {}", label, t);
                else unexpected(file, t);
            }
        }, rec.size());
    }

    /** NbtIo.writeCompressed(tag, temp) where temp is a DeferredFile: record now, write with the replace job. */
    public static void record(CompoundTag tag, DeferredFile temp) {
        NbtRecording rec = NbtRecording.record(tag);
        temp.recording = rec;
        if (rec.failure != null) {
            // Minecraft created the temporary file before writing it: do the same now, then fail as it would
            drain();
            File real;
            try {
                real = File.createTempFile(temp.prefix, temp.suffix, temp.folder);
            } catch (IOException e) {
                throw NbtRecording.<RuntimeException>sneaky(e);
            }
            writeNow(rec, real);
            return;
        }
        shadow(tag, rec, temp);
    }

    /**
     * Util.safeReplaceFile(current, temp, old) where temp is a DeferredFile: the writer creates the temporary file, writes
     * it and replaces current with it. onFailure gets what Minecraft's catch (Exception) would have caught.
     */
    public static void submitReplace(DeferredFile temp, File current, File old, Consumer<Exception> onFailure) {
        NbtRecording rec = temp.recording;
        submit(new Job() {
            @Override
            void write() throws Exception {
                File real = File.createTempFile(temp.prefix, temp.suffix, temp.folder);
                rec.writeTo(real);
                Util.m_137462_(current, real, old);
            }

            @Override
            void failed(Throwable t) {
                if (t instanceof Exception e) onFailure.accept(e);
                else unexpected(current, t);
            }
        }, rec == null ? 0 : rec.size());
    }

    /** PlayerAdvancements.save: the writer for the captured JSON text (FileUtil.createDirectoriesSafe(dir) first). */
    public static BufferedWriter captureText(Path dir, Path path, Charset charset, OpenOption[] options, Logger advancementsLogger) {
        return new BufferedWriter(new TextCapture(text -> submit(new Job() {
            @Override
            void write() throws Exception {
                FileUtil.m_257659_(dir);
                try (BufferedWriter writer = Files.newBufferedWriter(path, charset, options)) {
                    writer.write(text);
                }
            }

            @Override
            void failed(Throwable t) {
                if (t instanceof IOException) advancementsLogger.error("Couldn't save player advancements to {}", path, t);
                else unexpected(path.toFile(), t);
            }
        }, text.length())));
    }

    /** ServerStatsCounter.save: FileUtils.writeStringToFile(file, text) on the writer. */
    @SuppressWarnings("deprecation")
    public static void submitStats(File file, String text, Logger statsLogger) {
        submit(new Job() {
            @Override
            void write() throws Exception {
                org.apache.commons.io.FileUtils.writeStringToFile(file, text);
            }

            @Override
            void failed(Throwable t) {
                if (t instanceof IOException) statsLogger.error("Couldn't save stats", t);
                else unexpected(file, t);
            }
        }, text.length());
    }

    private static void writeNow(NbtRecording rec, File file) {
        drain();
        try {
            rec.writeTo(file);
        } catch (Throwable t) {
            throw NbtRecording.<RuntimeException>sneaky(t);
        }
    }

    private static void unexpected(File file, Throwable t) {
        LOGGER.error("Bons and Furious: vanilla_background_saves could not write {}", file, t);
    }

    private static void shadow(CompoundTag tag, NbtRecording rec, File file) {
        if (!SHADOW) return;
        try {
            ByteArrayOutputStream vanilla = new ByteArrayOutputStream(), ours = new ByteArrayOutputStream();
            NbtIo.m_128947_(tag, vanilla);
            rec.writeTo(ours);
            SHADOW_CHECKS.incrementAndGet();
            if (!Arrays.equals(vanilla.toByteArray(), ours.toByteArray())) {
                long m = SHADOW_MISMATCHES.incrementAndGet();
                if (m <= 20) LOGGER.warn("Bons and Furious: vanilla_background_saves shadow mismatch #{} for {}: {} vanilla bytes, {} from the recording",
                        m, file, vanilla.size(), ours.size());
            }
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: vanilla_background_saves shadow check of {} failed: {}", file, t.toString());
        }
    }

    // ------------------------------------------------------------------------------------------------ the writer

    static void submit(Job job, long bytes) {
        // bounded memory: with more than QUEUE_LIMIT uncompressed bytes waiting, let the writer catch up first
        if (queuedBytes > QUEUE_LIMIT) drain();
        job.bytes = bytes;
        synchronized (LOCK) {
            if (worker == null) start();
            QUEUE.addLast(job);
            submitted++;
            pending = submitted - completed;
            queuedBytes += bytes;
            LOCK.notifyAll();
        }
        BYTES.addAndGet(bytes);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_background_saves applies (saved data, player and level files are compressed and written on one background writer; reads and flushes wait for it){}",
                    SHADOW ? " - shadow verification on" : "");
        }
    }

    private static void start() {
        Thread t = new Thread(SaveWriter::loop, "Bons and Furious save writer");
        t.setDaemon(true);
        worker = t;
        t.start();
        if (!hookAdded) {
            hookAdded = true;
            try {
                Runtime.getRuntime().addShutdownHook(new Thread(() -> drain(60_000L), "Bons and Furious save writer flush"));
            } catch (IllegalStateException ignored) {
                // the JVM is already shutting down
            }
        }
    }

    private static void loop() {
        while (true) {
            Job job;
            synchronized (LOCK) {
                while (QUEUE.isEmpty()) {
                    try {
                        LOCK.wait();
                    } catch (InterruptedException ignored) {
                        // keep serving: queued saves must still be written
                    }
                }
                job = QUEUE.pollFirst();
            }
            try {
                job.write();
                JOBS.incrementAndGet();
            } catch (Throwable t) {
                try {
                    job.failed(t);
                } catch (Throwable ignored) {
                    // logging must not stop the writer
                }
            } finally {
                synchronized (LOCK) {
                    completed++;
                    pending = submitted - completed;
                    queuedBytes -= job.bytes;
                    LOCK.notifyAll();
                }
            }
        }
    }

    /** Waits until every job submitted so far is written (no-op when nothing is pending or on the writer itself). */
    public static void drain() {
        if (pending == 0 || Thread.currentThread() == worker) return;
        drain(Long.MAX_VALUE);
    }

    private static void drain(long timeoutMs) {
        if (Thread.currentThread() == worker) return;
        boolean interrupted = false;
        long deadline = timeoutMs == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + timeoutMs;
        synchronized (LOCK) {
            long target = submitted;
            if (completed < target) WAITS.incrementAndGet();
            while (completed < target) {
                long left = deadline == Long.MAX_VALUE ? 0L : deadline - System.currentTimeMillis();
                if (deadline != Long.MAX_VALUE && left <= 0) break;
                try {
                    LOCK.wait(left);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    /** Jobs submitted and not yet written (for tests and rigs). */
    public static long pending() {
        return pending;
    }

    // ------------------------------------------------------------------------------------------------ Forge's player-save event

    /**
     * Whether Forge's PlayerEvent.SaveToFile currently has any listener on MinecraftForge.EVENT_BUS (eventbus 6.2: its
     * ListenerList for that bus, which includes listeners of the parent event classes; true if it cannot be read).
     */
    static final class PlayerSaveEvent {
        private static volatile net.minecraftforge.eventbus.ListenerList listenerList;
        private static volatile int busId = -1;
        private static volatile boolean broken, noted;

        static boolean hasListeners() {
            if (broken) return true;
            try {
                if (listenerList == null) {
                    Object bus = net.minecraftforge.common.MinecraftForge.EVENT_BUS;
                    Field id = bus.getClass().getDeclaredField("busID");
                    id.setAccessible(true);
                    busId = id.getInt(bus);
                    listenerList = net.minecraftforge.eventbus.api.EventListenerHelper.getListenerList(
                            net.minecraftforge.event.entity.player.PlayerEvent.SaveToFile.class);
                }
                Object[] listeners = listenerList.getListeners(busId);
                boolean any = listeners.length > 0;
                if (!noted) {
                    noted = true;
                    LOGGER.info("Bons and Furious: vanilla_background_saves: player .dat files {} (PlayerEvent.SaveToFile has {} listener entries)",
                            any ? "stay synchronous" : "go to the background writer", listeners.length);
                }
                return any;
            } catch (Throwable t) {
                broken = true;
                LOGGER.warn("Bons and Furious: vanilla_background_saves cannot see Forge's PlayerEvent.SaveToFile listeners ({}); player .dat files stay synchronous", t.toString());
                return true;
            }
        }
    }
}
