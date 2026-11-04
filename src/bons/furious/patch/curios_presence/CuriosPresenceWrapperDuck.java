package bons.furious.patch.curios_presence;

import java.util.Map;
import net.minecraft.world.entity.LivingEntity;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/**
 * Bons and Furious switch curios_equipped_presence (since 1.0.49): read access to Curios' CurioInventoryWrapper's own slot
 * map and wearer, added by CurioInventoryWrapperPresenceMixin (the fields are package-private in Curios). Reads only.
 */
public interface CuriosPresenceWrapperDuck {
    Map<String, ICurioStacksHandler> bons$presenceCurios();

    LivingEntity bons$presenceWearer();
}
