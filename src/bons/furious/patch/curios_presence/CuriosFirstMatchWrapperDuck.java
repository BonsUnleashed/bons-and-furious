package bons.furious.patch.curios_presence;

import java.util.Map;
import net.minecraft.world.entity.LivingEntity;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/**
 * Bons and Furious switch curios_first_match_lean (since 1.0.49): read access to Curios' CurioInventoryWrapper's own slot
 * map and wearer, added by CurioInventoryWrapperFirstMatchMixin (separate from curios_equipped_presence's duck so that each
 * switch can be off on its own). Reads only.
 */
public interface CuriosFirstMatchWrapperDuck {
    Map<String, ICurioStacksHandler> bons$firstMatchCurios();

    LivingEntity bons$firstMatchWearer();
}
