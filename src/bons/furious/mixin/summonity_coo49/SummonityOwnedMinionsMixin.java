package bons.furious.mixin.summonity_coo49;

import bons.furious.patch.summonity_coo49.OwnedMinions;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

/**
 * summonity_owned_minions_tick_memo (Summonity 0.1.7.1, server side; change, off by default; All Rights Reserved: no code
 * of the mod is carried). MixinExtras @WrapMethod on MinionHelper.getOwnedMinions(ServerLevel, Player): within one server
 * tick, an owner's list is the one the first ask of that tick computed (OwnedMinions); a tick in which a summoned minion
 * joined or left any level computes every list as shipped. What differs: a minion or owner that crosses the 128-block
 * search edge, a minion that changes owner, or a minion that becomes visible by chunk loading during a tick is seen at the
 * owner's next tick.
 */
@Mixin(targets = "com.fevzi.summonity.entity.MinionHelper", remap = false)
public abstract class SummonityOwnedMinionsMixin {
    @WrapMethod(method = "getOwnedMinions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/player/Player;)Ljava/util/List;")
    private static List<?> bons$summonityCoo49OwnedMinions(ServerLevel level, Player owner, Operation<List<?>> original) {
        return OwnedMinions.get(level, owner, original);
    }
}
