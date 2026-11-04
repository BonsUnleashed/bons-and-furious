package bons.furious.patch.enigmaticlegacy_coo49;

import com.aizistral.enigmaticlegacy.api.items.ICreativeTabMember;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch enigmaticlegacy_creative_tab_members_memo (Enigmatic Legacy 2.30.1, both sides). Ours; no
 * Enigmatic Legacy code is carried. Idea: Collections Of Optimizations (enigmaticlegacy.groupCreativeTabMembers), idea
 * text only.
 *
 * EnigmaticLegacy.onCreativeTabContents runs for every creative tab build event (one per tab, each time the creative tabs
 * are rebuilt: joining a world, the creative screen after a permission or feature change, recipe viewers) and hands every
 * registered item, in ForgeRegistries.ITEMS' own order, to a lambda that returns at once unless the item implements
 * ICreativeTabMember. In a large pack that is tens of thousands of registry steps (an Integer box and a map lookup each)
 * per tab, for the few hundred items of Enigmatic Legacy and its add-ons.
 *
 * Here the members are kept once, in the registry's order, each with its registry id; on every event the kept list is
 * checked against the registry (the same registry object, the same number of keys, every kept member still at its id)
 * and then handed to the same lambda in the same order. ForgeRegistry iterates by ascending id, so unchanged ids mean the
 * same members in the same order; any change (a remap when joining a server or leaving it, a new entry) makes the next
 * event walk the registry as shipped and keep the new list. Only ForgeRegistry instances are kept; anything else runs the
 * original call. The lambda's calls for members (getTab, getCreativeTab, getCreativeTabStacks, accept) and their order are
 * unchanged; for other items it does nothing, which is what is left out. Flag:
 * -Dbons_and_furious.enigmaticlegacyCreativeTabMembersMemo=false; shadow (walks as shipped too and compares):
 * -Dbons_and_furious.enigmaticlegacyCreativeTabMembersMemo.shadow=true.
 */
public final class CreativeTabMembers {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.enigmaticlegacyCreativeTabMembersMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.enigmaticlegacyCreativeTabMembersMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong();
    public static final AtomicLong SHADOW_MISMATCHES = new AtomicLong();
    /** Walks of the registry (first event and every change) and events answered from the kept list. */
    public static final AtomicLong WALKS = new AtomicLong();
    public static final AtomicLong REUSES = new AtomicLong();
    private static volatile Kept kept;
    private static volatile boolean announced;

    private CreativeTabMembers() {
    }

    /** The kept members of one registry, in its iteration order, with the registry id of each and its key count. */
    private static final class Kept {
        final ForgeRegistry<?> registry;
        final Object[] members;
        final int[] ids;
        final int keys;

        Kept(ForgeRegistry<?> registry, Object[] members, int[] ids, int keys) {
            this.registry = registry;
            this.members = members;
            this.ids = ids;
            this.keys = keys;
        }
    }

    /** The wrapped call: {@code registry.forEach(consumer)}. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void forEach(IForgeRegistry<?> registry, Consumer<?> consumer, Operation<Void> original) {
        if (!enabled || !(registry instanceof ForgeRegistry<?> forge)) {
            original.call(registry, consumer);
            return;
        }
        Kept k = current(forge);
        if (SHADOW) shadow(forge, k);
        Consumer<Object> c = (Consumer<Object>) consumer;
        for (Object member : k.members) c.accept(member);
    }

    private static Kept current(ForgeRegistry<?> forge) {
        Kept k = kept;
        if (k != null && k.registry == forge && unchanged(k, forge)) {
            REUSES.incrementAndGet();
            return k;
        }
        k = walk(forge);
        kept = k;
        WALKS.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: enigmaticlegacy_creative_tab_members_memo applies ({} creative tab members kept for Enigmatic Legacy's tab events)", k.members.length);
        }
        return k;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean unchanged(Kept k, ForgeRegistry forge) {
        if (forge.getKeys().size() != k.keys) return false;
        Object[] members = k.members;
        int[] ids = k.ids;
        for (int i = 0; i < members.length; i++) {
            if (forge.getID(members[i]) != ids[i]) return false;
        }
        return true;
    }

    /** The walk as shipped (the registry's own iterator), keeping the members, their ids and the key count. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Kept walk(ForgeRegistry forge) {
        int keys = forge.getKeys().size();
        List<Object> members = new ArrayList<>();
        for (Object item : (Iterable<Object>) forge) {
            if (item instanceof ICreativeTabMember) members.add(item);
        }
        Object[] m = members.toArray();
        int[] ids = new int[m.length];
        for (int i = 0; i < m.length; i++) ids[i] = forge.getID(m[i]);
        return new Kept(forge, m, ids, keys);
    }

    /** Shadow check: the members a walk as shipped meets now, compared with the kept list (identity and order). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void shadow(ForgeRegistry forge, Kept k) {
        List<Object> now = new ArrayList<>();
        for (Object item : (Iterable<Object>) forge) {
            if (item instanceof ICreativeTabMember) now.add(item);
        }
        SHADOW_CHECKS.incrementAndGet();
        boolean same = now.size() == k.members.length;
        for (int i = 0; same && i < k.members.length; i++) same = now.get(i) == k.members[i];
        if (!same && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
            LOGGER.warn("Bons and Furious: enigmaticlegacy_creative_tab_members_memo shadow mismatch: {} kept members, {} by a walk as shipped", k.members.length, now.size());
        }
    }
}
