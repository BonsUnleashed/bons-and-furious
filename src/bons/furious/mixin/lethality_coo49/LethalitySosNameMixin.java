package bons.furious.mixin.lethality_coo49;

import bons.furious.patch.lethality_coo49.LethalitySos;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * lethality_sos_name_server_fallback (Lethality 0.4.1, MIT; both sides; since 1.0.49; fix): MixinExtras
 * @ModifyExpressionValue on the DistExecutor.unsafeCallWhenOn call of the colour gradient copies in BackupSOSItem and
 * UltraBackupSOSItem (addColorGradientText and addColorGradientTextWithFont with speed and spread): a null answer (a
 * dedicated server) becomes Lethality's own tick fallback instead of a NullPointerException at the unboxing. Helper
 * LethalitySos.
 */
@Mixin(targets = {"net.daphne.lethality.item.custom.curios.BackupSOSItem", "net.daphne.lethality.item.custom.curios.UltraBackupSOSItem"}, remap = false)
public abstract class LethalitySosNameMixin {
    @ModifyExpressionValue(method = {
            "addColorGradientText(Lnet/minecraft/network/chat/Component;FF[[I)Lnet/minecraft/network/chat/MutableComponent;",
            "addColorGradientTextWithFont(Lnet/minecraft/network/chat/Component;Lnet/minecraft/resources/ResourceLocation;FF[[I)Lnet/minecraft/network/chat/MutableComponent;"},
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/DistExecutor;unsafeCallWhenOn(Lnet/minecraftforge/api/distmarker/Dist;Ljava/util/function/Supplier;)Ljava/lang/Object;"),
            require = 2, allow = 2)
    private static Object bons$lethalityCoo49SosTick(Object answer) {
        return LethalitySos.tick(answer);
    }
}
