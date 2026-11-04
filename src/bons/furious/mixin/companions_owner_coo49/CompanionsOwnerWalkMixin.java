package bons.furious.mixin.companions_owner_coo49;

import bons.furious.patch.companions_owner_coo49.OwnerWalk;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.xylonity.companions.common.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

/**
 * companions_owner_walk (Companions 1.3.5 / 1.3.6, both sides): MixinExtras @WrapMethod on Util.computePlayer(Entity) and
 * Util.areEntitiesLinked: the same owner walks with the same calls in the same order, the seen UUIDs in small inline sets
 * instead of new HashSets (see OwnerWalk); with the runtime flag off Companions' own methods run.
 */
@Mixin(value = Util.class, remap = false)
public abstract class CompanionsOwnerWalkMixin {
    @WrapMethod(method = "computePlayer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/entity/player/Player;")
    private static Player bons$companionsOwnerPlayer(Entity entity, Operation<Player> original) {
        return OwnerWalk.enabled ? OwnerWalk.player(entity) : original.call(entity);
    }

    @WrapMethod(method = "areEntitiesLinked(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;)Z")
    private static boolean bons$companionsOwnerLinked(Entity a, Entity b, Operation<Boolean> original) {
        return OwnerWalk.enabled ? OwnerWalk.linked(a, b) : original.call(a, b);
    }
}
