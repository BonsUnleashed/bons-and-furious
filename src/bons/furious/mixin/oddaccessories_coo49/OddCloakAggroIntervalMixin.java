package bons.furious.mixin.oddaccessories_coo49;

import bons.furious.patch.oddaccessories_coo49.OddScans;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * oddaccessories_cloak_aggro_scan_interval (Odd Accessories 1.1.6, All Rights Reserved; server side; trade-off, off by
 * default): MixinExtras @WrapOperation on the second Level.getEntitiesOfClass (m_45976_) call of
 * FiberglassCloakItem.onCurioTick, the 12-block search whose mobs get their aggro toward the wearer cleared. On the wearer's
 * scan ticks (every N ticks, the value line) it runs as shipped; on other ticks it answers an empty list, so that tick
 * clears nothing. What differs: a mob can keep targeting the cloaked wearer up to N-1 ticks longer. The 4-block eligibility
 * search (the first call) and everything else in the tick are untouched. Our own logic only.
 */
@Mixin(targets = "net.oddaccessories.item.FiberglassCloakItem", remap = false)
public abstract class OddCloakAggroIntervalMixin {
    @SuppressWarnings({"rawtypes"})
    @WrapOperation(method = "onCurioTick(Ltop/theillusivec4/curios/api/SlotContext;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;", ordinal = 1),
            require = 1, allow = 1)
    private List bons$oddaccessoriesCoo49CloakAggro(Level level, Class<?> type, AABB box, Operation<List> original, @Local(ordinal = 0) LivingEntity wearer) {
        return OddScans.cloakAggroSearch(level, type, box, original, wearer);
    }
}
