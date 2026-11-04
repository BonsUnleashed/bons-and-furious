package bons.furious.mixin.tonsofenchants_coo49;

import bons.furious.patch.tonsofenchants_coo49.ToeLean;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import java.util.function.Consumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * tonsofenchants_frostbite_server_only (Tons Of Enchants 1.0, MIT; both sides, acts on the client): MixinExtras
 * @WrapWithCondition on the PU.doForEachEntity call of Frostbite.doWhenFrostbit (its last statement, after the config
 * check): the entity scan is left out for a player on a client level, where no entity can carry the server-side
 * "Frostbitten" flag the scan looks for (ToeLean.frostbiteScan). On the server the call runs as shipped. Our own logic only.
 */
@Mixin(targets = "com.deltajay.tonsofenchants.enchantments.Enchantments.Frostbite", remap = false)
public abstract class ToeFrostbiteClientMixin {
    @WrapWithCondition(method = "doWhenFrostbit(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V",
            at = @At(value = "INVOKE", target = "Lcom/deltajay/tonsofenchants/util/PU;doForEachEntity(Lnet/minecraft/world/entity/LivingEntity;IILjava/util/function/Consumer;)V"),
            require = 1, allow = 1)
    private static boolean bons$tonsofenchantsCoo49FrostbiteScan(LivingEntity player, int radius, int yRadius, Consumer<Entity> action) {
        return ToeLean.frostbiteScan(player);
    }
}
