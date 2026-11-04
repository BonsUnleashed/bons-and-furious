package bons.furious.mixin.neobackports_c49;

import bons.furious.patch.neobackports_c49.DefaultsCopy;
import com.google.common.collect.ImmutableMap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.nikdo53.neobackports.io.components.DataDefault;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * neobackports_component_defaults_memo (NeoBackports 1.4.3 / 1.4.2, both sides): MixinExtras @WrapOperation on the
 * ImmutableMap.copyOf call in DataDefault.getDefaults (the kept copy while nothing was written, DefaultsCopy), and
 * @Inject at the head and return of the private overrideDefault(Item, DataDefault), the only writer, to bump the generation.
 */
@Mixin(value = DataDefault.class, remap = false)
public abstract class DataDefaultMemoMixin {
    @WrapOperation(method = "getDefaults", at = @At(value = "INVOKE",
            target = "Lcom/google/common/collect/ImmutableMap;copyOf(Ljava/util/Map;)Lcom/google/common/collect/ImmutableMap;"))
    private static ImmutableMap<?, ?> bons$nbDefaults(Map<?, ?> registered, Operation<ImmutableMap<?, ?>> original) {
        return DefaultsCopy.copyOf(registered, original);
    }

    @Inject(method = "overrideDefault(Lnet/minecraft/world/item/Item;Lnet/nikdo53/neobackports/io/components/DataDefault;)V",
            at = {@At("HEAD"), @At("RETURN")})
    private static void bons$nbWritten(Item item, DataDefault<?> dataDefault, CallbackInfo ci) {
        DefaultsCopy.bump();
    }
}
