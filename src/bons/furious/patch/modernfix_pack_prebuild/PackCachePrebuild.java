package bons.furious.patch.modernfix_pack_prebuild;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import net.minecraft.server.packs.PackType;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.resource.PathPackResources;
import org.embeddedt.modernfix.resources.PackResourcesCacheEngine;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Bons and Furious switch modernfix_pack_cache_prebuild (ModernFix 5.27.77 on Forge 47.4.16 / Minecraft 1.20.1; client and
 * server). Idea: Collections Of Optimizations (vanilla.indexModFilesInBackground), idea text only; the design below is ours:
 * ModernFix's own index, built ahead of time, handed over where ModernFix creates it.
 *
 * What ModernFix does. Its ForgePathPackResourcesMixin gives every Forge mod-file pack a PackResourcesCacheEngine (a tree of
 * the jar's assets and data files) that answers listResources, getRootResource existence checks and, once built,
 * getNamespaces. The engine is created on the pack's first such lookup (generateResourceCache) and built inside awaitLoad by
 * the first thread that needs it, while every other thread asking about the same jar waits on its lock. At client start or
 * server boot that is a few hundred jar walks, on whichever thread happens to list first.
 *
 * What the switch does. When ResourcePackLoader.loadResourcePacks has made the mod-file packs, one task per pack builds an
 * engine on a small background pool (at most 8 threads, idle threads end after 5 s) exactly as ModernFix builds it: the same
 * constructor with the same root-path function (type -> the mod file's findResource(type directory), which is what
 * ResourcePackLoader$1.resolve does) and the same awaitLoad. Nothing is put into the pack. When ModernFix's
 * generateResourceCache later creates the pack's engine, the switch hands over the finished one instead of the new, unbuilt
 * one: it waits if the task is running, and lets ModernFix build in place if the task never started or failed. So the pack's
 * cacheEngine field is set at the same moment by ModernFix's own code (getNamespaces walks the disk until then, as before),
 * and the engine holds the same tree, built from the same immutable jar; only when and on which thread it is built changes.
 * A finished engine is also kept per mod file, and the packs a later loadResourcePacks makes for the same mod file (every
 * singleplayer world load) get it instead of walking the jar again. Only jar files (regular files) are prebuilt, never mods
 * loaded from a folder, whose content could change.
 *
 * Steps aside when ModernFix's resource pack mixin is not applied (its option is off), or another mod's mixin is applied to
 * ResourcePackLoader$1 (whose resolve the root-path function repeats).
 *
 * -Dbons_and_furious.modernfixPackCachePrebuild=false switches it off at run time.
 * -Dbons_and_furious.modernfixPackCachePrebuild.shadow=true (verification runs only): ModernFix builds every engine itself, as
 * without the switch, and each prebuilt engine is compared with it (every namespace and every file it lists for both pack
 * types): SHADOW_CHECKS, SHADOW_MISMATCHES (WARN for the first 20).
 */
public final class PackCachePrebuild {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.modernfixPackCachePrebuild", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.modernfixPackCachePrebuild.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Builds queued, engines handed over (prebuilt / shared from an earlier load), builds left to ModernFix, failed builds. */
    public static final LongAdder QUEUED = new LongAdder(), HANDED = new LongAdder(), SHARED = new LongAdder(), IN_PLACE = new LongAdder(), FAILED = new LongAdder();
    static final String MOD_FILE_PACK = "net.minecraftforge.resource.ResourcePackLoader$1";
    /** pack -> its task, until ModernFix asks for the pack's engine (weak keys: a pack dropped unused frees its task). */
    private static final Map<Object, Task> PENDING = Collections.synchronizedMap(new WeakHashMap<>());
    /** Finished engines per mod file, for the packs later loads make for the same file. */
    private static final Map<IModFile, PackResourcesCacheEngine> BUILT = new ConcurrentHashMap<>();
    /** Offline proof only: runs the tasks instead of the pool (null in the game). */
    public static volatile Executor testExecutor;
    private static volatile ThreadPoolExecutor pool;
    private static volatile int ready = -1;   // -1 not decided yet, 1 applies, 0 steps aside

    private PackCachePrebuild() {
    }

    /** One engine build; the pack itself is not referenced, so the weak PENDING entry can go with an unused pack. */
    static final class Task implements Runnable {
        final IModFile file;
        final AtomicInteger state = new AtomicInteger();   // 0 queued, 1 building, 2 built, 3 failed, 4 left to ModernFix
        final CountDownLatch done = new CountDownLatch(1);
        volatile PackResourcesCacheEngine engine;
        boolean kept;   // the engine came from an earlier load of the same mod file

        Task(IModFile file) {
            this.file = file;
        }

        @Override
        public void run() {
            if (!state.compareAndSet(0, 1)) return;   // ModernFix asked first and builds it itself
            try {
                if (!jarFile(file)) {   // a mod loaded from a folder could change before ModernFix would read it: never ahead of time
                    state.set(3);
                    return;
                }
                PackResourcesCacheEngine e = new PackResourcesCacheEngine(type -> file.findResource(type.m_10305_()));
                e.hasResource(new String[0]);   // ModernFix's awaitLoad: the whole tree is built now
                engine = e;
                BUILT.putIfAbsent(file, e);
                state.set(2);
            } catch (Throwable t) {
                state.set(3);
                FAILED.increment();
                LOGGER.debug("Bons and Furious: modernfix_pack_cache_prebuild could not build the cache of {} ahead of time ({}); ModernFix builds it when asked", file.getFileName(), t.toString());
            } finally {
                done.countDown();
            }
        }

        /** The finished engine, or null when ModernFix should build it as usual (never started, or failed). */
        PackResourcesCacheEngine take() {
            if (state.compareAndSet(0, 4)) return null;
            boolean interrupted = false;
            while (true) {
                try {
                    done.await();
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;   // finish waiting for the build, keep the interrupt for the caller
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
            return state.get() == 2 ? engine : null;
        }
    }

    /** ResourcePackLoader.loadResourcePacks, at its end: queue one build per mod-file pack (or hand a kept engine on). */
    public static void afterLoad(Map<IModFile, PathPackResources> packs) {
        if (!enabled || packs == null || packs.isEmpty() || !ready()) return;
        Executor run = testExecutor != null ? testExecutor : pool();
        int queued = 0, shared = 0;
        for (Map.Entry<IModFile, PathPackResources> e : packs.entrySet()) {
            IModFile file = e.getKey();
            PathPackResources pack = e.getValue();
            // the jar-file test (a file system call per mod file) runs in the task, off this thread
            if (file == null || pack == null || !MOD_FILE_PACK.equals(pack.getClass().getName())) continue;
            Task task = new Task(file);
            PackResourcesCacheEngine kept = BUILT.get(file);
            if (kept != null) {
                task.engine = kept;
                task.kept = true;
                task.state.set(2);
                task.done.countDown();
                shared++;
            }
            PENDING.put(pack, task);
            if (kept == null) {
                run.execute(task);
                queued++;
            }
        }
        QUEUED.add(queued);
        LOGGER.info("Bons and Furious: modernfix_pack_cache_prebuild applies ({} of ModernFix's mod-file caches built in the background, {} kept from an earlier load){}",
                queued, shared, SHADOW ? " - shadow verification on" : "");
    }

    /** ModernFix's generateResourceCache: its `new PackResourcesCacheEngine(retriever)`. */
    public static PackResourcesCacheEngine engine(Object pack, Function<PackType, Path> retriever, Operation<PackResourcesCacheEngine> original) {
        Task task = enabled ? PENDING.remove(pack) : null;
        PackResourcesCacheEngine prebuilt = task == null ? null : task.take();
        if (prebuilt == null) {
            if (task != null) IN_PLACE.increment();
            return original.call(retriever);
        }
        if (SHADOW) {
            PackResourcesCacheEngine own = original.call(retriever);
            shadow(prebuilt, own, task.file);
            return own;
        }
        (task.kept ? SHARED : HANDED).increment();
        return prebuilt;
    }

    /** Shadow mode: the prebuilt tree must answer like ModernFix's own (namespaces and every listed file, both types). */
    static void shadow(PackResourcesCacheEngine prebuilt, PackResourcesCacheEngine own, IModFile file) {
        try {
            String a = describe(prebuilt), b = describe(own);
            SHADOW_CHECKS.incrementAndGet();
            if (!a.equals(b)) {
                long m = SHADOW_MISMATCHES.incrementAndGet();
                if (m <= 20) LOGGER.warn("Bons and Furious: modernfix_pack_cache_prebuild shadow mismatch #{} for {}", m, file.getFileName());
            }
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: modernfix_pack_cache_prebuild shadow check failed for {} ({})", file.getFileName(), t.toString());
        }
    }

    /** Every namespace of both types and every file the engine lists under each, in its order. Public for the offline proof. */
    public static String describe(PackResourcesCacheEngine engine) {
        StringBuilder sb = new StringBuilder();
        for (PackType type : PackType.values()) {
            Set<String> namespaces = new TreeSet<>(engine.getNamespaces(type));
            sb.append(type).append(' ').append(namespaces).append('\n');
            for (String ns : namespaces) {
                List<String> files = new ArrayList<>();
                engine.collectResources(type, ns, new String[0], Integer.MAX_VALUE, (id, supplier) -> files.add(id.toString()));
                sb.append(ns).append(' ').append(files).append('\n');
            }
        }
        return sb.toString();
    }

    static boolean jarFile(IModFile file) {
        try {
            Path p = file.getFilePath();
            return p != null && Files.isRegularFile(p);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Decided once: ModernFix's generateResourceCache carries our hand-over, and nobody else patches ResourcePackLoader$1. */
    static boolean ready() {
        int r = ready;
        if (r < 0) {
            String why = null;
            boolean modernFix = false, ours = false;
            for (Method m : PathPackResources.class.getDeclaredMethods()) {
                if (m.getName().equals("generateResourceCache")) modernFix = true;
                if (m.getName().endsWith("$bons$prebuiltEngine")) ours = true;
            }
            if (!modernFix) why = "ModernFix's resource pack cache (mixin.perf.resourcepacks) is not applied";
            else if (!ours) why = "ModernFix's generateResourceCache has a shape this switch does not recognise";
            else {
                try {
                    for (IMixinInfo info : Mixins.getMixinsForClass(MOD_FILE_PACK)) {
                        String name = info.getClassName();
                        if (!name.startsWith("bons.furious.") && !name.startsWith("bons.pure.") && !name.startsWith("agentcraft."))
                            why = name + " changes ResourcePackLoader$1, whose resolve this switch repeats";
                    }
                } catch (Throwable t) {
                    // no Mixin environment (offline tools): nothing was applied by anyone else
                }
            }
            ready = r = why == null ? 1 : 0;
            if (why != null) LOGGER.info("Bons and Furious: modernfix_pack_cache_prebuild steps aside: {}; ModernFix builds its caches as usual", why);
        }
        return r == 1;
    }

    private static Executor pool() {
        ThreadPoolExecutor p = pool;
        if (p == null) {
            synchronized (PackCachePrebuild.class) {
                p = pool;
                if (p == null) {
                    int threads = Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors() - 2));
                    AtomicInteger n = new AtomicInteger();
                    p = new ThreadPoolExecutor(threads, threads, 5, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
                        Thread t = new Thread(r, "Bons and Furious pack cache " + n.incrementAndGet());
                        t.setDaemon(true);
                        return t;
                    });
                    p.allowCoreThreadTimeOut(true);
                    pool = p;
                }
            }
        }
        return p;
    }
}
