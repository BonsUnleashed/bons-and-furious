package bons.furious.patch.fromtheshadows_coo49;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.sonmok14.fromtheshadows.server.utils.registry.ItemRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch fromtheshadows_mask_tick_precheck (From The Shadows Reborn 2.9, All Rights Reserved; both
 * sides). Ours; no From The Shadows code is carried. Idea: Collections Of Optimizations (fromtheshadows.skipUnmaskedTick),
 * idea text only.
 *
 * ServerEvents.onLivingUpdateEvent runs for every living entity on every tick on both sides: it fills a list with the
 * items of all equipment slots (never read), then for each of the six slots adds that slot's item to an "armour" list when
 * the slot is an armour slot and asks whether the armour list contains the plague doctor mask (ArrayList.containsAll of a
 * one-element list: the mask item's equals, i.e. identity); only then, and only on a server level, does it act (cure the
 * plague, remove a curable harmful effect, cool the mask down, wear it). So for an entity with no mask item in its feet,
 * legs, chest or head slot, and for every entity on a client level, every iteration ends after reads only (getAllSlots,
 * getItemBySlot, getItem, the mask's registry object).
 *
 * cannotAct answers exactly that: a client level, or none of the four armour slots holds the mask item (the same stacks
 * the handler would read, the same identity test). Then the handler returns at once (FtsMaskTickMixin); otherwise it runs
 * as shipped. Flag: -Dbons_and_furious.fromtheshadowsMaskTickPrecheck=false.
 */
public final class FtsMask {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.fromtheshadowsMaskTickPrecheck", "true"));
    private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private static volatile boolean announced;

    private FtsMask() {
    }

    /** True when the handler cannot act for this entity this tick (see the class comment). */
    public static boolean cannotAct(LivingEntity e) {
        if (!enabled || e == null) return false;
        // asked first, as the original asks it in its first iteration on both sides (an absent registry object throws the
        // same error from the same handler)
        Item mask = ItemRegistry.PLAGUE_DOCTOR_MASK.get();
        Level level = e.m_9236_();
        if (level != null && level.f_46443_) return announce();
        for (EquipmentSlot slot : ARMOUR) {
            ItemStack s = e.m_6844_(slot);
            if (s != null && s.m_41720_() == mask) return false;
        }
        return announce();
    }

    private static boolean announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: fromtheshadows_mask_tick_precheck applies (From The Shadows' mask handler returns at once for entities without a plague doctor mask)");
        }
        return true;
    }
}
