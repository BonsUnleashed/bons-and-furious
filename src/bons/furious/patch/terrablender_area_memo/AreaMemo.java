package bons.furious.patch.terrablender_area_memo;

import com.mojang.logging.LogUtils;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;

/**
 * Bons and Furious switch terrablender_area_thread_memo (TerraBlender 3.0.1.10, the same Area in 3.0.1.11; LGPL-3.0; both
 * sides: world generation runs on the server and on the integrated server). No TerraBlender code here.
 *
 * What TerraBlender does. Its region map is a stack of layers (an initial layer and zoom layers), each an Area whose
 * get(x, z) answers from a 32-slot table guarded by one StampedLock: a read lock for the lookup, the write lock to
 * compute a missing value, which asks the parent layers (each through its own Area and lock). Every biome lookup of every
 * world-generation thread goes through the top Area, so all of them take the same lock, and the small table keeps
 * missing and recomputing.
 *
 * What the switch does. Each thread keeps its own direct-mapped table (4,096 slots of area, x, z and value) in front of
 * every Area: a lookup that thread made before is answered from there without the lock; any other lookup runs
 * TerraBlender's own get unchanged (lock, its own table, the parents) and its answer is kept. A layer's value is a pure
 * function of (x, z): every pixel re-seeds the layer's random from (x, z) and the world seed, and the layer's context is
 * used only inside its own write lock, so a kept answer is always the one get would compute again. The tables are per
 * thread, so threads never contend for them; an Area is identified by a number taken when it first answers.
 * Memory: about 64 KB per world-generation thread.
 *
 * -Dbons_and_furious.areaThreadMemo=false switches it off at run time.
 */
public final class AreaMemo {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.areaThreadMemo", "true"));
    private static final int SLOTS = 4096, MASK = SLOTS - 1;
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final ThreadLocal<Table> TABLES = ThreadLocal.withInitial(Table::new);
    private static volatile boolean announced;

    private AreaMemo() {
    }

    /** One thread's table: slot -> (area id, x, z, value); area id 0 = empty slot. */
    static final class Table {
        final int[] area = new int[SLOTS];
        final long[] key = new long[SLOTS];
        final int[] value = new int[SLOTS];
    }

    /** A new area number (never 0). */
    public static int newId() {
        int id = IDS.incrementAndGet();
        if (id == 0) id = IDS.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: terrablender_area_thread_memo applies (TerraBlender region lookups answered per thread without the area lock)");
        }
        return id;
    }

    private static int slot(int id, long key) {
        long h = (key ^ (id * 0x9E3779B97F4A7C15L)) * 0xBF58476D1CE4E5B9L;
        return (int) (h ^ (h >>> 31)) & MASK;
    }

    /** The kept answer of area {@code id} for (x, z), or Long.MIN_VALUE when this thread has none. */
    public static long lookup(int id, int x, int z) {
        long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
        Table t = TABLES.get();
        int s = slot(id, key);
        return t.area[s] == id && t.key[s] == key ? t.value[s] : Long.MIN_VALUE;
    }

    /** Keeps get's answer for (x, z) of area {@code id} in this thread's table. */
    public static void keep(int id, int x, int z, int value) {
        long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
        Table t = TABLES.get();
        int s = slot(id, key);
        t.area[s] = id;
        t.key[s] = key;
        t.value[s] = value;
    }
}
