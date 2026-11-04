package bons.furious.mixin.fallentraits_coo49;

import bons.furious.patch.fallentraits_coo49.FtStreaks;
import net.minecraft.world.entity.Entity;
import net.rtxyd.fallen.lib.util.IObjectCaky;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * fallentraits_unused_streak_reset_skip (Fallen Traits & Relics 1.0.0; server side): @Redirect of the two
 * EntityCakyHandler.resolveWith calls of FTEvents.onHurt (the Rage Glove and Hand of Creation streak resets, whose answers
 * are dropped): the shipped reset for an entity whose streak a relic asked for, otherwise left out (the first time with one
 * marker entry that makes the entity's Fallen Lib cache as the reset would): FtStreaks.reset. Our own logic only.
 */
@Mixin(targets = "net.kayn.fallen_traits.events.FTEvents", remap = false)
public abstract class FtStreakResetMixin {
    @SuppressWarnings("rawtypes")
    @Redirect(method = "onHurt(Lnet/minecraftforge/event/entity/living/LivingHurtEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/rtxyd/fallen/lib/runtime/forgemod/util/EntityCakyHandler;resolveWith(Lnet/minecraft/world/entity/Entity;Ljava/lang/String;Lnet/rtxyd/fallen/lib/util/IObjectCaky$Type;Lnet/rtxyd/fallen/lib/util/IObjectCaky$CakyLoader;Lnet/rtxyd/fallen/lib/util/IObjectCaky$CakyReviewer;)Ljava/lang/Object;"),
            require = 2, allow = 2)
    private static Object bons$fallentraitsCoo49StreakReset(Entity entity, String key, IObjectCaky.Type type, IObjectCaky.CakyLoader loader, IObjectCaky.CakyReviewer reviewer) {
        return FtStreaks.reset(entity, key, type, loader, reviewer);
    }
}
