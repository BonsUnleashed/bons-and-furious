package bons.furious.mixin.alexscaves;

import com.github.alexmodguy.alexscaves.server.entity.util.MagnetUtil;
import com.github.alexmodguy.alexscaves.server.misc.ACTagRegistry;
import com.github.alexmodguy.alexscaves.server.potion.ACEffectRegistry;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

/**
 * alexscaves_equipment_enumeration (Alex's Caves 2.0.2).
 *
 * MagnetUtil.isDynamicallyMagnetic called EquipmentSlot.values() for every full equipment scan, and that enum method
 * returns a new copy of the array on each call. The scan now walks one private copy made when the class loads, in the
 * same slot order, so the answer is unchanged.
 */
@Mixin(value = MagnetUtil.class, remap = false)
public abstract class MagnetUtilEquipmentMixin {
    @Unique
    private static final EquipmentSlot[] ac$equipmentSlots = EquipmentSlot.values();

    /**
     * @author BonsUnleashed
     * @reason Iterate a cached EquipmentSlot array instead of cloning EquipmentSlot.values() per call.
     */
    @Overwrite
    private static boolean isDynamicallyMagnetic(LivingEntity entity, boolean legsOnly) {
        if (entity.m_21023_(ACEffectRegistry.MAGNETIZING.get())) {
            return true;
        }
        if (legsOnly) {
            return entity.m_6844_(EquipmentSlot.FEET).m_204117_(ACTagRegistry.MAGNETIC_ITEMS);
        }
        for (EquipmentSlot slot : ac$equipmentSlots) {
            if (entity.m_6844_(slot).m_204117_(ACTagRegistry.MAGNETIC_ITEMS)) {
                return true;
            }
        }
        return false;
    }
}
