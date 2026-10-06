package bons.furious.mixin.jei_startup;

import bons.furious.patch.jei_startup.TooltipWords;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.Set;
import mezz.jei.gui.ingredients.ListElementInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ListElementInfo.class, remap = false)
public abstract class TooltipWordsMixin {
    @WrapMethod(method = "getStrings")
    private static Set<String> bons$words(List<Component> tooltip, Operation<Set<String>> original) {
        return TooltipWords.enabled ? TooltipWords.read(tooltip) : original.call(tooltip);
    }
}
