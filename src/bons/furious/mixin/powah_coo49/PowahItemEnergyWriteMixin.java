package bons.furious.mixin.powah_coo49;

import bons.furious.patch.powah_coo49.PowahItemWrites;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import owmii.powah.lib.logistics.energy.Energy;

/**
 * powah_item_energy_same_value_write_skip (Powah 5.0.11; kind opt, on by default; no Powah code carried): the
 * write(compound, false, false) call in Energy$Item.write(), made on the compound stack.getOrCreateTagElement answered (that
 * call stays Powah's own and still creates a fresh item's data), leaves an unchanged energy value as it is and writes a
 * changed one with the same put and a constant key. See PowahItemWrites.
 */
@Mixin(value = Energy.Item.class, remap = false)
public abstract class PowahItemEnergyWriteMixin {
    @WrapOperation(method = "write()V", at = @At(value = "INVOKE",
            target = "Lowmii/powah/lib/logistics/energy/Energy$Item;write(Lnet/minecraft/nbt/CompoundTag;ZZ)Lnet/minecraft/nbt/CompoundTag;"),
            require = 1, allow = 1)
    private CompoundTag bons$powahItemEnergySameValueWrite(Energy.Item self, CompoundTag nbt, boolean capacity, boolean transfer, Operation<CompoundTag> original) {
        return PowahItemWrites.write(self, nbt, capacity, transfer) ? nbt : original.call(self, nbt, capacity, transfer);
    }
}
