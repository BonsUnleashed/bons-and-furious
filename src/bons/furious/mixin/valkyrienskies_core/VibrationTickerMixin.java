package bons.furious.mixin.valkyrienskies_core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.VibrationParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationInfo;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.mod.common.util.AcVsHotPaths;

/**
 * valkyrien_sculk_vibrations (Minecraft 1.20.1 VibrationSystem.Ticker, for Valkyrien Skies 2.4.11).
 *
 * A sculk sensor, shrieker, warden or allay on a ship lives in shipyard coordinates, while Valkyrien Skies stores the
 * vibrations it hears in world coordinates. Valkyrien Skies' own fix (MixinVibrationSystemTicker, cancelled while this
 * switch applies) injects into this interface, which Forge's Mixin 0.8.5 rejects, so on Forge the ticker measured the
 * distance and aimed the reloaded particle across the two coordinate systems. Here the ticker sees the receiver's
 * position source in world space (AcVsHotPaths.sculkPositionSource) and the vibration position through
 * AcVsHotPaths.sculkEventPosition, in exactly the two methods Valkyrien Skies meant to patch.
 *
 * Forge's Mixin cannot inject into an interface, so both methods are replaced whole. The bodies are written from the
 * methods' behaviour: every call, argument and branch is the vanilla one except the three marked calls. Interface
 * mixin methods must be public, so the two methods become public static (they were private static).
 */
@Mixin(targets = "net.minecraft.world.level.gameevent.vibrations.VibrationSystem$Ticker", remap = false)
public interface VibrationTickerMixin {
    /** areAdjacentChunksTicking: the 2x2 chunks around the receiver are loaded and ticking. */
    @Shadow
    static boolean m_280446_(Level level, BlockPos pos) {
        throw new AssertionError();
    }

    /**
     * tryReloadVibrationParticle: after a reload, re-sends the in-flight vibration particle from the point it has
     * reached between the vibration and its receiver.
     *
     * @author BonsUnleashed
     * @reason Aim the particle at the receiver's world position when the receiver is on a ship.
     */
    @Overwrite
    static void m_280404_(ServerLevel level, VibrationSystem.Data data, VibrationSystem.User user) {
        if (!data.m_280616_()) {                         // no particle reload pending
            return;
        }
        if (data.m_280602_() == null) {                  // nothing in flight any more: drop the request
            data.m_280671_(false);
            return;
        }
        Vec3 from = data.m_280602_().f_243906_();
        PositionSource receiver = AcVsHotPaths.sculkPositionSource(user, level);        // was user.getPositionSource()
        Vec3 to = receiver.m_142502_(level).orElse(from);
        int ticksLeft = data.m_280274_();
        int ticksTotal = user.m_280576_(data.m_280602_().f_243776_());
        double progress = 1.0 - (double) ticksLeft / (double) ticksTotal;
        double x = Mth.m_14139_(progress, from.f_82479_, to.f_82479_);
        double y = Mth.m_14139_(progress, from.f_82480_, to.f_82480_);
        double z = Mth.m_14139_(progress, from.f_82481_, to.f_82481_);
        boolean sent = level.m_8767_(new VibrationParticleOption(receiver, ticksLeft), x, y, z, 1, 0.0, 0.0, 0.0, 0.0) > 0;
        if (sent) {
            data.m_280671_(false);
        }
    }

    /**
     * receiveVibration: delivers an arrived vibration to the receiver, unless the receiver needs its neighbouring
     * chunks ticking and they are not.
     *
     * @author BonsUnleashed
     * @reason Measure the event and the receiver in the same (world) coordinates when the receiver is on a ship.
     */
    @Overwrite
    static boolean m_280174_(ServerLevel level, VibrationSystem.Data data, VibrationSystem.User user, VibrationInfo vibration) {
        BlockPos event = BlockPos.m_274446_(AcVsHotPaths.sculkEventPosition(vibration, level));   // was vibration.pos()
        BlockPos receiver = AcVsHotPaths.sculkPositionSource(user, level)                        // was user.getPositionSource()
                .m_142502_(level).map(BlockPos::m_274446_).orElse(event);
        if (user.m_280215_() && !m_280446_(level, receiver)) {
            return false;
        }
        user.m_280271_(level, event, vibration.f_243709_(), vibration.m_246794_(level).orElse(null),
                vibration.m_247126_(level).orElse(null), VibrationSystem.Listener.m_280659_(event, receiver));
        data.m_280036_(null);
        return true;
    }
}
