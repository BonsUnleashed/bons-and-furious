package bons.furious.mixin.boss_music_c49;

import bons.furious.patch.boss_music_c49.BossMusicStops;
import com.github.L_Ender.cataclysm.entity.etc.Animation_Monsters;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cataclysm_boss_music_stop_once (trade-off, off by default; L_Ender's Cataclysm 3.16, 3.31 identical; CC-BY-NC-ND-4.0, no
 * code carried; server). Animation_Monsters.tick sends MessageMusic(play) (the first SimpleChannel.send) or
 * MessageMusic(stop) (the second) to the boss's trackers every tick. A play clears this boss's "stop sent" mark; a stop is
 * sent only while the mark is clear and then sets it. See BossMusicStops.
 */
@Mixin(value = Animation_Monsters.class, remap = false)
public abstract class CataclysmBossMusicStopOnceMixin {
    @Unique
    private boolean bons$stopSent;

    @WrapWithCondition(method = "m_8119_", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/network/simple/SimpleChannel;send(Lnet/minecraftforge/network/PacketDistributor$PacketTarget;Ljava/lang/Object;)V",
            ordinal = 0), require = 1, allow = 1)
    private boolean bons$play(SimpleChannel channel, PacketDistributor.PacketTarget target, Object message) {
        this.bons$stopSent = false;
        return true;
    }

    @WrapWithCondition(method = "m_8119_", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/network/simple/SimpleChannel;send(Lnet/minecraftforge/network/PacketDistributor$PacketTarget;Ljava/lang/Object;)V",
            ordinal = 1), require = 1, allow = 1)
    private boolean bons$stop(SimpleChannel channel, PacketDistributor.PacketTarget target, Object message) {
        if (!BossMusicStops.sendStop(BossMusicStops.cataclysm, this.bons$stopSent)) return false;
        this.bons$stopSent = true;
        return true;
    }
}
