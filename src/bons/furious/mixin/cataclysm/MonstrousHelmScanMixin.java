package bons.furious.mixin.cataclysm;

import bons.furious.patch.cataclysm.CataclysmWorldScans;
import com.github.L_Ender.cataclysm.items.Monstrous_Helm;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cataclysm_helm_scan (Monstrous_Helm, L_Ender's Cataclysm 3.16, CC-BY-NC-ND-4.0: no Cataclysm code is carried; both sides,
 * acts where onArmorTick runs).
 *
 * onArmorTick(stack, level, player): berserk = player.getMaxHealth() * 1.0F / 2.0F >= player.getHealth(); list =
 * level.getEntities(player, box.inflate(4)); if (berserk && !player.getCooldowns().isOnCooldown(this)) { hit list }. The
 * list is used only inside that if. When the same test (pure reads, evaluated here the same way) is false the query
 * returns an empty list instead of collecting the entities.
 */
@Mixin(value = Monstrous_Helm.class, remap = false)
public abstract class MonstrousHelmScanMixin {
    @WrapOperation(method = "onArmorTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_45933_(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private List<Entity> bons$scanOnlyWhenTriggered(Level level, Entity wearer, AABB box, Operation<List<Entity>> original) {
        if (CataclysmWorldScans.helmScan && wearer instanceof Player player) {
            boolean berserk = player.m_21233_() * 1.0F / 2.0F >= player.m_21223_();
            if (!(berserk && !player.m_36335_().m_41519_((Item) (Object) this))) {
                CataclysmWorldScans.helmActs();
                return List.of();
            }
        }
        return original.call(level, wearer, box);
    }
}
