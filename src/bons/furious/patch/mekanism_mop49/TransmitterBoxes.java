package bons.furious.patch.mekanism_mop49;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch mekanism_transmitter_render_box_keep (Mekanism 10.4.13.69, client; since 1.0.49). No Mekanism code
 * here.
 *
 * TileEntityTransmitter.getRenderBoundingBox (every cable, pipe, tube and transporter; each has a block entity renderer,
 * so the renderer's culling asks it for every transmitter in view, every frame) builds a new BlockPos and a new AABB from
 * the block entity's position on each call. The position is final in BlockEntity, so the box never changes: the first
 * box is kept with the transmitter and handed out again. Shadow (rigs):
 * -Dbons_and_furious.mekanismTransmitterRenderBox.shadow=true builds the box anyway and compares it with the kept one.
 */
public final class TransmitterBoxes {
    /** Runtime switch; -Dbons_and_furious.mekanismTransmitterRenderBox=false also turns it off (a new box per call). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mekanismTransmitterRenderBox", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.mekanismTransmitterRenderBox.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private TransmitterBoxes() {
    }

    public static void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: mekanism_transmitter_render_box_keep: Mekanism transmitters keep their render box");
    }

    /** SHADOW: the box built now against the kept one (equal corners), counted; returns the kept box. */
    public static AABB shadow(AABB kept, AABB fresh, Object transmitter) {
        SHADOW_CHECKS.incrementAndGet();
        if (!kept.equals(fresh) && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: mekanism_transmitter_render_box_keep SHADOW: {} kept {} but builds {}", transmitter, kept, fresh);
        return kept;
    }
}
