package bons.furious.mixin.undergarden_coo49;

import bons.furious.patch.undergarden_coo49.UndergardenScans;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import quek.undergarden.entity.cavern.Muncher;

/**
 * undergarden_muncher_mob_griefing (The Undergarden 0.8.14, gameplay fix, off by default; server): the isAggressive()
 * result that opens Muncher.aiStep's block eating (m_8107_: aggressive && (horizontalCollision || verticalCollision &&
 * mobGriefingEvent)). For an aggressive muncher against a wall the mob griefing event now decides (one post; the original
 * then reads the wall flag, true, and eats only when the event allowed it); every other case keeps the original value, so
 * the floor and ceiling case posts the event exactly as before. See UndergardenScans.muncherAggressive.
 */
@Mixin(value = Muncher.class, remap = false)
public abstract class MuncherGriefingMixin {
    @ModifyExpressionValue(method = "m_8107_()V", at = @At(value = "INVOKE", target = "Lquek/undergarden/entity/cavern/Muncher;m_5912_()Z"),
            require = 1, allow = 1)
    private boolean bons$undergardenCoo49MuncherGriefing(boolean aggressive) {
        return UndergardenScans.muncherAggressive(aggressive, (Entity) (Object) this);
    }
}
