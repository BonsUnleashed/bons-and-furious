package bons.furious.mixin.castercuriosbonus_coo49;

import bons.furious.patch.castercuriosbonus_coo49.CcbRefresh;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rinko1231.ccb.item.necklace.MindToMatter;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import top.theillusivec4.curios.api.SlotContext;

/**
 * caster_curios_bonus_per_wearer_refresh (Caster Curios Bonus, GPLv3; 1.21.1 tested build: Caster Curios Bonus
 * 1.21.1-1.1.1; both sides; gameplay fix, off by default): in MindToMatter.curioTick, MixinExtras @WrapOperation on both
 * writes of the item's tickCounterHTM field (the increment and the reset: left out while the switch is on) and on the read
 * the "tickCounterHTM &gt;= 10" test makes (answers "due" or "not due" from the wearer's own tick counter,
 * CcbRefresh.counterFor). The refresh body runs as shipped. Our own logic only; nothing of the mod is carried.
 *
 * Ported to 1.21.1: the 1.21.1 build's curioTick has the same counter code; only its refresh body uses 1.21's
 * ResourceLocation attribute-modifier ids.
 */
@Mixin(targets = "com.rinko1231.ccb.item.necklace.MindToMatter", remap = false)
public abstract class CcbMindToMatterPerWearerMixin {
    @WrapOperation(method = "curioTick(Ltop/theillusivec4/curios/api/SlotContext;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "FIELD", target = "Lcom/rinko1231/ccb/item/necklace/MindToMatter;tickCounterHTM:I", opcode = Opcodes.PUTFIELD),
            require = 2, allow = 2)
    private void bons$castercuriosbonusCoo49NecklaceCounterWrite(MindToMatter item, int value, Operation<Void> original) {
        if (CcbRefresh.perWearer) return;
        original.call(item, value);
    }

    @WrapOperation(method = "curioTick(Ltop/theillusivec4/curios/api/SlotContext;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "FIELD", target = "Lcom/rinko1231/ccb/item/necklace/MindToMatter;tickCounterHTM:I", opcode = Opcodes.GETFIELD, ordinal = 1),
            require = 1, allow = 1)
    private int bons$castercuriosbonusCoo49NecklaceCounterTest(MindToMatter item, Operation<Integer> original, SlotContext slotContext, ItemStack stack) {
        if (CcbRefresh.perWearer) return CcbRefresh.counterFor(slotContext, CcbRefresh.MIND_TO_MATTER_INTERVAL);
        return original.call(item);
    }
}
