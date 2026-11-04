package bons.furious.mixin.curios_presence;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import top.theillusivec4.curios.common.inventory.CurioStacksHandler;

/**
 * curios_equipped_presence (Curios API 5.14.1+1.20.1, since 1.0.49): read access to CurioStacksHandler.update, the flag
 * flagUpdate sets and update() clears (while it is set, getStacks / getSlots / getRenders resize the type, post
 * SlotModifiersUpdatedEvent and reset an open curios menu). CuriosPresence answers "unknown" while it is set. Adds no
 * behaviour.
 */
@Mixin(value = CurioStacksHandler.class, remap = false)
public interface CurioStacksHandlerPresenceAccessor {
    @Accessor(value = "update", remap = false)
    boolean bons$presencePendingUpdate();
}
