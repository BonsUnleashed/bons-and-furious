package bons.furious.mixin.uniqueaccessories_coo49;

import bons.furious.patch.uniqueaccessories_coo49.UaRepath;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * uniqueaccessories_rose_repath_interval (Unique Accessories 1.3.3, All Rights Reserved; server side; trade-off, off by
 * default): MixinExtras @WrapOperation on the PathNavigation.moveTo(Entity, double) call of RoseOfTemptationEvents.onPlayerTick
 * (see UaRepath). Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "net.genzyuro.uniqueaccessories.system.RoseOfTemptationEvents", remap = false)
public abstract class UaRoseRepathMixin {
    @WrapOperation(method = "onPlayerTick(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;m_5624_(Lnet/minecraft/world/entity/Entity;D)Z"),
            require = 1, allow = 1, remap = false)
    private static boolean bons$uniqueaccessoriesCoo49Repath(PathNavigation navigation, Entity target, double speed, Operation<Boolean> original) {
        return UaRepath.moveTo(navigation, target, speed, original);
    }
}
