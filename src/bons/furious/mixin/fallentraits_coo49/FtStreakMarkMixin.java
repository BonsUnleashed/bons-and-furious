package bons.furious.mixin.fallentraits_coo49;

import bons.furious.patch.fallentraits_coo49.FtStreaks;
import net.minecraft.world.entity.Entity;
import net.rtxyd.fallen.lib.util.IObjectCaky;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * fallentraits_unused_streak_reset_skip (Fallen Traits & Relics 1.0.0; server side): @ModifyArg on the entity argument of
 * the EntityCakyHandler.resolveWith call in RageGlove.getStack and HandOfCreation.getStack (the relics' only reads of their
 * hit streak): marks the entity for the streak key before Fallen Lib answers (FtStreaks.used) and passes it on unchanged.
 * Our own logic only.
 */
@Mixin(targets = {"net.kayn.fallen_traits.content.item.curio.RageGlove", "net.kayn.fallen_traits.content.item.curio.HandOfCreation"}, remap = false)
public abstract class FtStreakMarkMixin {
    @SuppressWarnings("rawtypes")
    @ModifyArg(method = "getStack",
            at = @At(value = "INVOKE", target = "Lnet/rtxyd/fallen/lib/runtime/forgemod/util/EntityCakyHandler;resolveWith(Lnet/minecraft/world/entity/Entity;Ljava/lang/String;Lnet/rtxyd/fallen/lib/util/IObjectCaky$Type;Lnet/rtxyd/fallen/lib/util/IObjectCaky$CakyLoader;Lnet/rtxyd/fallen/lib/util/IObjectCaky$CakyReviewer;)Ljava/lang/Object;"),
            index = 0, require = 1, allow = 1)
    private Entity bons$fallentraitsCoo49StreakMark(Entity entity, String key, IObjectCaky.Type type, IObjectCaky.CakyLoader loader, IObjectCaky.CakyReviewer reviewer) {
        FtStreaks.used(entity, key);
        return entity;
    }
}
