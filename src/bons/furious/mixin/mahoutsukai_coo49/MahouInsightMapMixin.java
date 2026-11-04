package bons.furious.mixin.mahoutsukai_coo49;

import bons.furious.patch.mahoutsukai_coo49.MahouInsightMap;
import java.util.Map;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * mahoutsukai_insight_target_purge (Mahou Tsukai 1.20.1-v1.34.81, All Rights Reserved; server): @Inject at the RETURN of
 * InsightEyesPotion's static initializer hands its static player map to MahouInsightMap, whose logout / server-stop
 * listeners remove the entries of players that left. Nothing of the class is changed.
 */
@Mixin(targets = "stepsword.mahoutsukai.potion.InsightEyesPotion", remap = false)
public abstract class MahouInsightMapMixin {
    @Shadow
    public static Map<Player, Boolean> hadTargets;

    @Inject(method = "<clinit>", at = @At("RETURN"), require = 1, allow = 1)
    private static void bons$mahoutsukaiCoo49InsightTrack(CallbackInfo ci) {
        MahouInsightMap.track(hadTargets);
    }
}
