package bons.furious.mixin.castercuriosbonus_coo49;

import bons.furious.patch.castercuriosbonus_coo49.CcbRefresh;
import com.rinko1231.ccb.item.charm.Eureka;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import top.theillusivec4.curios.api.SlotContext;

/**
 * caster_curios_bonus_per_wearer_refresh (Caster Curios Bonus 1.20.1-1.1.1, GPLv3; server ticks; gameplay fix, off by
 * default): in Eureka.curioTick (server only by the charm's own side check), MixinExtras @WrapOperation on both writes of
 * the item's tickCounter field (the increment and the reset: left out while the switch is on) and on the read the
 * "tickCounter &lt; 20" test makes (answers "due" or "not due" from the wearer's own tick counter, CcbRefresh.counterFor).
 * The refresh body runs as shipped. Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "com.rinko1231.ccb.item.charm.Eureka", remap = false)
public abstract class CcbEurekaPerWearerMixin {
    @WrapOperation(method = "curioTick(Ltop/theillusivec4/curios/api/SlotContext;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "FIELD", target = "Lcom/rinko1231/ccb/item/charm/Eureka;tickCounter:I", opcode = Opcodes.PUTFIELD),
            require = 2, allow = 2)
    private void bons$castercuriosbonusCoo49EurekaCounterWrite(Eureka item, int value, Operation<Void> original) {
        if (CcbRefresh.perWearer) return;
        original.call(item, value);
    }

    @WrapOperation(method = "curioTick(Ltop/theillusivec4/curios/api/SlotContext;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "FIELD", target = "Lcom/rinko1231/ccb/item/charm/Eureka;tickCounter:I", opcode = Opcodes.GETFIELD, ordinal = 1),
            require = 1, allow = 1)
    private int bons$castercuriosbonusCoo49EurekaCounterTest(Eureka item, Operation<Integer> original, SlotContext slotContext, ItemStack stack) {
        if (CcbRefresh.perWearer) return CcbRefresh.counterFor(slotContext, CcbRefresh.EUREKA_INTERVAL);
        return original.call(item);
    }
}
