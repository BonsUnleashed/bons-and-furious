package bons.furious.mixin.powah_coo49;

import bons.furious.patch.powah_coo49.PowahItemWrites;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import owmii.powah.lib.logistics.energy.Energy;

/**
 * powah_item_energy_same_value_write_skip (Powah 6.2.10; kind opt, on by default; no Powah code carried): Energy$Item's
 * private write() (stack.set(PowahComponents.ENERGY_STORED, getStored())) returns at its head when that set could not
 * change the stack: the stack's own energy component already holds that value and the item's default components have
 * none. Every other write runs as Powah's. See PowahItemWrites.
 */
@Mixin(value = Energy.Item.class, remap = false)
public abstract class PowahItemEnergyWriteMixin {
    @Shadow
    @Final
    private ItemStack stack;

    @Inject(method = "write()V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void bons$powahItemEnergySameValueWrite(CallbackInfo ci) {
        if (PowahItemWrites.unchanged((Energy.Item) (Object) this, this.stack)) ci.cancel();
    }
}
