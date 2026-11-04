package bons.furious.mixin.mysticalagriculture_coo49;

import bons.furious.patch.mysticalagriculture_coo49.AbilityCacheLean;
import com.blakebr0.mysticalagriculture.api.lib.AbilityCache;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * mysticalagriculture_ability_cache_lean (Mystical Agriculture 7.0.24, both sides; kind opt, default on; no Mystical
 * Agriculture code carried). AbilityCache.getCachedAbilities(player): the mod's own getPlayerKey, then one loop over the same
 * entries into a new HashSet instead of the stream. See AbilityCacheLean.
 */
@Mixin(value = AbilityCache.class, remap = false)
public abstract class AbilityCacheLeanMixin {
    @Shadow
    @Final
    private Map<String, Map<String, Runnable>> cache;

    @Shadow
    private static String getPlayerKey(Player player) {
        throw new AssertionError();
    }

    @Inject(method = "getCachedAbilities(Lnet/minecraft/world/entity/player/Player;)Ljava/util/Set;", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void bons$mysticalagricultureCachedAbilities(Player player, CallbackInfoReturnable<Set<String>> cir) {
        if (AbilityCacheLean.enabled) cir.setReturnValue(AbilityCacheLean.abilities(this.cache, getPlayerKey(player)));
    }
}
