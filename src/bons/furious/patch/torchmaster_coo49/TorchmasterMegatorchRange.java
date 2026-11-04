package bons.furious.patch.torchmaster_coo49;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.xalcon.torchmaster.Torchmaster;
import net.xalcon.torchmaster.TorchmasterConfig;
import net.xalcon.torchmaster.common.logic.DistanceLogics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49 switch torchmaster_megatorch_range_first (Torchmaster 20.1.9, server; kind change, off by default:
 * every answer is Torchmaster's, far torches are answered several times faster, but a torch in range asked about an entity
 * type that is not on its block list now pays the distance test it used to skip; helper of TorchmasterMegatorchRangeMixin).
 * No Torchmaster code is carried.
 *
 * What Torchmaster does. The light registry asks every light in the dimension whether it blocks a spawn;
 * MegatorchEntityBlockingLight.shouldBlockEntity(entity, pos) answers
 * MegaTorchFilterRegistry.containsEntity(EntityType.getKey(entity.getType())) && DistanceLogics.Cubic.isPositionInRange(pos,
 * this.pos, megaTorchRadius): a reverse registry look-up and a set probe per torch before the distance test that rules out
 * almost every torch.
 *
 * What the switch does. The same two tests in the other order: the distance test (DistanceLogics.Cubic with the radius read
 * from the same config value), then, only in range, the block-list question through Torchmaster's own registry. Both tests
 * are pure (arithmetic; a registry look-up and a set probe), so the order cannot change the answer, and each is asked at
 * most once (an in-range spawn costs what it cost before). Runtime flag bons_and_furious.torchmasterMegatorchRangeFirst.
 */
public final class TorchmasterMegatorchRange {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.torchmasterMegatorchRangeFirst", "true"));
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private TorchmasterMegatorchRange() {
    }

    /** shouldBlockEntity's answer for the mega torch at {@code torch}: the distance test first, then the block list. */
    public static boolean shouldBlock(Entity entity, BlockPos at, BlockPos torch) {
        if (!DistanceLogics.Cubic.isPositionInRange(at.m_123341_(), at.m_123342_(), at.m_123343_(), torch,
                (Integer) TorchmasterConfig.GENERAL.megaTorchRadius.get())) {
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: torchmaster_megatorch_range_first applies (mega torches test the distance before the entity block list)");
            }
            return false;
        }
        return Torchmaster.MegaTorchFilterRegistry.containsEntity(EntityType.m_20613_(entity.m_6095_()));
    }
}
