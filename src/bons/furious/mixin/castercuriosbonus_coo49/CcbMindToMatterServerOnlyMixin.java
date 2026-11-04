package bons.furious.mixin.castercuriosbonus_coo49;

import bons.furious.patch.castercuriosbonus_coo49.CcbRefresh;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import top.theillusivec4.curios.api.SlotContext;

/**
 * caster_curios_bonus_mana_to_health_server_only (Caster Curios Bonus 1.20.1-1.1.1, GPLv3; client; gameplay fix, off by
 * default): MixinExtras @WrapOperation on the SlotContext.entity() read in MindToMatter.curioTick (after Iron's
 * super.curioTick, which still runs): on a client level it answers null, so the necklace's "instanceof Player" test ends
 * the tick there (no counter, no client-side health modifier); on a server level the wearer as shipped. See CcbRefresh.
 * Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "com.rinko1231.ccb.item.necklace.MindToMatter", remap = false)
public abstract class CcbMindToMatterServerOnlyMixin {
    @WrapOperation(method = "curioTick(Ltop/theillusivec4/curios/api/SlotContext;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE", target = "Ltop/theillusivec4/curios/api/SlotContext;entity()Lnet/minecraft/world/entity/LivingEntity;"),
            require = 1, allow = 1)
    private LivingEntity bons$castercuriosbonusCoo49Wearer(SlotContext slotContext, Operation<LivingEntity> original) {
        return CcbRefresh.wearer(original.call(slotContext));
    }
}
