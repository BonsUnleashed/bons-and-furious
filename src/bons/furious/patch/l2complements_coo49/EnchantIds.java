package bons.furious.patch.l2complements_coo49;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch l2complements_enchant_id_string_compare (L2 Complements 2.6.1, both sides). L2 Complements is All
 * Rights Reserved: none of its code is carried. Ours, idea from Collections Of Optimizations
 * (l2complements.leanEnchantmentLookup), idea text only.
 *
 * What L2 Complements does: EnchUtils.getTagEnchantmentLevel(enchantment, stack), called from its ItemStack mixin on every
 * getMaxDamage of an enchanted stack (the durability bar of every damageable stack drawn, every damage check), looks the
 * enchantment's id up and then, for every entry of the stack's enchantment list, parses the entry's "id" string with
 * ResourceLocation.tryParse (a new ResourceLocation, two substrings, a character check of both parts) only to compare it
 * with that id; the first equal entry's "lvl" (clamped to 0..255) is the answer, else 0.
 *
 * What the switch does (EnchantIdLevelMixin, a cancellable HEAD inject): the same walk compares strings instead. tryParse(s)
 * equals the location ns:path exactly when s is "ns:path", or, for the minecraft namespace only, "path" or ":path"
 * (ResourceLocation splits at the first ':', an empty or missing namespace means minecraft, and a valid location has no ':'
 * in either part; any other string either fails to parse or names another location). Same reads in the same order (isEmpty,
 * the registry key, the enchantment list, each entry's "id" and the matching entry's "lvl"), same answer, no parsing and
 * no allocation. An enchantment without a registry key runs the shipped method.
 *
 * Flag: -Dbons_and_furious.l2complementsEnchantIdStringCompare=false runs the shipped method. SHADOW MODE (rigs):
 * -Dbons_and_furious.l2complementsEnchantIdStringCompare.shadow=true also computes the shipped answer (with tryParse) on every
 * call, answers with it, counts SHADOW_CHECKS / SHADOW_MISMATCHES and logs the first 20 mismatches.
 */
public final class EnchantIds {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.l2complementsEnchantIdStringCompare", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.l2complementsEnchantIdStringCompare.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** "Run the shipped method". */
    public static final int UNKNOWN = Integer.MIN_VALUE;
    private static volatile boolean announced;

    private EnchantIds() {
    }

    /** True when ResourceLocation.tryParse(id) equals ns:path (ns and path from a valid location, mc = ns is minecraft). */
    public static boolean matches(String id, String ns, String path, boolean mc) {
        int nl = ns.length(), pl = path.length(), il = id.length();
        if (il == nl + 1 + pl && id.charAt(nl) == ':' && id.startsWith(ns) && id.endsWith(path)) return true;
        if (!mc) return false;
        if (il == pl) return id.equals(path);
        return il == pl + 1 && id.charAt(0) == ':' && id.endsWith(path);
    }

    /** The shipped getTagEnchantmentLevel's answer, or UNKNOWN when the shipped method must run. */
    public static int level(Enchantment enchantment, ItemStack stack) {
        if (stack.m_41619_()) return 0;
        ResourceLocation target = BuiltInRegistries.f_256876_.m_7981_(enchantment);
        if (target == null) return UNKNOWN;
        String ns = target.m_135827_(), path = target.m_135815_();
        boolean mc = "minecraft".equals(ns);
        ListTag list = stack.m_41785_();
        int answer = 0;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.m_128728_(i);
            if (matches(entry.m_128461_("id"), ns, path, mc)) {
                answer = Mth.m_14045_(entry.m_128451_("lvl"), 0, 255);
                break;
            }
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: l2complements_enchant_id_string_compare applies (L2 Complements' durability enchantment lookup compares ids without parsing them)");
        }
        if (SHADOW) {
            int shipped = shipped(target, list);
            SHADOW_CHECKS.incrementAndGet();
            if (shipped != answer) {
                long n = SHADOW_MISMATCHES.incrementAndGet();
                if (n <= 20) LOGGER.warn("Bons and Furious: l2complements_enchant_id_string_compare shadow mismatch #{}: {} on {}: {} vs {}", n, target, list, answer, shipped);
            }
            return shipped;
        }
        return answer;
    }

    /** The shipped walk (tryParse + equals), for shadow mode. */
    static int shipped(ResourceLocation target, ListTag list) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.m_128728_(i);
            ResourceLocation id = ResourceLocation.m_135820_(entry.m_128461_("id"));
            if (id != null && id.equals(target)) return Mth.m_14045_(entry.m_128451_("lvl"), 0, 255);
        }
        return 0;
    }
}
