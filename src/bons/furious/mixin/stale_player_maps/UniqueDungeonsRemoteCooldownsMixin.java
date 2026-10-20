package bons.furious.mixin.stale_player_maps;

import bons.furious.patch.stale_player_maps.UniqueDungeonsMaps;
import com.becks.uniquedungeons.common.stackCooldowns.StackCooldownUtils;
import org.spongepowered.asm.mixin.Mixin;

/**
 * stale_player_map_purge_uniquedungeons_remote (Unique Dungeons 0.70.3.2, MIT; client only). Fix. A marker: it only adds
 * {@link UniqueDungeonsMaps.RemoteInert} to StackCooldownUtils, and it is applied only when every member the "remote
 * players' client cooldowns stay empty" premise rests on still matches its fingerprint - among them
 * StackCooldownUtils.updateClientCooldowns, which exists only in the client jar (@OnlyIn CLIENT), hence this separate
 * client-only key. UniqueDungeonsMaps asks for the marker before it lets a departed remote player's entry go. No member is
 * added or changed; no Unique Dungeons code is carried.
 */
@Mixin(value = StackCooldownUtils.class, remap = false)
public abstract class UniqueDungeonsRemoteCooldownsMixin implements UniqueDungeonsMaps.RemoteInert {
}
