package bons.furious.mixin.ribbits_coo49;

import bons.furious.patch.ribbits_coo49.BandScan;
import bons.furious.patch.ribbits_coo49.BandScanHolder;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yungnickyoung.minecraft.ribbits.entity.goal.RibbitPlayMusicGoal;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * ribbits_band_scan_interval (Ribbits 1.21.1-NeoForge-4.1.6, server): the ribbit box search (Level.getEntitiesOfClass with
 * class and box) in RibbitPlayMusicGoal.canUse and tick answers from BandScan, which keeps the goal's last search for N
 * calls. The player search in tick (the three-argument getEntitiesOfClass) is not touched. See BandScan.
 */
@Mixin(value = RibbitPlayMusicGoal.class, remap = false)
public abstract class RibbitBandScanMixin implements BandScanHolder {
    @Unique
    private List<?> bons$band;
    @Unique
    private Level bons$bandLevel;
    @Unique
    private int bons$bandUses;

    @WrapOperation(method = {"canUse", "tick"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 2, allow = 2)
    private List<?> bons$bandSearch(Level level, Class<?> type, AABB box, Operation<List<?>> original) {
        return BandScan.neighbours(this, level, type, box, original);
    }

    @Override
    public List<?> bons$band() {
        return this.bons$band;
    }

    @Override
    public Level bons$bandLevel() {
        return this.bons$bandLevel;
    }

    @Override
    public int bons$bandUses() {
        return this.bons$bandUses;
    }

    @Override
    public void bons$keepBand(List<?> band, Level level, int uses) {
        this.bons$band = band;
        this.bons$bandLevel = level;
        this.bons$bandUses = uses;
    }
}
