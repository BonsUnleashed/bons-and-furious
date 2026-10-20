package bons.furious.mixin.terramity_look;

import bons.furious.patch.terramity_look.TerramityClientScans;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * terramity_client_scan_skip (Terramity 0.9.8; client side; since 1.0.45): the 64-step look scan of the Conductite Scouter
 * helmet and the Void Glasses (three level.clip calls and an entity query per step) only has effects on the server; on a
 * client level its loop bound reads 0 (TerramityClientScans.lookSteps), the NBT timer writes around it stay. The bound is
 * the method's only int constant 64 (the guard fingerprints both methods). Our own logic only.
 */
@Mixin(targets = {"net.mcreator.terramity.procedures.ConductiteScouterHelmetTickEventProcedure",
        "net.mcreator.terramity.procedures.VoidGlassesWhileBaubleIsEquippedTickProcedure"}, remap = false)
public abstract class TerramityLookScanMixin {
    @ModifyExpressionValue(method = "execute(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "CONSTANT", args = "intValue=64"), remap = false)
    private static int bons$clientSteps(int steps, @Local(argsOnly = true) LevelAccessor world, @Local(argsOnly = true) Entity entity) {
        return TerramityClientScans.lookSteps(steps, world, entity);
    }
}
