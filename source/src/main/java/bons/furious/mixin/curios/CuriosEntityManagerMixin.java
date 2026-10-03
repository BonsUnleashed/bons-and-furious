package bons.furious.mixin.curios;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.common.data.CuriosEntityManager;

/**
 * curios_slot_map_lookup (Curios API 5.14.1+1.20.1, both sides).
 *
 * getEntitySlots probed the entity-slot map twice (containsKey, then get). After a data reload that map is an
 * ImmutableMap, which never holds null values, so a single get answers both questions: the same slot map, or the
 * same empty map when the entity type has none. Any other map implementation keeps the original two-probe path.
 */
@Mixin(value = CuriosEntityManager.class, remap = false)
public abstract class CuriosEntityManagerMixin {
    @Shadow
    private Map<EntityType<?>, Map<String, ISlotType>> entitySlots;

    /**
     * @author BonsUnleashed
     * @reason One map probe instead of two for the immutable entity-slot map.
     */
    @Overwrite
    public Map<String, ISlotType> getEntitySlots(EntityType<?> type) {
        Map<EntityType<?>, Map<String, ISlotType>> slots = this.entitySlots;
        if (slots instanceof ImmutableMap) {
            Map<String, ISlotType> found = slots.get(type);
            if (found != null) {
                return found;
            }
            return ImmutableMap.of();
        }

        if (this.entitySlots.containsKey(type)) {
            return this.entitySlots.get(type);
        }
        return ImmutableMap.of();
    }
}
