package bons.furious.patch.cnb_coo49;

import java.util.List;
import net.minecraft.world.level.Level;

/**
 * cnb_cactem_heal_scan_reuse (Creatures and Beasts: Continued 1.7.17, server): the per-goal state kept on each
 * CactemEntity$HealGoal by CactemHealScanMixin (the last search's list, its level, and how many calls reused it).
 */
public interface CactemHealScanHolder {
    List<?> bons$cnbCoo49HealList();

    Level bons$cnbCoo49HealLevel();

    int bons$cnbCoo49HealUses();

    void bons$cnbCoo49KeepHeal(List<?> list, Level level, int uses);
}
