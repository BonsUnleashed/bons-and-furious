package bons.furious.patch.iceandfire_dens_c49;

import com.github.alexthe666.iceandfire.entity.EntityDragonBase;
import com.github.alexthe666.iceandfire.entity.util.HomePosition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * iceandfire_structure_dens (since 1.0.49): makes a dragon cave's dragon. {@link #REAL} restates Ice and Fire's set-up
 * (WorldGenDragonCave.createDragon; LGPL-3.0) without its random draws, which the caller makes in Ice and Fire's order and
 * passes in. The offline harness installs a recording maker.
 */
@FunctionalInterface
public interface DragonMaker {
    Entity make(DenKind kind, ServerLevel level, boolean male, int age, int variant, double x, double y, double z, float yaw, BlockPos home);

    DragonMaker REAL = (kind, level, male, age, variant, x, y, z, yaw, home) -> {
        EntityDragonBase dragon = (EntityDragonBase) kind.dragonType().m_20615_(level);
        dragon.setGender(male);
        dragon.growDragon(age);
        dragon.setAgingDisabled(true);
        dragon.m_21153_(dragon.m_21233_());
        dragon.setVariant(variant);
        dragon.m_19890_(x, y, z, yaw, 0.0f);
        dragon.m_21837_(true);
        dragon.homePos = new HomePosition(home, level);
        dragon.setHunger(50);
        return dragon;
    };
}
