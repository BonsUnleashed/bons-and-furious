package bons.furious.mixin.createsolar_c49;

import bons.furious.patch.createsolar_c49.GogglesMemo;
import com.hachirouwu.createsolar.ClientIntegration;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.lang.reflect.Method;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * createsolar_goggles_reflection_memo (Create: Solar Powered 1.2.6, MIT; client side): wraps the per-frame Class.forName
 * (ordinal 1, inside the goggles try block) and Class.getMethod calls of ClientIntegration.onRenderGui; GogglesMemo answers
 * them from what the same calls answered before (see there why that is identical). The createChecked probe (ordinal 0) and
 * Method.invoke run as shipped.
 */
@Mixin(value = ClientIntegration.class, remap = false)
public abstract class ClientIntegrationGogglesMixin {
    @WrapOperation(method = "onRenderGui(Lnet/minecraftforge/client/event/RenderGuiEvent$Post;)V",
            at = @At(value = "INVOKE", target = "Ljava/lang/Class;forName(Ljava/lang/String;)Ljava/lang/Class;", ordinal = 1),
            require = 1, allow = 1)
    private static Class<?> bons$createsolarC49ForName(String name, Operation<Class<?>> original) {
        return GogglesMemo.enabled ? GogglesMemo.forName(name, original) : original.call(name);
    }

    @WrapOperation(method = "onRenderGui(Lnet/minecraftforge/client/event/RenderGuiEvent$Post;)V",
            at = @At(value = "INVOKE", target = "Ljava/lang/Class;getMethod(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;"),
            require = 1, allow = 1)
    private static Method bons$createsolarC49GetMethod(Class<?> owner, String name, Class<?>[] params, Operation<Method> original) {
        return GogglesMemo.enabled ? GogglesMemo.getMethod(owner, name, params, original) : original.call(owner, name, params);
    }
}
