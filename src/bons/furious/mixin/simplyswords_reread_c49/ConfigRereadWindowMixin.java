package bons.furious.mixin.simplyswords_reread_c49;

import bons.furious.patch.simplyswords_reread_c49.RereadWindow;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.sweenus.simplyswords.config.Config;
import org.spongepowered.asm.mixin.Mixin;

/**
 * simplyswords_config_reread_interval (Simply Swords 1.56.0-1.20.1, both sides; a trade-off, off by default): a MixinExtras
 * @WrapMethod around Config.safeValueFetch that returns at once while the same type's map was filled from the same file at
 * most the switch's value in ms ago (RereadWindow). Priority 1100: outside simplyswords_config_unchanged_reuse's wrapper.
 * Our own logic only.
 */
@Mixin(value = Config.class, priority = 1100, remap = false)
public abstract class ConfigRereadWindowMixin {
    @WrapMethod(method = "safeValueFetch")
    private static void bons$rereadWindow(String type, String parent, Operation<Void> original) {
        RereadWindow.fetch(type, parent, original);
    }
}