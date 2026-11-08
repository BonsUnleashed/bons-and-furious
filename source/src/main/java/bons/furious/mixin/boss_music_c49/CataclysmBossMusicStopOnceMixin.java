package bons.furious.mixin.boss_music_c49;

import bons.furious.patch.boss_music_c49.BossMusicStops;
import com.github.L_Ender.cataclysm.entity.etc.Animation_Monsters;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cataclysm_boss_music_stop_once (trade-off, off by default; L_Ender's Cataclysm, CC-BY-NC-ND-4.0, no code carried; 1.21.1
 * tested build: L_Ender's Cataclysm 1.21.1-3.33; server). Animation_Monsters.tick sends MessageMusic(play) (the first
 * PacketDistributor.sendToPlayersTrackingEntityAndSelf) or MessageMusic(stop) (the second) to the boss's trackers every
 * tick. A play clears this boss's "stop sent" mark; a stop is sent only while the mark is clear and then sets it. See
 * BossMusicStops.
 *
 * Ported to 1.21.1: 3.33's tick has the same two sends (NeoForge's static PacketDistributor call with a MessageMusic
 * payload instead of a SimpleChannel send); its client handler (MessageMusic.handle, enqueued) still stops the music only
 * while BossMusicPlayer's sound is bound to that boss.
 */
@Mixin(value = Animation_Monsters.class, remap = false)
public abstract class CataclysmBossMusicStopOnceMixin {
    @Unique
    private boolean bons$stopSent;

    @WrapWithCondition(method = "tick()V", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/network/PacketDistributor;sendToPlayersTrackingEntityAndSelf(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;[Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
            ordinal = 0), require = 1, allow = 1)
    private boolean bons$play(Entity entity, CustomPacketPayload payload, CustomPacketPayload[] payloads) {
        this.bons$stopSent = false;
        return true;
    }

    @WrapWithCondition(method = "tick()V", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/network/PacketDistributor;sendToPlayersTrackingEntityAndSelf(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;[Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
            ordinal = 1), require = 1, allow = 1)
    private boolean bons$stop(Entity entity, CustomPacketPayload payload, CustomPacketPayload[] payloads) {
        if (!BossMusicStops.sendStop(BossMusicStops.cataclysm, this.bons$stopSent)) return false;
        this.bons$stopSent = true;
        return true;
    }
}
