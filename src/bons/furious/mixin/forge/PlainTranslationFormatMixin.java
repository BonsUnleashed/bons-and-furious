package bons.furious.mixin.forge;

import bons.furious.patch.forge.PlainTranslation;
import net.minecraftforge.common.ForgeI18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * forge_plain_translation_format (Forge 47.4.16).
 *
 * Forge formats every translation template through ForgeI18n.parseFormat, which builds an ExtendedMessageFormat (a parsed
 * MessageFormat) and formats the arguments into it. A template with no '{', '}' or '\'' contains no format element and no
 * quoting, so MessageFormat copies it character for character: it is returned as it is (a template with any of those
 * characters, or null, takes the original path, quotes and exceptions included). Static and on a cold path, so an @Inject.
 */
@Mixin(value = ForgeI18n.class, remap = false)
public abstract class PlainTranslationFormatMixin {
    @Inject(method = "parseFormat", at = @At("HEAD"), cancellable = true)
    private static void bons$plainTemplate(String format, Object[] args, CallbackInfoReturnable<String> cir) {
        if (PlainTranslation.plain(format)) cir.setReturnValue(format);
    }
}
