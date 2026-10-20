package bons.furious.mixin.stale_player_maps;

import bons.furious.patch.stale_player_maps.MoreArtifactsMaps;
import java.util.Map;
import net.gobies.moreartifacts.item.artifacts.NecroplasmAmuletItem;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * stale_player_map_purge_moreartifacts (More Artifacts 1.5.5, All Rights Reserved: no code of it is carried). Fix. The
 * Necroplasm Amulet's static initializer hands its cooldown map (filled only from its server-side LivingHurtEvent handler)
 * to our shared layer (see {@link MoreArtifactsMaps}).
 */
@Mixin(value = NecroplasmAmuletItem.class, remap = false)
public abstract class MoreArtifactsNecroplasmMixin {
    @Shadow
    @Final
    private static Map<Player, Long> cooldownMap;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void bons$trackPlayerMap(CallbackInfo ci) {
        MoreArtifactsMaps.trackServer("NecroplasmAmuletItem.cooldownMap", cooldownMap);
    }
}
