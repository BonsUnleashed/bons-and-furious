package bons.furious.patch.forge_registry_snapshot;

import com.google.common.collect.BiMap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegistryManager;
import org.slf4j.Logger;

/**
 * Bons and Furious switch forge_registry_snapshot_reuse (Forge 47.4.16; both sides: every world save of a server,
 * including the integrated one). Idea: Shinoyuki-BetterAutoSave `cacheRegistrySnapshot` (idea text only); the design
 * (validate every input on every save, keep Forge's own Snapshot object) is ours.
 *
 * What Forge does. Each level.dat save runs ForgeHooks.writeAdditionalLevelSaveData, which calls
 * RegistryManager.ACTIVE.takeSnapshot(true): for every persisted registry ForgeRegistry.makeSnapshot() builds a new
 * Snapshot (its ids go into an Object2IntRBTreeMap ordered by ResourceLocation.compareNamespaced, one getKey lookup and
 * one tree insert per id, about 60,000 here), then Snapshot.write() turns it into the "fml.Registries" NBT. The snapshot
 * comes out the same on every save unless a registry changed.
 *
 * What the switch does. While (and only while) writeAdditionalLevelSaveData runs takeSnapshot (a thread-local depth set
 * by ForgeHooksSnapshotMixin), makeSnapshot compares the registry's live inputs with those it saw when it last built a
 * snapshot: the sequence of (id, getKey(value)) pairs in ids.forEach order (what makeSnapshot iterates), the aliases map,
 * the blocked id set and getOverrideOwners(). When every one is equal, the Snapshot object built then is handed back;
 * otherwise the original makeSnapshot runs and the new snapshot and inputs are kept.
 *
 * Why the result is identical. makeSnapshot is a pure function of exactly those inputs (getKey and getOverrideOwners are
 * pure lookups; the latter is still called once per save, as by makeSnapshot), so equal inputs give a snapshot with equal
 * content, and Snapshot.write() - Forge's own method, called by Forge's own loop - builds fresh tags from it with the same
 * insertion order every time (no cached tag is copied, so no map can come out reordered). The kept Snapshot is used only
 * by this loop, which calls write() and drops it; snapshots for the network (takeSnapshot(false), whose packet bytes are
 * cached inside the Snapshot) are never touched. Any change to a registry - an added, removed or remapped id, a renamed
 * entry, an alias, a blocked id, an override - fails the comparison and rebuilds that registry's snapshot on that save.
 *
 * -Dbons_and_furious.registrySnapshotReuse=false switches it off at run time.
 * -Dbons_and_furious.registrySnapshotReuse.shadow=true (verification runs only): on every reuse the original also builds
 * a snapshot, both are written and compared (NBT bytes); the original's snapshot is used. SHADOW_CHECKS /
 * SHADOW_MISMATCHES (WARN for the first 20).
 */
public final class RegistrySnapshots {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.registrySnapshotReuse", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.registrySnapshotReuse.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Test and log support: snapshots handed back / built. */
    public static final AtomicLong REUSED = new AtomicLong(), BUILT = new AtomicLong();
    private static final ThreadLocal<int[]> SAVING = ThreadLocal.withInitial(() -> new int[1]);
    private static volatile boolean announced;

    /** One registry's inputs when its kept snapshot was built, and that snapshot. Immutable after construction. */
    public record Kept(int[] ids, ResourceLocation[] keys, Map<ResourceLocation, ResourceLocation> aliases, IntSet blocked,
                       Map<ResourceLocation, String> overrides, ForgeRegistry.Snapshot snapshot) {
    }

    private RegistrySnapshots() {
    }

    /** ForgeHooks.writeAdditionalLevelSaveData: its RegistryManager.takeSnapshot(true) call, marked as a save. */
    @SuppressWarnings("rawtypes")
    public static Map takeSnapshotForSave(RegistryManager manager, boolean savingToDisc, Operation<Map> original) {
        if (!enabled || !savingToDisc) return original.call(manager, savingToDisc);
        int[] depth = SAVING.get();
        depth[0]++;
        try {
            return original.call(manager, savingToDisc);
        } finally {
            depth[0]--;
        }
    }

    /** True while the current thread runs writeAdditionalLevelSaveData's takeSnapshot(true). */
    public static boolean saving() {
        return enabled && SAVING.get()[0] > 0;
    }

    /**
     * ForgeRegistry.makeSnapshot during a level save. kept/keep: the registry's slot (ForgeRegistrySnapshotMixin);
     * overrides: getOverrideOwners() of this save (computed by the caller, which can reach the package-private method).
     */
    public static ForgeRegistry.Snapshot makeSnapshot(ForgeRegistry<?> registry, BiMap<Integer, ?> ids, Map<ResourceLocation, ResourceLocation> aliases,
                                                      IntSet blocked, Map<ResourceLocation, String> overrides, Kept kept,
                                                      java.util.function.Consumer<Kept> keep, Operation<ForgeRegistry.Snapshot> original) {
        if (kept != null && same(registry, ids, kept) && aliases.equals(kept.aliases()) && blocked.equals(kept.blocked())
                && overrides.equals(kept.overrides())) {
            REUSED.incrementAndGet();
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: forge_registry_snapshot_reuse applies (level.dat saves reuse each registry's snapshot while the registry is unchanged){}",
                        SHADOW ? " - shadow verification on" : "");
            }
            if (SHADOW) {
                ForgeRegistry.Snapshot fresh = original.call();
                SHADOW_CHECKS.incrementAndGet();
                if (!Arrays.equals(bytes(fresh.write()), bytes(kept.snapshot().write()))) {
                    long k = SHADOW_MISMATCHES.incrementAndGet();
                    if (k <= 20) LOGGER.warn("Bons and Furious: forge_registry_snapshot_reuse shadow mismatch #{} for {}", k, registry.getRegistryName());
                }
                return fresh;
            }
            return kept.snapshot();
        }
        ForgeRegistry.Snapshot built = original.call();
        BUILT.incrementAndGet();
        keep.accept(capture(registry, ids, aliases, blocked, overrides, built));
        return built;
    }

    /** The sequence of (id, getKey(value)) pairs in ids.forEach order equals the kept one. */
    private static boolean same(ForgeRegistry<?> registry, BiMap<Integer, ?> ids, Kept kept) {
        int n = kept.ids().length;
        if (ids.size() != n) return false;
        int[] i = {0};
        boolean[] ok = {true};
        @SuppressWarnings("unchecked") ForgeRegistry<Object> reg = (ForgeRegistry<Object>) registry;
        ids.forEach((id, value) -> {
            if (!ok[0]) return;
            int k = i[0]++;
            if (k >= n || id.intValue() != kept.ids()[k]) {
                ok[0] = false;
                return;
            }
            ResourceLocation key = reg.getKey(value);
            ResourceLocation was = kept.keys()[k];
            if (key != was && (key == null || !key.equals(was))) ok[0] = false;
        });
        return ok[0] && i[0] == n;
    }

    private static Kept capture(ForgeRegistry<?> registry, BiMap<Integer, ?> ids, Map<ResourceLocation, ResourceLocation> aliases, IntSet blocked,
                                Map<ResourceLocation, String> overrides, ForgeRegistry.Snapshot built) {
        int n = ids.size();
        int[] seqIds = new int[n];
        ResourceLocation[] seqKeys = new ResourceLocation[n];
        int[] i = {0};
        @SuppressWarnings("unchecked") ForgeRegistry<Object> reg = (ForgeRegistry<Object>) registry;
        ids.forEach((id, value) -> {
            int k = i[0]++;
            if (k < n) {
                seqIds[k] = id;
                seqKeys[k] = reg.getKey(value);
            }
        });
        if (i[0] != n) return null;   // changed while capturing: keep nothing, the next save builds again
        return new Kept(seqIds, seqKeys, new HashMap<>(aliases), new IntOpenHashSet(blocked), new HashMap<>(overrides), built);
    }

    static byte[] bytes(CompoundTag t) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            NbtIo.m_128941_(t, new DataOutputStream(bo));
            return bo.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
