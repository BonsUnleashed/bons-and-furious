package bons.furious.mixin.simplyswordsoverhaul_coo49;

import bons.furious.patch.simplyswordsoverhaul_coo49.SsoMoltenEdge;
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.sweenus.simplyswords.item.custom.MoltenEdgeSwordItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * simplyswordsoverhaul_molten_edge_buff_refresh (Simply Swords Overhaul 1.0.4 with Simply Swords 1.56.0; MIT: no code of
 * the mod is carried; server side; since 1.0.49). Trade-off, off by default. A MixinSquared injection into Simply Swords
 * Overhaul's MixinMoltenEdgeSword.modifyInventoryTick handler (merged into MoltenEdgeSwordItem.inventoryTick at its head):
 * MixinExtras @WrapOperation on its three Player.addEffect calls (strength, haste, speed for 100 ticks, every tick while
 * the sword is held below the health threshold): SsoMoltenEdge leaves a call out while the player already has that buff
 * at the same level and flags with more than SsoMoltenEdge.RENEW_AT ticks left; otherwise it runs as shipped. Priority
 * 1500: applied after Simply Swords Overhaul's mixin. require = 0 and expect = 0: without that handler the injection
 * finds nothing; the switch's guard lines pin it, and the group's offline proof applies Simply Swords Overhaul's own mixin
 * and checks the merged handler calls this wrapper.
 */
@Mixin(value = MoltenEdgeSwordItem.class, priority = 1500, remap = false)
public abstract class SsoMoltenEdgeBuffMixin {
    @TargetHandler(mixin = "net.jrdemiurge.simplyswordsoverhaul.mixin.MixinMoltenEdgeSword", name = "modifyInventoryTick")
    @WrapOperation(method = "@MixinSquared:Handler",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;m_7292_(Lnet/minecraft/world/effect/MobEffectInstance;)Z"),
            require = 0, expect = 0, allow = 3)
    private boolean bons$simplyswordsoverhaulCoo49MoltenEdgeBuff(Player player, MobEffectInstance effect, Operation<Boolean> original) {
        return SsoMoltenEdge.addEffect(player, effect, original);
    }
}
