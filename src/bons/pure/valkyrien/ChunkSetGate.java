package bons.pure.valkyrien;

import agentcraft.consolidated.bons_valkyrien_fixes.org.valkyrienskies.mod.common.util.AcVsSweep6;
import agentcraft.consolidated.bons_valkyrien_fixes.org.valkyrienskies.mod.common.util.AcVsSweep7;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Keeps the switches valkyrien_entity_ship_collision and valkyrien_chunk_set_version independent. The sweep-7 collision
 * loop reads the counter fields that the ShipActiveChunksSet patch adds; when that patch is switched off (or skipped for
 * an unsupported Valkyrien Skies version) the fields do not exist and the exact sweep-6 loop runs instead. Both loops
 * return what Valkyrien Skies 2.4.11 returns.
 */
public final class ChunkSetGate {
    private ChunkSetGate() {}

    private static final boolean COUNTER = counterPresent();

    private static boolean counterPresent() {
        try {
            Class<?> set = Class.forName("org.valkyrienskies.core.impl.chunk_tracking.ShipActiveChunksSet", false, ChunkSetGate.class.getClassLoader());
            return set.getField("acVsMods").getType() == long.class
                && set.getField("acVsExtents").getType() == Object.class && set.getField("acVsWorld").getType() == Object.class;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean counterPatched() {
        return COUNTER;
    }

    public static boolean collidingWithUnloadedShips(Entity entity, Level level) {
        return COUNTER ? AcVsSweep7.collidingWithUnloadedShips(entity, level) : AcVsSweep6.collidingWithUnloadedShips(entity, level);
    }
}
