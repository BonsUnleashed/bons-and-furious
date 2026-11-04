package bons.furious.patch.mysticalagriculture_coo49;

import com.blakebr0.mysticalagriculture.api.MysticalAgricultureAPI;
import com.blakebr0.mysticalagriculture.api.tinkering.Augment;
import com.blakebr0.mysticalagriculture.api.tinkering.ITinkerable;
import com.blakebr0.mysticalagriculture.api.util.AugmentUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;

/**
 * Bons and Furious 1.0.49 switch mysticalagriculture_augment_lookup_lean (Mystical Agriculture 7.0.24, both sides; kind
 * opt, default on; helper of AugmentLookupMixin; no Mystical Agriculture code carried).
 *
 * What Mystical Agriculture does. AugmentUtils.getAugment(stack, slot) (per slot of every tinkerable tool and armour piece:
 * from the tools' inventoryTick, the armour's onArmorTick, the augment tick handler on both sides, the tinkering table)
 * reads the stack's tag, asks the item for its slot count and builds the key "Augment-" + slot twice (contains, then
 * getString), then parses the stored id into a new ResourceLocation for the registry lookup. getArmorAugments(player)
 * gathers the four armour pieces' lists (getAugments: a new list per piece) into a fifth list.
 *
 * What the switch does. Past the mod's own tag check (a stack without a tag never reaches the switch), getAugment makes the
 * same reads in the same order (the tag again: a plain field, item, getAugmentSlots, contains, getString, the registry's
 * getAugmentById), with the slot keys "Augment-0".."Augment-15" built once and the id's ResourceLocation
 * parsed once per distinct id string (at most 512 kept; ResourceLocation is immutable and equal strings parse to equal
 * locations, so the registry is asked with an equal location; a malformed id still throws the same exception, from the same
 * parse, and is never kept). getArmorAugments makes the same calls per armour piece (tag, item, slot count, getAugment per
 * slot) and adds the augments straight into the one list it returns, in the same order. Callers still get their own lists.
 * Runtime flag bons_and_furious.mysticalagricultureAugmentLookupLean.
 */
public final class AugmentLookup {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mysticalagricultureAugmentLookupLean", "true"));
    private static final String[] KEYS = new String[16];
    private static final ConcurrentHashMap<String, ResourceLocation> LOCATIONS = new ConcurrentHashMap<>();
    private static final int MAX_LOCATIONS = 512;
    private static volatile boolean announced;

    static {
        for (int i = 0; i < KEYS.length; i++) KEYS[i] = "Augment-" + i;
    }

    private AugmentLookup() {
    }

    /** AugmentUtils.getAugment(stack, slot), as described above (reached once the mod found a tag; null kept for safety). */
    public static Augment augment(ItemStack stack, int slot) {
        CompoundTag nbt = stack.m_41783_();
        if (nbt == null) return null;
        Item item = stack.m_41720_();
        if (item instanceof ITinkerable tinkerable && slot < tinkerable.getAugmentSlots()) {
            String key = slot >= 0 && slot < KEYS.length ? KEYS[slot] : "Augment-" + slot;
            if (nbt.m_128441_(key)) {
                String name = nbt.m_128461_(key);
                if (!announced) announce();
                return MysticalAgricultureAPI.getAugmentRegistry().getAugmentById(location(name));
            }
        }
        return null;
    }

    /** The ResourceLocation of an augment id string (parsed as the original does; kept once it parsed). */
    static ResourceLocation location(String name) {
        ResourceLocation known = LOCATIONS.get(name);
        if (known != null) return known;
        ResourceLocation parsed = new ResourceLocation(name);
        if (LOCATIONS.size() < MAX_LOCATIONS) LOCATIONS.putIfAbsent(name, parsed);
        return parsed;
    }

    /** AugmentUtils.getArmorAugments(player), as described above. */
    public static List<Augment> armor(Player player) {
        NonNullList<ItemStack> armor = player.m_150109_().f_35975_;
        ArrayList<Augment> out = new ArrayList<>();
        for (ItemStack stack : armor) {
            CompoundTag nbt = stack.m_41783_();
            if (nbt == null) continue;
            Item item = stack.m_41720_();
            if (item instanceof ITinkerable tinkerable) {
                int slots = tinkerable.getAugmentSlots();
                for (int i = 0; i < slots; i++) {
                    Augment augment = AugmentUtils.getAugment(stack, i);
                    if (augment != null) out.add(augment);
                }
            }
        }
        return out;
    }

    private static void announce() {
        announced = true;
        LogManager.getLogger("Bons and Furious").info("Bons and Furious: mysticalagriculture_augment_lookup_lean applies (augment slots are read without building their keys and parsing their ids on every look-up)");
    }
}
