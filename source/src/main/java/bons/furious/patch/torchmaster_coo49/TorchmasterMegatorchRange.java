package bons.furious.patch.torchmaster_coo49;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.xalcon.torchmaster.Torchmaster;
import net.xalcon.torchmaster.logic.IDistanceLogic;
import net.xalcon.torchmaster.platform.Services;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49 switch torchmaster_megatorch_range_first (Torchmaster 21.1.13 for NeoForge 1.21.1, MIT; server;
 * kind change, off by default: every answer is Torchmaster's, far torches are answered several times faster, but a torch
 * in range asked about an entity type that is not on its block list now pays the distance test it used to skip; helper of
 * TorchmasterMegatorchRangeMixin). No Torchmaster code is carried.
 *
 * What Torchmaster does. For every spawn check (TorchmasterEventHandler.onCheckSpawn, from MobSpawnEvent.PositionCheck)
 * the level's light manager (FilteredLightManager, saved data of the level) asks every light in the dimension whether it
 * blocks the entity type; MegatorchEntityBlockingLight.shouldBlockEntityType(type, level, pos, spawnType) answers
 * MegaTorchFilterRegistry.containsEntity(EntityType.getKey(type)) && IDistanceLogic.Cubic.isPositionInRange(pos.x, pos.y,
 * pos.z, this.pos, megaTorchRadius): a reverse registry look-up and a set probe per torch before the distance test that
 * rules out almost every torch.
 *
 * What the switch does. The same two tests in the other order: the distance test (IDistanceLogic.Cubic with the radius read
 * from the same config value, Services.PLATFORM.getConfig().getMegaTorchRadius()), then, only in range, the block-list
 * question through Torchmaster's own registry. Both tests are pure (arithmetic; a registry look-up and a set probe; the
 * radius is a cached read of Torchmaster's COMMON config), so the order cannot change the answer, and each is asked at most
 * once (an in-range spawn costs what it cost before). Runtime flag bons_and_furious.torchmasterMegatorchRangeFirst.
 */
public final class TorchmasterMegatorchRange {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.torchmasterMegatorchRangeFirst", "true"));
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private TorchmasterMegatorchRange() {
    }

    /** shouldBlockEntityType's answer for the mega torch at {@code torch}: the distance test first, then the block list. */
    public static boolean shouldBlock(EntityType<?> entityType, Vec3 at, BlockPos torch) {
        if (!IDistanceLogic.Cubic.isPositionInRange(at.x, at.y, at.z, torch, Services.PLATFORM.getConfig().getMegaTorchRadius())) {
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: torchmaster_megatorch_range_first applies (mega torches test the distance before the entity block list)");
            }
            return false;
        }
        return Torchmaster.MegaTorchFilterRegistry.containsEntity(EntityType.getKey(entityType));
    }
}
