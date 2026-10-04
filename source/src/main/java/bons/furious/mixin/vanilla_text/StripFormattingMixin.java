package bons.furious.mixin.vanilla_text;

import bons.furious.patch.vanilla_text.StripFormatting;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.ChatFormatting;
import org.spongepowered.asm.mixin.Mixin;

/**
 * vanilla_strip_formatting_fast_path (Minecraft 1.21.1 with NeoForge 21.1.252, both sides). Mojang member names.
 *
 * ChatFormatting.stripFormatting runs a regex replaceAll over every string it gets. A string without the section sign
 * U+00A7 cannot match the pattern and replaceAll then returns the very same String, so such strings are returned
 * directly (StripFormatting explains why the result is identical). null and strings with U+00A7 run the original, as
 * does everything with the runtime switch off. No Minecraft code is carried.
 *
 * Ported to 1.21.1: unchanged (same method, descriptor and pattern in 1.21.1).
 */
@Mixin(value = ChatFormatting.class, remap = false)
public abstract class StripFormattingMixin {
    @WrapMethod(method = "stripFormatting(Ljava/lang/String;)Ljava/lang/String;")
    private static String bons$withoutCodes(String text, Operation<String> original) {
        if (StripFormatting.enabled && StripFormatting.unchanged(text)) {
            if (StripFormatting.SHADOW) StripFormatting.shadow(text, original.call(text));
            return text;
        }
        return original.call(text);
    }
}
