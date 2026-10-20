package bons.furious.mixin.stale_player_maps;

import bons.furious.patch.stale_player_maps.UniqueDungeonsMaps;
import com.becks.uniquedungeons.common.stackCooldowns.StackCooldownMap;
import com.becks.uniquedungeons.common.stackCooldowns.StackCooldowns;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Map;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * stale_player_map_purge_uniquedungeons (Unique Dungeons 0.70.3.2, MIT; both sides). Fix. The static initializer hands the
 * player map to our shared layer; getCooldowns, the map's only accessor, runs whole under one lock (see
 * {@link UniqueDungeonsMaps}). No Unique Dungeons code is carried.
 */
@Mixin(value = StackCooldownMap.class, remap = false)
public abstract class UniqueDungeonsCooldownMapMixin {
    @Shadow
    @Final
    private static Map<Player, StackCooldowns> map;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void bons$trackPlayerMap(CallbackInfo ci) {
        UniqueDungeonsMaps.track(map);
    }

    @WrapMethod(method = "getCooldowns")
    private static StackCooldowns bons$lockedCooldowns(Player player, Operation<StackCooldowns> original) {
        return UniqueDungeonsMaps.getCooldowns(player, original);
    }
}
