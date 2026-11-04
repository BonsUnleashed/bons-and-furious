package bons.furious.mixin.cnb_coo49;

import bons.furious.patch.cnb_coo49.CactemHealScanHolder;
import bons.furious.patch.cnb_coo49.CnbScans;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cnb_cactem_heal_scan_reuse (Creatures and Beasts: Continued 1.7.10, server; trade-off, off by default): the cactem box
 * search (Level.getEntitiesOfClass, 33 x 9 x 33) in CactemEntity$HealGoal.cactemNeedsHeal, which canUse, canContinueToUse
 * and tick each call, answers from CnbScans.healMembers: the goal's last search's list for the next N - 1 calls, filtered
 * by the current box (the search's own intersects test); health and elder state are read live by the original loop; a
 * removed cactem in the list or a level change searches again at once. What differs: a hurt cactem that walks into an
 * elder's range can be seen up to N - 1 calls later. The per-goal state lives in this mixin's @Unique fields.
 */
@Mixin(targets = "com.cgessinger.creaturesandbeasts.entities.CactemEntity$HealGoal", remap = false)
public abstract class CactemHealScanMixin implements CactemHealScanHolder {
    @Unique
    private List<?> bons$cnbCoo49HealList;
    @Unique
    private Level bons$cnbCoo49HealLevel;
    @Unique
    private int bons$cnbCoo49HealUses;

    @WrapOperation(method = "cactemNeedsHeal(Lcom/cgessinger/creaturesandbeasts/entities/CactemEntity;Lnet/minecraft/world/level/Level;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private List<?> bons$cnbCoo49HealSearch(Level level, Class<?> type, AABB box, Operation<List<?>> original) {
        return CnbScans.healMembers(this, level, type, box, original);
    }

    @Override
    public List<?> bons$cnbCoo49HealList() {
        return this.bons$cnbCoo49HealList;
    }

    @Override
    public Level bons$cnbCoo49HealLevel() {
        return this.bons$cnbCoo49HealLevel;
    }

    @Override
    public int bons$cnbCoo49HealUses() {
        return this.bons$cnbCoo49HealUses;
    }

    @Override
    public void bons$cnbCoo49KeepHeal(List<?> list, Level level, int uses) {
        this.bons$cnbCoo49HealList = list;
        this.bons$cnbCoo49HealLevel = level;
        this.bons$cnbCoo49HealUses = uses;
    }
}
