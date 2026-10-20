package bons.furious.mixin.stale_player_maps;

import bons.furious.patch.stale_player_maps.MoreArtifactsMaps;
import java.util.Map;
import net.gobies.moreartifacts.event.DamageEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * stale_player_map_purge_moreartifacts (More Artifacts 1.5.5, All Rights Reserved: no code of it is carried; both sides).
 * Fix. DamageEvents' static initializer hands its five player maps to our shared layer (see {@link MoreArtifactsMaps});
 * BLEEDING_ENTITIES is keyed by UUID and the mod removes its entries itself, so it is left alone.
 */
@Mixin(value = DamageEvents.class, remap = false)
public abstract class MoreArtifactsDamageEventsMixin {
    @Shadow
    @Final
    private static Map<Player, Double> generalDamageReductionMap;

    @Shadow
    @Final
    private static Map<Player, Double> fireDamageReductionMap;

    @Shadow
    @Final
    private static Map<Player, Double> generalDamageIncreaseMap;

    @Shadow
    @Final
    private static Map<Player, Map<Item, Boolean>> equippedArtifactsMap;

    @Shadow
    @Final
    private static Map<Player, Long> lastHealTimeMap;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void bons$trackPlayerMaps(CallbackInfo ci) {
        MoreArtifactsMaps.trackBoth("DamageEvents.generalDamageReductionMap", generalDamageReductionMap);
        MoreArtifactsMaps.trackBoth("DamageEvents.fireDamageReductionMap", fireDamageReductionMap);
        MoreArtifactsMaps.trackBoth("DamageEvents.generalDamageIncreaseMap", generalDamageIncreaseMap);
        MoreArtifactsMaps.trackBoth("DamageEvents.equippedArtifactsMap", equippedArtifactsMap);
        MoreArtifactsMaps.trackServer("DamageEvents.lastHealTimeMap", lastHealTimeMap);
    }
}
