package bons.furious.mixin.projectile_gates;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * knightlib_bone_hitbox_gate (Minecraft 1.20.1, client, since 1.0.46): read access to AbstractClientPlayer.playerInfo
 * (f_108546_), the PlayerInfo that getPlayerInfo() fills the first time it is asked. ProjectileGates lets KnightLib's
 * pick loop be skipped only while every player of the level has it already, so skipping that loop's isSpectator() calls
 * cannot change when it is filled. Adds no behaviour and carries no Minecraft code.
 */
@Mixin(value = AbstractClientPlayer.class, remap = false)
public interface AbstractClientPlayerInfoAccessor {
    @Accessor(value = "f_108546_", remap = false)
    PlayerInfo bons$playerInfo();
}
