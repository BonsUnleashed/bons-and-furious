package bons.furious.patch.domesticationinnovation_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch domesticationinnovation_enchant_id_compare (Domestication Innovation 1.7.1, both sides).
 *
 * TameableUtils.getEnchantLevel(pet, enchantment) (hasEnchant: about 20 questions per collared pet per server tick, ~8
 * per pet per frame in the pet layers) walks the pet's stored enchantment list and, per entry, parses the entry's "id"
 * string into a new ResourceLocation (EnchantmentHelper.getEnchantmentId = ResourceLocation.tryParse(tag.getString("id")))
 * only to compare it with the enchantment's registry key: `res == null || !res.equals(key)` -> next entry.
 *
 * The switch answers that parse for the comparison without building a ResourceLocation, from the key's text
 * "namespace:path" (memoised per Enchantment; a registry key never changes once registered):
 *  - id equals the key text: tryParse(id) would equal the key, so the key itself is returned (equals -> true; the
 *    method then reads the level exactly as before);
 *  - id's first ':' is at index 1 or later and id differs from the key text: tryParse splits there, so the parsed
 *    namespace:path differs from the key (or the id is invalid and tryParse gives null); null is returned and the entry is
 *    skipped exactly as before;
 *  - anything else (no ':' or a leading ':', where tryParse fills in "minecraft"; an enchantment without a key): the
 *    original parse runs.
 * The list, the config read, the order of entries, the level read and the result are unchanged; only the throwaway
 * ResourceLocation (and, for skipped entries, the registry lookup that follows it) disappear. Runtime flag:
 * -Dbons_and_furious.domesticationEnchantIdCompare=false. Shadow mode (.shadow=true) also runs the original parse for
 * every shortcut and compares the decision (match / skip), WARN for up to 20 mismatches. No Domestication Innovation code is
 * carried (LGPL-3.0); the wrapper only calls our logic or DI's own call.
 */
public final class EnchantIds {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.domesticationEnchantIdCompare", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.domesticationEnchantIdCompare.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong();
    public static final AtomicLong SHADOW_MISMATCHES = new AtomicLong();
    private static final ConcurrentHashMap<Enchantment, Key> KEYS = new ConcurrentHashMap<>();
    private static volatile boolean announced;

    /** A registry key and its text. */
    public record Key(ResourceLocation location, String text) {
    }

    private EnchantIds() {
    }

    /** The memoised key of a registered enchantment, or null (unregistered: not memoised, the original decides). */
    public static Key key(Enchantment enchantment) {
        Key k = KEYS.get(enchantment);
        if (k != null) return k;
        ResourceLocation rl = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
        if (rl == null) return null;
        k = new Key(rl, rl.toString());
        Key prev = KEYS.putIfAbsent(enchantment, k);
        return prev != null ? prev : k;
    }

    /**
     * What the comparison needs from the entry's id: the key itself (match), null (no match), or the original parse.
     * `shortcut(id, key)` is the pure decision; resolve wires it to the wrapped call.
     */
    public static ResourceLocation resolve(CompoundTag entry, Enchantment enchantment, Operation<ResourceLocation> original) {
        if (!enabled || enchantment == null) return original.call(entry);
        Key k = key(enchantment);
        if (k == null) return original.call(entry);
        String id = entry.m_128461_("id");
        int s = shortcut(id, k.text());
        if (s == ORIGINAL) return original.call(entry);
        ResourceLocation answer = s == MATCH ? k.location() : null;
        if (SHADOW) shadow(entry, k, answer, original);
        else if (!announced) announce();
        return answer;
    }

    public static final int MATCH = 1, NO_MATCH = 0, ORIGINAL = -1;

    /** MATCH when id is the key text, NO_MATCH when id has its first ':' at index >= 1 and differs, else ORIGINAL. */
    public static int shortcut(String id, String keyText) {
        if (id.equals(keyText)) return MATCH;
        return id.indexOf(':') >= 1 ? NO_MATCH : ORIGINAL;
    }

    private static void shadow(CompoundTag entry, Key k, ResourceLocation answer, Operation<ResourceLocation> original) {
        ResourceLocation parsed = original.call(entry);
        boolean stockMatch = parsed != null && parsed.equals(k.location());
        boolean ourMatch = answer != null && answer.equals(k.location());
        SHADOW_CHECKS.incrementAndGet();
        if (stockMatch != ourMatch) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) LOGGER.warn("Bons and Furious: domesticationinnovation_enchant_id_compare shadow mismatch #{}: id {} key {}: original {}, ours {}",
                    n, entry.m_128461_("id"), k.text(), stockMatch, ourMatch);
        }
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: domesticationinnovation_enchant_id_compare: pet enchantment ids are compared by their text");
    }
}
