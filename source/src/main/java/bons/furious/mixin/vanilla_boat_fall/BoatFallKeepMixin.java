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
 * vanilla_boat_fall_no_break (Minecraft 1.21.1 with NeoForge 21.1.252, server side incl. the integrated server; kind change,
 * off by default). MixinExtras @WrapOperation on the kill() call and on the two spawnAtLocation(ItemLike) calls (the boat
 * type's planks, three times; NeoForge's per-type sticks, twice) inside Boat.checkFallDamage: left out while the switch acts,
 * so the boat survives a fall onto land. causeFallDamage (the passengers' fall damage), the doEntityDrops game rule read and
 * resetFallDistance stay as they are; another mod's injection elsewhere in the method is untouched, and a mod that returns
 * from the method before these calls leaves nothing for them to change. Only our own logic is carried. See BoatFall.
 */
@Mixin(value = Boat.class, remap = false)
public abstract class BoatFallKeepMixin {
    @WrapOperation(method = "checkFallDamage(DZLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V", allow = 1,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/vehicle/Boat;kill()V"))
    private void bons$keepBoat(Boat boat, Operation<Void> original) {
        if (!BoatFall.keep()) original.call(boat);
    }

    @WrapOperation(method = "checkFallDamage(DZLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V", allow = 2,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/vehicle/Boat;spawnAtLocation(Lnet/minecraft/world/level/ItemLike;)Lnet/minecraft/world/entity/item/ItemEntity;"))
    private ItemEntity bons$noBreakDrops(Boat boat, ItemLike item, Operation<ItemEntity> original) {
        return BoatFall.enabled ? null : original.call(boat, item);
    }
}
