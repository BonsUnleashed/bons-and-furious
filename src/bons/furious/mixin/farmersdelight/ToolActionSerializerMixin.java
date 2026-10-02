package bons.furious.mixin.farmersdelight;

import bons.furious.patch.farmersdelight.ToolActionItems;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import vectorwing.farmersdelight.common.crafting.ingredient.ToolActionIngredient;

/**
 * farmersdelight_tool_action_items (Farmer's Delight 1.20.1-1.3.4, both sides; acts on clients joining a server).
 *
 * A client decodes every recipe of the server's recipe packet from one FriendlyByteBuf. Decoding a tool-action ingredient
 * now runs in the pass of that buffer (ToolActionItems.enterBuffer: one pass per buffer instance), so the packet's 625
 * tool-action ingredients scan the item registry once per action instead of once each. The decode itself is unchanged.
 * recipeessentials redirects the packet's readList, so the pass hangs on FD's own parse method rather than on the packet.
 */
@Mixin(value = ToolActionIngredient.Serializer.class, remap = false)
public abstract class ToolActionSerializerMixin {
    @WrapMethod(method = "parse(Lnet/minecraft/network/FriendlyByteBuf;)Lvectorwing/farmersdelight/common/crafting/ingredient/ToolActionIngredient;")
    private ToolActionIngredient bons$bufferPass(FriendlyByteBuf buffer, Operation<ToolActionIngredient> original) {
        ToolActionItems.Pass previous = ToolActionItems.enterBuffer(buffer);
        try {
            return original.call(buffer);
        } finally {
            ToolActionItems.leave(previous);
        }
    }
}
