package bons.furious.mixin.boss_music_c49;

import bons.furious.patch.boss_music_c49.BossMusicStops;
import com.bobmowzie.mowziesmobs.server.entity.MowzieEntity;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * mowziesmobs_boss_music_stop_once (trade-off, off by default; Mowzie's Mobs 1.7.3, 1.8.2 the same calls; no code carried;
 * server). MowzieEntity.tick broadcasts entity event 67 (play; the first Level.broadcastEntityEvent) or 68 (stop; the
 * second) every tick. A play clears this boss's "stop sent" mark; a stop is broadcast only while the mark is clear and then
 * sets it. See BossMusicStops.
 */
@Mixin(value = MowzieEntity.class, remap = false)
public abstract class MowzieBossMusicStopOnceMixin {
    @Unique
    private boolean bons$stopSent;

    @WrapWithCondition(method = "m_8119_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_7605_(Lnet/minecraft/world/entity/Entity;B)V", ordinal = 0), require = 1, allow = 1)
    private boolean bons$play(Level level, Entity entity, byte event) {
        this.bons$stopSent = false;
        return true;
    }

    @WrapWithCondition(method = "m_8119_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_7605_(Lnet/minecraft/world/entity/Entity;B)V", ordinal = 1), require = 1, allow = 1)
    private boolean bons$stop(Level level, Entity entity, byte event) {
        if (!BossMusicStops.sendStop(BossMusicStops.mowziesmobs, this.bons$stopSent)) return false;
        this.bons$stopSent = true;
        return true;
    }
}
