package bons.furious.patch.vanilla_structure_templates;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_structure_template_hits (Minecraft 1.21.1, tested build NeoForge 21.1.252, with or
 * without ModernFix 5.27.24; on 1.20.1 Forge 47.4.16 with or without ModernFix 5.27.77; server side, world generation
 * threads). Mojang member names. Idea: TickMender `structureTemplateCacheHit`
 * ("A structure template that is already loaded is answered without the cache's lock"), idea text only; the guard on the
 * map class and the reasoning are ours.
 *
 * What the game does. StructureTemplateManager.get(id) is `structureRepository.computeIfAbsent(id, this::tryLoad)`.
 * Vanilla's map is a ConcurrentHashMap. ModernFix's perf.dynamic_structure_manager (on by default) replaces it at the end
 * of the constructor with CacheBuilder.newBuilder().softValues().build().asMap(), a Guava 31.1 LocalCache, whose
 * computeIfAbsent is compute(): every call, also for a template that is already loaded, takes the key's segment lock,
 * runs the segment's write cleanup, swaps in a ComputingValueReference (+ SettableFuture, Stopwatch) and back. A miss
 * holds the same lock while it reads and parses the template, so on parallel world-generation threads (C2ME, DH) every
 * lookup of any template in that segment waits behind the load. Measured: C2ME workers parked 3.29 s on that lock per
 * 19.1 s warm-up window of a pack server.
 *
 * What the switch does. get first asks the map plainly (map.get(id)); a non-null value is returned as is; null (absent,
 * a soft value the GC has collected, or a load in progress on another thread) runs the original computeIfAbsent, so
 * tryLoad still runs at most once per absence, under the same lock, on the same thread as without the switch. Only for
 * the two map classes whose get is known: java.util.concurrent.ConcurrentHashMap and Guava's LocalCache; any other map
 * (a mod's replacement) always runs the original.
 *
 * Why the result is identical. For a live entry both maps' computeIfAbsent return exactly the value get returns, and
 * Guava's compute puts the same value reference back (its write/access queues are discarding queues for a soft-values
 * cache: recordWrite does nothing). get sees an entry being loaded by another thread as null (Guava's loading reference
 * yields the old, unset value), so it waits for the load inside computeIfAbsent exactly as before. A value returned by
 * get was in the map at that moment, which is an outcome the locked call can also produce (as if it had run just before a
 * concurrent clear or remove). The only difference is when Guava purges entries whose soft values were collected (get's
 * periodic cleanup instead of every compute's), which shows only in the map's size and iteration; nothing in the pack
 * reads those (StructureTemplateManager uses get/put/remove/clear/computeIfAbsent; the three accessor mixins of
 * Repurposed Structures, Integrated API and Moog's Structure Lib read only the resource manager).
 *
 * -Dbons_and_furious.structureTemplateHits=false switches it off at run time.
 * -Dbons_and_furious.structureTemplateHits.shadow=true (verification runs only): every hit also runs the original
 * computeIfAbsent and compares the two results by identity (a difference is possible only around a concurrent clear,
 * remove or GC collection; SHADOW_MISMATCHES counts them, WARN for the first 20); the original's result is returned.
 *
 * Ported to 1.21.1: StructureTemplateManager.get is unchanged and the class still uses the map only through get, put,
 * remove, clear and computeIfAbsent (vanilla's map is still Maps.newConcurrentMap()). ModernFix 5.27.24's
 * dynamic_structure_manager installs the same CacheBuilder.newBuilder().softValues().build().asMap(); Minecraft 1.21.1
 * ships Guava 32.1.2, whose LocalCache get / compute / computeIfAbsent, segment locking, recordWrite and the loading /
 * computing value references are the same as 31.1's (the differences are string formatting, lambdas, serialization
 * guards and copyEntry reading the key once). Integrated API 1.8.2's StructureTemplateManagerAccessor reads only the
 * resource manager. Generator Accelerator 1.6.2 installs CacheBuilder.maximumSize(256): also a Guava LocalCache, but
 * size-bounded (an LRU access queue that compute and get update differently), which the argument above does not cover,
 * so the switch steps aside next to it (patches/vanilla_structure_templates.json yield).
 */
public final class StructureTemplateHits {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.structureTemplateHits", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.structureTemplateHits.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    static final String GUAVA_LOCAL_CACHE = "com.google.common.cache.LocalCache";
    private static volatile boolean announced;

    /** True for the map classes whose plain get answers exactly like computeIfAbsent's hit path. */
    private static final ClassValue<Boolean> KNOWN = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return type == ConcurrentHashMap.class || type.getName().equals(GUAVA_LOCAL_CACHE);
        }
    };

    private StructureTemplateHits() {
    }

    /** StructureTemplateManager.get: its structureRepository.computeIfAbsent(id, this::tryLoad) call. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Object get(Map map, Object key, Function loader, Operation<Object> original) {
        if (!enabled || map == null || key == null || !KNOWN.get(map.getClass())) return original.call(map, key, loader);
        Object value = map.get(key);
        if (value == null) return original.call(map, key, loader);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_structure_template_hits applies (loaded structure templates are answered without the cache lock; map {}){}",
                    map.getClass().getName(), SHADOW ? " - shadow verification on" : "");
        }
        if (SHADOW) {
            Object locked = original.call(map, key, loader);
            SHADOW_CHECKS.incrementAndGet();
            if (locked != value) {
                long k = SHADOW_MISMATCHES.incrementAndGet();
                if (k <= 20) LOGGER.warn("Bons and Furious: vanilla_structure_template_hits shadow difference #{} for {} (a concurrent clear, remove or GC collection)", k, key);
            }
            return locked;
        }
        return value;
    }
}
