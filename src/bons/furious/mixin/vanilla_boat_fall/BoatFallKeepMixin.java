package bons.furious.mixin.vanilla_boat_fall;

import bons.furious.patch.vanilla_boat_fall.BoatFall;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_boat_fall_no_break (Minecraft 1.20.1 as patched by Forge 47.4.28, server side incl. the integrated server; kind
 * change, off by default). MixinExtras @WrapOperation on the kill() (m_6074_) call and the two spawnAtLocation (m_19998_)
 * calls (planks, sticks) inside Boat.checkFallDamage (m_7840_): left out while the switch acts, so the boat survives a fall
 * onto land; causeFallDamage (the passengers' fall damage) and resetFallDistance stay. SecurityCraft's and BCLib's
 * injections and BoatBreakFix's HEAD cancel in the same method are untouched (with BoatBreakFix installed the boat never
 * reaches these calls). Only our own logic is carried. See BoatFall.
 */
@Mixin(value = Boat.class, remap = false)
public abstract class BoatFallKeepMixin {
    @WrapOperation(method = "m_7840_", allow = 1, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/vehicle/Boat;m_6074_()V"))
    private void bons$keepBoat(Boat boat, Operation<Void> original) {
        if (!BoatFall.keep()) original.call(boat);
    }

    @WrapOperation(method = "m_7840_", allow = 2, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/vehicle/Boat;m_19998_(Lnet/minecraft/world/level/ItemLike;)Lnet/minecraft/world/entity/item/ItemEntity;"))
    private ItemEntity bons$noBreakDrops(Boat boat, ItemLike item, Operation<ItemEntity> original) {
        return BoatFall.enabled ? null : original.call(boat, item);
    }
}
