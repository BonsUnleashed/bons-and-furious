package bons.furious.patch.ribbits_coo49;

import java.util.List;
import net.minecraft.world.level.Level;

/** The per-goal state of ribbits_band_scan_interval, kept on each RibbitPlayMusicGoal (RibbitBandScanMixin). */
public interface BandScanHolder {
    List<?> bons$band();

    Level bons$bandLevel();

    int bons$bandUses();

    void bons$keepBand(List<?> band, Level level, int uses);
}
