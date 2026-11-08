package bons.furious.mixin.cosmeticarmor_coo49;

import bons.furious.patch.cosmeticarmor_coo49.RestoreQueues;
import com.google.common.cache.LoadingCache;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cosmeticarmor_restore_queue_field (Cosmetic Armor Reworked 1.21.1-v1, client), part 2 of 2: the restore-queue cache the
 * PlayerRenderHandler enum constructor builds (CacheBuilder.newBuilder().weakKeys().build(loader), the field initializer of
 * INSTANCE) is handed to RestoreQueues.wrap, so all seven render handlers' getUnchecked(player) calls (NeoForge
 * RenderPlayerEvent.Pre/Post, RenderHandEvent, RenderArmEvent) read the player's own queue field. Our own logic only
 * (Minecraft Mod Public License target; none of its code is carried).
 */
@Mixin(targets = "lain.mods.cos.impl.client.PlayerRenderHandler", remap = false)
public abstract class RenderHandlerCacheMixin {
    @ModifyExpressionValue(method = "<init>(Ljava/lang/String;I)V", at = @At(value = "INVOKE",
            target = "Lcom/google/common/cache/CacheBuilder;build(Lcom/google/common/cache/CacheLoader;)Lcom/google/common/cache/LoadingCache;"),
            require = 1, allow = 1)
    @SuppressWarnings("rawtypes")
    private LoadingCache bons$cosmeticarmor_coo49Cache(LoadingCache original) {
        return RestoreQueues.wrap(original);
    }
}
