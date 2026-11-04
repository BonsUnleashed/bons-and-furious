package bons.furious.patch.iceandfire_c49;

import com.github.alexthe666.iceandfire.entity.props.EntityData;
import com.github.alexthe666.iceandfire.entity.props.EntityDataProvider;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.util.LazyOptional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch iceandfire_entity_data_memo (Ice and Fire 2.1.13-1.20.1-beta-5, both sides, since 1.0.49).
 * Idea: Collections Of Optimizations (iceandfire.fastEntityDataLookup), idea text only. Our own logic; no Ice and Fire
 * code is carried (LGPL-3.0 target, injectors only).
 *
 * Ice and Fire's EntityDataProvider.getCapability(Entity) runs for every living entity every tick on both sides
 * (CapabilityHandler.tickData) and again for start-tracking syncs, items and renderers. For a LivingEntity it boxes the
 * entity id and looks it up in a static HashMap<Integer, LazyOptional<EntityData>> chosen by level().isClientSide()
 * (SERVER_CACHE / CLIENT_CACHE); a miss asks the entity for its capability, registers a listener that removes the id when
 * that LazyOptional is invalidated, and stores present answers under the id.
 *
 * What changes: each LivingEntity keeps one memo (the side map, the id and the LazyOptional that map held for the id when
 * the stock lookup last returned it). A call whose entity has a valid memo for the same id and the same side map returns
 * that LazyOptional without the map lookup; anything else runs the original method and, when its answer is the map's
 * entry for the id, keeps it as the memo.
 *
 * Why it is identical: the stock answer for a living entity is map.get(id) whenever that is non-null. A memo is created
 * only while map.get(id) is its LazyOptional, and it stays valid until the id leaves that map: every removal and the clear
 * that EntityDataProvider performs (removeCachedEntry: remove on either side, clear when the local player leaves; the
 * invalidation listener's remove, lambda$getCapability$3) invalidates the memo of that id first (removed / cleared). A put
 * happens only on a miss, i.e. after the id was absent, i.e. after such a removal. So a valid memo always equals
 * map.get(id), and the calls before the lookup (getId, level, isClientSide) run exactly as in stock. Both maps are
 * referenced only by EntityDataProvider (pack scan, both instances); the guard fingerprints every method of it that
 * touches them. Several entities with the same id on one side (a client preview entity and a later server entity) share
 * the map entry in stock and share the memo here.
 *
 * -Dbons_and_furious.iceandfireEntityDataMemo=false turns the memo off (every call takes the stock path; removals still
 * invalidate). .shadow=true runs the stock lookup on every memo hit, returns its answer and counts any difference.
 */
public final class EntityDataMemo {
    public static final String KEY = "iceandfire_entity_data_memo";
    public static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.iceandfireEntityDataMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.iceandfireEntityDataMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static final Int2ObjectOpenHashMap<Memo> SERVER = new Int2ObjectOpenHashMap<>(), CLIENT = new Int2ObjectOpenHashMap<>();
    private static volatile boolean announced;

    private EntityDataMemo() {
    }

    /** One memo: the side map, the id and the LazyOptional that map held for the id; valid until the id leaves the map. */
    public static final class Memo {
        final Map<?, ?> map;
        final int id;
        final LazyOptional<EntityData> value;
        volatile boolean valid = true;

        Memo(Map<?, ?> map, int id, LazyOptional<EntityData> value) {
            this.map = map;
            this.id = id;
            this.value = value;
        }
    }

    private static Int2ObjectOpenHashMap<Memo> registry(Map<?, ?> map) {
        return map == EntityDataProvider.CLIENT_CACHE ? CLIENT : map == EntityDataProvider.SERVER_CACHE ? SERVER : null;
    }

    /** The wrapped EntityDataProvider.getCapability(Entity). */
    public static LazyOptional<EntityData> lookup(Entity entity, Operation<LazyOptional<EntityData>> original) {
        if (!enabled || !(entity instanceof EntityDataMemoHolder holder)) return original.call(entity);
        // the same calls, in the same order, as the stock method before its map lookup
        int key = entity.m_19879_();
        Map<Integer, LazyOptional<EntityData>> side = entity.m_9236_().m_5776_() ? EntityDataProvider.CLIENT_CACHE : EntityDataProvider.SERVER_CACHE;
        Memo m = holder.bons$iafDataMemo();
        if (m != null && m.valid && m.id == key && m.map == side) {
            if (SHADOW) return shadow(entity, original, m);
            return m.value;
        }
        LazyOptional<EntityData> r = original.call(entity);
        if (r != null && side.get(key) == r) {
            holder.bons$iafDataMemo(memo(side, key, r));
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: {} applies (Ice and Fire's entity data is answered from a per-entity memo until the entity's id leaves Ice and Fire's cache){}",
                        KEY, SHADOW ? " - shadow verification on" : "");
            }
        }
        return r;
    }

    private static LazyOptional<EntityData> shadow(Entity entity, Operation<LazyOptional<EntityData>> original, Memo m) {
        LazyOptional<EntityData> r = original.call(entity);
        SHADOW_CHECKS.incrementAndGet();
        if (r != m.value && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: {} shadow mismatch for {} (id {}): the memo holds {}, Ice and Fire answers {}", KEY, entity, m.id, m.value, r);
        return r;
    }

    private static Memo memo(Map<?, ?> side, int key, LazyOptional<EntityData> value) {
        Int2ObjectOpenHashMap<Memo> reg = registry(side);
        if (reg == null) return null;
        synchronized (reg) {
            Memo m = reg.get(key);
            if (m != null && m.valid && m.value == value) return m;
            if (m != null) m.valid = false;
            m = new Memo(side, key, value);
            reg.put(key, m);
            return m;
        }
    }

    /** Before Ice and Fire removes a key from one of its maps: the memo of that id is no longer valid. */
    public static void removed(Map<?, ?> map, Object key) {
        Int2ObjectOpenHashMap<Memo> reg = registry(map);
        if (reg == null) return;
        synchronized (reg) {
            if (key instanceof Integer id) {
                Memo m = reg.remove(id.intValue());
                if (m != null) m.valid = false;
            } else {
                for (Memo m : reg.values()) m.valid = false;   // not an id Ice and Fire stores: forget everything
                reg.clear();
            }
        }
    }

    /** Before Ice and Fire clears one of its maps: no memo of that map is valid. */
    public static void cleared(Map<?, ?> map) {
        Int2ObjectOpenHashMap<Memo> reg = registry(map);
        if (reg == null) return;
        synchronized (reg) {
            for (Memo m : reg.values()) m.valid = false;
            reg.clear();
        }
    }

    /** Harness view: the memos one side keeps (for checks only). */
    public static int memoCount(boolean client) {
        Int2ObjectOpenHashMap<Memo> reg = client ? CLIENT : SERVER;
        synchronized (reg) {
            return reg.size();
        }
    }
}
