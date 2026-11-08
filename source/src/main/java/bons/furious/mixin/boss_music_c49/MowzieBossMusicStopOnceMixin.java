package bons.furious.mixin.boss_music_c49;

import bons.furious.patch.boss_music_c49.MowzieBossMusicStops;
import com.bobmowzie.mowziesmobs.server.entity.MowzieEntity;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * mowziesmobs_boss_music_stop_once (trade-off, off by default; Mowzie's Mobs, custom licence: no code carried; 1.21.1 tested
 * build: Mowzie's Mobs 1.21.1-1.8.2; server). MowzieEntity.tick broadcasts entity event 67 (play; the first
 * Level.broadcastEntityEvent) or 68 (stop; the second) every tick while the boss has boss music. A play clears this boss's
 * "stop sent" mark; a stop is broadcast only while the mark is clear and then sets it. See MowzieBossMusicStops.
 *
 * Ported to 1.21.1: tick has the same two broadcasts (bytecode: ordinal 0 pushes 67, ordinal 1 pushes 68), and the client's
 * handleEntityEvent still sends 68 to BossMusicPlayer.stopBossMusic, which acts only while the current music is bound to
 * that boss object.
 */
@Mixin(value = MowzieEntity.class, remap = false)
public abstract class MowzieBossMusicStopOnceMixin {
    @Unique
    private boolean bons$mowzieStopSent;

    @WrapWithCondition(method = "tick()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;broadcastEntityEvent(Lnet/minecraft/world/entity/Entity;B)V", ordinal = 0), require = 1, allow = 1)
    private boolean bons$mowziePlay(Level level, Entity entity, byte event) {
        this.bons$mowzieStopSent = false;
        return true;
    }

    @WrapWithCondition(method = "tick()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;broadcastEntityEvent(Lnet/minecraft/world/entity/Entity;B)V", ordinal = 1), require = 1, allow = 1)
    private boolean bons$mowzieStop(Level level, Entity entity, byte event) {
        if (!MowzieBossMusicStops.sendStop(this.bons$mowzieStopSent)) return false;
        this.bons$mowzieStopSent = true;
        return true;
    }
}
