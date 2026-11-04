package bons.furious.mixin.fallentraits_coo49;

import bons.furious.patch.fallentraits_coo49.FtDevourers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * fallentraits_devourer_index (Fallen Traits & Relics 1.0.0; server side; trade-off, off by default): MixinExtras
 * @WrapOperation on the Level.getEntitiesOfClass (m_6443_) search of FTEvents.onDevourerHeal: it runs as shipped when a
 * mob of the Devourer index is in the level and its box meets the search box; otherwise an empty list is answered and the
 * heal goes through, as when the search finds no Devourer (FtDevourers). What differs: a Devourer that has not ticked
 * since it got the trait or was loaded takes no heals until its first tick. Our own logic only.
 */
@Mixin(targets = "net.kayn.fallen_traits.events.FTEvents", remap = false)
public abstract class FtDevourerHealGateMixin {
    @SuppressWarnings({"rawtypes"})
    @WrapOperation(method = "onDevourerHeal(Lnet/minecraftforge/event/entity/living/LivingHealEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$fallentraitsCoo49DevourerSearch(Level level, Class<?> type, AABB box, Predicate pred, Operation<List> original) {
        return FtDevourers.search(level, type, box, pred, original);
    }
}
