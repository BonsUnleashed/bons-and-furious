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
 * cataclysm_helm_scan (Monstrous_Helm; L_Ender's Cataclysm, CC-BY-NC-ND-4.0: no Cataclysm code is carried; 1.21.1 tested
 * build: L_Ender's Cataclysm 1.21.1-3.33; both sides, acts where the helm's inventory tick runs).
 *
 * inventoryTick(stack, level, entity, slot, selected): only for a player wearing the helm: berserk =
 * player.getMaxHealth() * 1.0F / 2.0F >= player.getHealth(); list = level.getEntities(player, box.inflate(4)); if (berserk
 * && !player.getCooldowns().isOnCooldown(this)) { hit list }. The list is used only inside that if. When the same test
 * (pure reads, evaluated here the same way; nothing runs between the query and Cataclysm's own test) is false the query
 * returns an empty list instead of collecting the entities.
 *
 * Ported to 1.21.1: NeoForge 21 has no onArmorTick; Cataclysm 3.33 moved the same body into Item.inventoryTick behind
 * "the entity is a Player whose head slot holds the Monstrous Helm" (Inventory.tick calls it for every stack, worn armor
 * included). Same query (Level.getEntities(Entity, AABB)), same berserk expression, ItemCooldowns.isOnCooldown(Item) as
 * in 1.20.1; the wrap now selects inventoryTick.
 */
@Mixin(value = Monstrous_Helm.class, remap = false)
public abstract class MonstrousHelmScanMixin {
    @WrapOperation(method = "inventoryTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private List<Entity> bons$scanOnlyWhenTriggered(Level level, Entity wearer, AABB box, Operation<List<Entity>> original) {
        if (CataclysmWorldScans.helmScan && wearer instanceof Player player) {
            boolean berserk = player.getMaxHealth() * 1.0F / 2.0F >= player.getHealth();
            if (!(berserk && !player.getCooldowns().isOnCooldown((Item) (Object) this))) {
                CataclysmWorldScans.helmActs();
                return List.of();
            }
        }
        return original.call(level, wearer, box);
    }
}
