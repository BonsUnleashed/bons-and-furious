package bons.furious.patch.iceandfire_client_c49;

import com.github.alexthe666.iceandfire.entity.EntityDragonBase;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switches iceandfire_dragon_armor_layer_ordinals and iceandfire_dragon_layers_empty_skip (Ice and Fire
 * 2.1.13-1.20.1-beta-5, client, since 1.0.49). Ideas: Collections Of Optimizations (iceandfire.skipEmptyArmorLayer,
 * iceandfire.skipEmptyDragonLayers), idea text only. Our own logic; no Ice and Fire code (LGPL-3.0 target, wrappers only).
 *
 * LayerDragonArmor.render builds two strings per dragon per frame, "type_h_n_l_f" from the four armour ordinals and
 * "type_0_0_0_0", and draws only when they differ; the two strings are equal exactly when the four ordinals are 0 (the
 * ordinals are decimal integers between "_" separators after the same prefix). LayerDragonBanner.render and
 * LayerDragonRider.render push a pose, test for a banner in the off hand / for passengers, and pop the pose: with nothing
 * to draw, the stack is left as it was.
 *
 * What changes: the armour layer returns at once when all four ordinals (the same getItemBySlot / getArmorOrdinal reads,
 * pure) are 0; the banner layer when the off-hand stack is empty or not a banner; the rider layer when the dragon has no
 * passengers (the same reads stock makes after its pushPose). Everything else runs the original method. Why it is
 * identical: in those cases stock draws nothing, calls no buffer and leaves the pose stack as it found it.
 *
 * -Dbons_and_furious.iceandfireDragonArmorLayerOrdinals=false / -Dbons_and_furious.iceandfireDragonLayersEmptySkip=false
 * run the originals.
 */
public final class DragonLayers {
    public static final String ARMOR_KEY = "iceandfire_dragon_armor_layer_ordinals", EMPTY_KEY = "iceandfire_dragon_layers_empty_skip";
    public static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean armorEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.iceandfireDragonArmorLayerOrdinals", "true"));
    public static volatile boolean emptyEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.iceandfireDragonLayersEmptySkip", "true"));
    private static volatile boolean armorAnnounced, emptyAnnounced;

    private DragonLayers() {
    }

    /** True when the armour layer would draw nothing: every armour ordinal is 0. */
    public static boolean bare(EntityDragonBase dragon) {
        if (!armorEnabled) return false;
        // stock reads all four ordinals before comparing, so all four are read here too
        int head = dragon.getArmorOrdinal(dragon.m_6844_(EquipmentSlot.HEAD)), chest = dragon.getArmorOrdinal(dragon.m_6844_(EquipmentSlot.CHEST));
        int legs = dragon.getArmorOrdinal(dragon.m_6844_(EquipmentSlot.LEGS)), feet = dragon.getArmorOrdinal(dragon.m_6844_(EquipmentSlot.FEET));
        boolean bare = (head | chest | legs | feet) == 0;
        if (!bare) return false;
        dragon.dragonType.getName(); // stock builds its strings from the type name next (same exception if it has none)
        if (!armorAnnounced) {
            armorAnnounced = true;
            LOGGER.info("Bons and Furious: {} applies (Ice and Fire's armour layer skips dragons without armour)", ARMOR_KEY);
        }
        return bare;
    }

    /** True when the banner layer would draw nothing: no banner in the off hand. */
    public static boolean noBanner(EntityDragonBase dragon) {
        if (!emptyEnabled) return false;
        ItemStack s = dragon.m_21120_(InteractionHand.OFF_HAND);
        boolean none = s.m_41619_() || !(s.m_41720_() instanceof BannerItem);
        if (none) announceEmpty();
        return none;
    }

    /** True when the rider layer would draw nothing: no passengers. */
    public static boolean noRider(EntityDragonBase dragon) {
        if (!emptyEnabled) return false;
        boolean none = dragon.m_20197_().isEmpty();
        if (none) announceEmpty();
        return none;
    }

    private static void announceEmpty() {
        if (!emptyAnnounced) {
            emptyAnnounced = true;
            LOGGER.info("Bons and Furious: {} applies (Ice and Fire's banner and rider layers skip dragons with nothing to draw)", EMPTY_KEY);
        }
    }
}
