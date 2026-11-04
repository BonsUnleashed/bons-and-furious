package bons.furious.mixin.moreartifacts_coo49;

import bons.furious.patch.moreartifacts_coo49.MaEquipState;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Iterator;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * moreartifacts_equip_state_single_pass (More Artifacts 1.5.5, All Rights Reserved; both sides), on
 * DamageCalculator.getCurrentEquipState: MixinExtras @ModifyExpressionValue on the first List.iterator() call (the artifact
 * list's loop), with the method's Player argument (@Local argsOnly): the loop gets an iterator over only the worn
 * artifacts, in the list's order, when the player's curio slots can be read once (see MaEquipState); otherwise the list's
 * own iterator, untouched. No HEAD inject, no per-thread hand-off (2026-11-02 redesign for the true-stock speed gate: the
 * stand-aside path pays one check). The method's loops, its calls and its map are its own. Our own logic only; nothing of
 * the mod is carried.
 */
@Mixin(targets = "net.gobies.moreartifacts.util.DamageCalculator", remap = false)
public abstract class MaEquipStateMixin {
    @SuppressWarnings("rawtypes")
    @ModifyExpressionValue(method = "getCurrentEquipState(Lnet/minecraft/world/entity/player/Player;)Ljava/util/Map;",
            at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;", ordinal = 0),
            require = 1, allow = 1, remap = false)
    private static Iterator bons$moreartifactsCoo49EquipItems(Iterator artifacts, @Local(argsOnly = true) Player player) {
        return MaEquipState.items(artifacts, player);
    }
}
