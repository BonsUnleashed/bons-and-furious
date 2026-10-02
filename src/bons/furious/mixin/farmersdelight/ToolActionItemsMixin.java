package bons.furious.mixin.farmersdelight;

import bons.furious.patch.farmersdelight.ToolActionItems;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.stream.Stream;
import net.minecraftforge.common.ToolAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import vectorwing.farmersdelight.common.crafting.ingredient.ToolActionIngredient;

/**
 * farmersdelight_tool_action_items (Farmer's Delight 1.20.1-1.3.4, both sides).
 *
 * The constructor hands Ingredient's constructor a lazy stream over every registered item. Inside a recipe-load pass
 * (opened by RecipeLoadPassMixin and ToolActionSerializerMixin) that stream is replaced, before anything consumed it, by
 * ToolActionItems.values: the original stream wrapped to record its items for the first ingredient of an action, fresh
 * stacks of the recorded items for the following ones. Outside a pass, or with the switch off, the stream is untouched.
 * Farmer's Delight is MIT; no FD code is carried (the replacement pipeline is our own and mirrors the three stages).
 */
@Mixin(value = ToolActionIngredient.class, remap = false)
public abstract class ToolActionItemsMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/Ingredient;<init>(Ljava/util/stream/Stream;)V"))
    private static Stream<?> bons$values(Stream<?> values, @Local(argsOnly = true) ToolAction action) {
        return ToolActionItems.values(action, values);
    }
}
