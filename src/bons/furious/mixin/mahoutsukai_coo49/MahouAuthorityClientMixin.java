package bons.furious.mixin.mahoutsukai_coo49;

import bons.furious.patch.mahoutsukai_coo49.MahouTicks;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * mahoutsukai_authority_client_skip (Mahou Tsukai 1.20.1-v1.34.81, All Rights Reserved; client dist): @ModifyVariable at
 * the HEAD of BorrowedAuthorityPotion.authorityLivingUpdate: on a client level the parameter becomes null, so the handler's
 * opening null test ends it; everything the handler does is inside its server-side branch, and on the client it only reads
 * the capability, its authority flag and the motion. See MahouTicks. Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "stepsword.mahoutsukai.potion.BorrowedAuthorityPotion", remap = false)
public abstract class MahouAuthorityClientMixin {
    @ModifyVariable(method = "authorityLivingUpdate(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), argsOnly = true, require = 1, allow = 1)
    private static LivingEntity bons$mahoutsukaiCoo49AuthorityClient(LivingEntity entity) {
        return MahouTicks.authority(entity);
    }
}
