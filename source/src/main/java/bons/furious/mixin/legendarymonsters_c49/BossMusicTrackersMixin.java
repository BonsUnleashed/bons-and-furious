package bons.furious.mixin.legendarymonsters_c49;

import bons.furious.patch.legendarymonsters_c49.BossMusicTrackers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * legendary_monsters_boss_music_trackers (Legendary Monsters 2.2.3 for Minecraft 1.21.1; server; since 1.0.49; exact
 * variant): both LegendaryMonsters.sendMSGToAll calls of IAnimatedBoss.tick (the play and the stop message) are wrapped; the
 * message goes to the boss's trackers only (BossMusicTrackers). Our own logic only.
 */
@Mixin(targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.OriginClasses.IAnimatedBoss", remap = false)
public abstract class BossMusicTrackersMixin {
    @WrapOperation(method = "tick()V",
            at = @At(value = "INVOKE", target = "Lnet/miauczel/legendary_monsters/LegendaryMonsters;sendMSGToAll(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V", remap = false),
            require = 2, allow = 2, remap = false)
    private void bons$musicToTrackers(CustomPacketPayload message, Operation<Void> original) {
        BossMusicTrackers.send((Entity) (Object) this, message, original);
    }
}
