package bons.furious.patch.smartparticles_heap;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;

/**
 * Bons and Furious switch smartparticles_heap_slot_clear (Smart Particles 20.1.10, client only). [FIX] Smart Particles'
 * particle-limit heap no longer keeps particles of a previous level, and through them the old ClientLevel, in memory.
 *
 * Smart Particles adds three fields to ParticleEngine (its mixin sp.mixin.ParticleManagerMixin): spHeapParticles
 * (Particle[particleLimit], 5000 by default), spHeapScores (double[particleLimit]) and spKeep (an identity set). At the end
 * of every ParticleEngine.tick, with a player present, its handler fills the heap from slot 0 up with the particles it
 * keeps that tick and reads only slots it filled in that same call (heap inserts, sift up / down and the keep pass all stay
 * below the current heap size, and the heap's top is read only once the heap is full); spKeep is cleared (or replaced)
 * before each use and read only in the pass that fills it. Slots above the current heap size are never cleared, so they
 * keep the particles of earlier ticks, and nothing resets the arrays when the client changes level: a particle from a
 * previous level stays referenced (Particle.level: its ClientLevel) until a later tick keeps at least as many particles.
 *
 * Here, when the ParticleEngine gets a new level (setLevel: every dimension change, world join and logout), every slot of
 * spHeapParticles is set to null and spKeep is emptied. The arrays themselves stay (same length, so Smart Particles keeps
 * reusing them), and since Smart Particles only reads slots it writes first in the same tick, and empties spKeep itself
 * before using it, nothing it reads changes. The particles of the old level are dropped by vanilla in the same call.
 * Smart Particles is MIT licensed; none of its code is carried (the fields are reached by reflection, by name).
 *
 * Runtime switch -Dbons_and_furious.smartparticlesHeapSlotClear=false (read at every level change): nothing is cleared.
 */
public final class SmartParticlesHeap {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.smartparticlesHeapSlotClear", "true"));
    /** Level changes with arrays cleared, and particle references dropped (tests, rigs). */
    public static final AtomicLong CLEARED = new AtomicLong(), DROPPED = new AtomicLong();
    private static volatile Field heap, keep;
    private static volatile boolean resolved, announced;

    private SmartParticlesHeap() {
    }

    private static synchronized void resolve(Class<?> engine) {
        if (resolved) return;
        try {
            Field h = engine.getDeclaredField("spHeapParticles");
            Field k = engine.getDeclaredField("spKeep");
            if (!h.getType().isArray() || h.getType().getComponentType().isPrimitive() || !Set.class.isAssignableFrom(k.getType()))
                throw new NoSuchFieldException("unexpected field types " + h.getType() + ", " + k.getType());
            h.setAccessible(true);
            k.setAccessible(true);
            heap = h;
            keep = k;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Bons and Furious: smartparticles_heap_slot_clear stands down: Smart Particles' heap fields are not on {} ({})", engine.getName(), e.toString());
        } finally {
            resolved = true;
        }
    }

    /** ParticleEngine.setLevel, at its start: empty Smart Particles' heap slots and keep set. */
    public static void levelChanged(Object engine) {
        if (!enabled || engine == null) return;
        if (!resolved) resolve(engine.getClass());
        Field h = heap, k = keep;
        if (h == null || k == null) return;
        try {
            Object[] slots = (Object[]) h.get(engine);
            int dropped = 0;
            if (slots != null) {
                for (Object o : slots) if (o != null) dropped++;
                Arrays.fill(slots, null);
            }
            Set<?> kept = (Set<?>) k.get(engine);
            if (kept != null) kept.clear();
            CLEARED.incrementAndGet();
            DROPPED.addAndGet(dropped);
            if (!announced && dropped > 0) {
                announced = true;
                LOGGER.info("Bons and Furious: smartparticles_heap_slot_clear applies (Smart Particles' particle heap dropped {} particles of the previous level)",
                        dropped);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            heap = null;
            LOGGER.warn("Bons and Furious: smartparticles_heap_slot_clear stands down: {}", e.toString());
        }
    }
}
