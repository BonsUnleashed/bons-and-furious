package bons.furious.mixin.fancymenu_identity_c49;

import bons.furious.patch.fancymenu_identity_c49.ScreenIdentityMemo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import de.keksuccino.fancymenu.customization.screen.identifier.UniversalScreenIdentifierRegistry;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * fancymenu_screen_identity_memo, part 2 (FancyMenu 3.9.14; client side):
 * UniversalScreenIdentifierRegistry.getUniversalIdentifierFor(String) answers its walk over every registered universal
 * identifier from ScreenIdentityMemo's index (screen class -> first identifier registered for it); register, the map's only
 * writer, drops the index at its end; the next lookups walk as before until the index is built again from the map.
 */
@Mixin(value = UniversalScreenIdentifierRegistry.class, remap = false)
public abstract class UniversalIdentifierIndexMixin {
    @Shadow @Final private static Map<String, String> UNIVERSAL_IDENTIFIERS;

    @WrapMethod(method = "getUniversalIdentifierFor(Ljava/lang/String;)Ljava/lang/String;")
    private static String bons$fancymenuIdentityC49UniversalFor(String screenClassPath, Operation<String> original) {
        if (!ScreenIdentityMemo.enabled) return original.call(screenClassPath);
        String answer = ScreenIdentityMemo.universalFor(screenClassPath, UNIVERSAL_IDENTIFIERS);
        return answer == ScreenIdentityMemo.ORIGINAL ? original.call(screenClassPath) : answer;
    }

    @Inject(method = "register(Ljava/lang/String;Ljava/lang/String;)V", at = @At("TAIL"), require = 1, allow = 1)
    private static void bons$fancymenuIdentityC49Registered(String universalIdentifier, String targetScreenClassPath, CallbackInfo ci) {
        ScreenIdentityMemo.universalChanged();
    }
}
