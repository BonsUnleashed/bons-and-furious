package bons.furious.patch.geckolib_lean;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * Bons and Furious switch geckolib_bone_lookup_memo (GeckoLib 4.8.4, client; since 1.0.49; exact variant, on by default).
 * Idea: Collections Of Optimizations (geckolib.cacheBoneLookup), idea text only.
 *
 * What GeckoLib does. BakedGeoModel.getBone(name) walks the whole bone tree (CoreBakedGeoModel.searchForChildBone: a
 * pre-order depth-first search with String.equals on every bone) on every call and returns Optional.of(the first match) or
 * Optional.empty(). GeckoLib's own renderers never call it; several mods' renderers do, every frame (Ars Nouveau, Bosses of
 * Mass Destruction, Faunify, Adorable Hamster Pets, Bons Space Program here).
 *
 * What the switch does. Per BakedGeoModel, one pre-order walk builds name -> first bone in that order (the bone the search
 * finds), stamped with the global bone-tree version; later calls answer from it while the version is unchanged, and build
 * it again when it changed. The version advances on every structural change of every list the tree is made of: GeoBone's
 * child lists and the built-in factory's top-level list are CountingBoneList (the exact-variant difference). The answer is
 * returned the way GeckoLib returns it (Optional.empty() for a miss, a new Optional.of(bone) for a hit).
 *
 * The stock search runs instead for any model the memo cannot prove: a top-level list or a child list that is not a
 * CountingBoneList (another factory, a mod-built model) or that handed out its raw array (elements()), a bone whose class
 * overrides getName or getChildBones, a bone with a null name (stock would throw from inside the search), an element that
 * is not a GeoBone (stock would throw), a cycle (stock could recurse forever), or a tree deeper than 256 bones.
 *
 * -Dbons_and_furious.geckolibBoneLookupMemo=false switches it off at run time (the stock search every call).
 * -Dbons_and_furious.geckolibBoneLookupMemo.shadow=true (verification runs): every call runs the stock search too and
 * compares the bone found (identity); SHADOW_CHECKS / SHADOW_MISMATCHES, at most 20 WARN lines; the stock answer is returned.
 */
public final class GeckoBoneMemo {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.geckolibBoneLookupMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.geckolibBoneLookupMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Advanced by every structural change of a CountingBoneList. */
    public static final AtomicLong VERSION = new AtomicLong();
    private static final int MAX_DEPTH = 256;
    private static boolean announced;

    /** One model's answers at one bone-tree version; bones == null: the model is not memoizable at that version. */
    public record Memo(long version, Map<String, GeoBone> bones) {}

    /** Classes whose getName and getChildBones are GeoBone's own (field reads). */
    private static final ClassValue<Boolean> PLAIN = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("getName").getDeclaringClass() == GeoBone.class
                        && type.getMethod("getChildBones").getDeclaringClass() == GeoBone.class;
            } catch (Throwable t) {
                return false;
            }
        }
    };

    private GeckoBoneMemo() {}

    /** The list GeoBone.<init> (children) and BakedModelFactory$Builtin.constructGeoModel (top level) create instead. */
    public static ObjectArrayList<GeoBone> newList() {
        return new CountingBoneList<>();
    }

    /** BakedGeoModel.getBone, wrapped: `memo` is the model's stored memo (may be stale or null). */
    public static Optional<GeoBone> getBone(BakedGeoModel model, Memo memo, String name, Operation<Optional<GeoBone>> original) {
        Map<String, GeoBone> bones = memo.bones();
        if (bones == null) return original.call(name);
        GeoBone found = name == null ? null : bones.get(name);
        if (SHADOW) {
            Optional<GeoBone> stock = original.call(name);
            SHADOW_CHECKS.incrementAndGet();
            if (stock.orElse(null) != found) {
                long n = SHADOW_MISMATCHES.incrementAndGet();
                if (n <= 20) LOGGER.warn("Bons and Furious: geckolib_bone_lookup_memo shadow mismatch #{}: '{}' memo {} stock {}", n, name, found, stock.orElse(null));
            }
            return stock;
        }
        return found == null ? Optional.empty() : Optional.of(found);
    }

    /** The memo for the model at bone-tree version `version` (read before the walk, so a change during it is seen next call). */
    public static Memo build(BakedGeoModel model, long version) {
        List<GeoBone> top = model.topLevelBones();
        if (!(top instanceof CountingBoneList<?> list) || list.exposed()) return new Memo(version, null);
        HashMap<String, GeoBone> bones = new HashMap<>();
        IdentityHashMap<Object, Boolean> path = new IdentityHashMap<>();
        for (int i = 0, n = list.size(); i < n; i++) {
            if (!(list.get(i) instanceof GeoBone bone) || !visit(bone, bones, path, 0)) return new Memo(version, null);
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: geckolib_bone_lookup_memo applies (GeckoLib bone lookups by name are answered from a per-model index){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return new Memo(version, bones);
    }

    /** Pre-order: the bone, then each child followed by its subtree (searchForChildBone's order); false = not provable. */
    private static boolean visit(GeoBone bone, HashMap<String, GeoBone> bones, IdentityHashMap<Object, Boolean> path, int depth) {
        if (depth > MAX_DEPTH || !PLAIN.get(bone.getClass())) return false;
        String name = bone.getName();
        if (name == null) return false;
        bones.putIfAbsent(name, bone);
        if (!(bone.getChildBones() instanceof CountingBoneList<?> children) || children.exposed()) return false;
        if (path.put(bone, Boolean.TRUE) != null) return false;   // the bone is its own ancestor: a cycle
        for (int i = 0, n = children.size(); i < n; i++) {
            if (!(children.get(i) instanceof GeoBone child) || !visit(child, bones, path, depth + 1)) return false;
        }
        path.remove(bone);
        return true;
    }

    /** The current bone-tree version. */
    public static long version() {
        return VERSION.get();
    }
}
