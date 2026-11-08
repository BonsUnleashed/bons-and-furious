package bons.furious.mixin.vanilla_lookup_loops;

import bons.furious.patch.vanilla_lookup_loops.RlStrings;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * vanilla_rl_string_memo (Minecraft 1.21.1 as patched by NeoForge 21.1.252, both sides). ResourceLocation.toString: a
 * MixinExtras @ModifyReturnValue at its return keeps the first string it builds in an @Unique field, and a cancellable HEAD
 * injection returns that string on later calls (in shadow mode the string is built and compared instead). Only our own
 * logic is carried. See RlStrings.
 */
@Mixin(value = ResourceLocation.class, remap = false)
public abstract class ResourceLocationStringMixin {
    /** The text this id's first toString built (null until then). */
    @Unique
    private String bons$text;

    @Inject(method = "toString()Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    private void bons$keptText(CallbackInfoReturnable<String> cir) {
        String text = this.bons$text;
        if (text != null && RlStrings.enabled && !RlStrings.SHADOW) cir.setReturnValue(text);
    }

    @ModifyReturnValue(method = "toString()Ljava/lang/String;", at = @At("RETURN"))
    private String bons$keepText(String text) {
        if (RlStrings.enabled) {
            String kept = this.bons$text;
            if (kept == null) this.bons$text = RlStrings.first(text);
            else if (RlStrings.SHADOW) RlStrings.shadow(kept, text);
        }
        return text;
    }
}
