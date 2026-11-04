package bons.furious.mixin.gtbcsspelllib_coo49;

import bons.furious.patch.gtbcsspelllib_coo49.SpellLibScans;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * gtbcs_spell_lib_shake_scan_presence_gate (GTBC's SpellLib 2.2.0, All Rights Reserved; client): MixinExtras
 * @WrapOperation on the four Level.getEntitiesOfClass (m_45976_) calls of ForgeClientEvents that look for the library's
 * screen effect entities around the local player every frame: two in onCameraSetup (GSLScreenShakeEntity,
 * GSLFollowingScreenShakeEntity), one in processScreenFlashEffects (GSLScreenFlashEntity), one in
 * processPowerInversionEffects (GSLPowerInversionEntity), each over the player's box grown by 50 blocks. While
 * vanilla_entity_class_count_layer says the client level holds no instance of the searched class, the search is marked
 * EMPTY so that vanilla_entity_walk_skip leaves out its empty walk over the entity sections; the call and the listener's
 * loop over the (same, empty) list run unchanged. See SpellLibScans. Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "com.gametechbc.spelllib.events.ForgeClientEvents", remap = false)
public abstract class SpellLibShakeScanMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "onCameraSetup(Lnet/minecraftforge/client/event/ViewportEvent$ComputeCameraAngles;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 2, allow = 2)
    private static List bons$gtbcsspelllibCoo49ShakeScan(Level level, Class<?> searched, AABB box, Operation<List> original) {
        return SpellLibScans.scan(level, searched, box, original);
    }

    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "processScreenFlashEffects(Lnet/minecraft/world/entity/player/Player;FLnet/minecraft/client/gui/GuiGraphics;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$gtbcsspelllibCoo49FlashScan(Level level, Class<?> searched, AABB box, Operation<List> original) {
        return SpellLibScans.scan(level, searched, box, original);
    }

    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "processPowerInversionEffects(Lnet/minecraft/world/entity/player/Player;FLnet/minecraft/client/gui/GuiGraphics;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$gtbcsspelllibCoo49InversionScan(Level level, Class<?> searched, AABB box, Operation<List> original) {
        return SpellLibScans.scan(level, searched, box, original);
    }
}
