package bons.furious.mixin.fancymenu_identity_c49;

import bons.furious.patch.fancymenu_identity_c49.ScreenIdentityMemo;
import de.keksuccino.fancymenu.customization.screen.identifier.ScreenIdentifierHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * fancymenu_screen_identity_memo, part 3 (FancyMenu 3.9.14; client side): the Class.forName(identifier, false, loader)
 * call inside ScreenIdentifierHandler.isValidIdentifier returns the Class an earlier call of the same name found (classes
 * found stay found while their loader lives); a name not found, or an error, runs forName again, as before.
 */
@Mixin(value = ScreenIdentifierHandler.class, remap = false)
public abstract class IdentifierClassMemoMixin {
    @Redirect(method = "isValidIdentifier(Ljava/lang/String;)Z",
            at = @At(value = "INVOKE", target = "Ljava/lang/Class;forName(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;"),
            require = 1, allow = 1)
    private static Class<?> bons$fancymenuIdentityC49ForName(String name, boolean initialize, ClassLoader loader) throws ClassNotFoundException {
        return ScreenIdentityMemo.forName(name, initialize, loader);
    }
}
