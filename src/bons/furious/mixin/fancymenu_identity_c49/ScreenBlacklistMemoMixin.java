package bons.furious.mixin.fancymenu_identity_c49;

import bons.furious.patch.fancymenu_identity_c49.ScreenIdentityMemo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import de.keksuccino.fancymenu.customization.ScreenCustomization;
import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * fancymenu_screen_identity_memo, part 1 (FancyMenu 3.9.14; client side): ScreenCustomization.isScreenBlacklisted(String)
 * answers through ScreenIdentityMemo: FancyMenu's own rules once per class name, other mods' rules called as the loop
 * calls them (a null rule or a changed list: the original method runs); addScreenBlacklistRule, the rule list's only
 * writer, drops everything kept at its end.
 */
@Mixin(value = ScreenCustomization.class, remap = false)
public abstract class ScreenBlacklistMemoMixin {
    @Shadow @Final private static List<ScreenCustomization.ScreenBlacklistRule> SCREEN_BLACKLIST_RULES;

    @WrapMethod(method = "isScreenBlacklisted(Ljava/lang/String;)Z")
    private static boolean bons$fancymenuIdentityC49Blacklisted(String screenClassPath, Operation<Boolean> original) {
        Boolean answer = ScreenIdentityMemo.blacklisted(screenClassPath, SCREEN_BLACKLIST_RULES);
        return answer != null ? answer : original.call(screenClassPath);
    }

    @Inject(method = "addScreenBlacklistRule(Lde/keksuccino/fancymenu/customization/ScreenCustomization$ScreenBlacklistRule;)V", at = @At("TAIL"), require = 1, allow = 1)
    private static void bons$fancymenuIdentityC49RulesChanged(ScreenCustomization.ScreenBlacklistRule rule, CallbackInfo ci) {
        ScreenIdentityMemo.rulesChanged();
    }
}
