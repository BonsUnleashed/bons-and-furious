package bons.furious.mixin.soulsweapons_coo49;

import bons.furious.patch.soulsweapons_coo49.SoulsDespawnPeek;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * soulsweapons_despawn_timer_no_write (Soulslike Weaponry 1.4.10, same bytes in 1.4.8; CC0-1.0; server side; since 1.0.49).
 * Exact variant. Soulslike Weaponry's own Entity.tick hook asks DespawnTimerData.getDespawnTicks for every server entity on
 * every tick, and that getter writes "despawn_timer" = 0 into the entity's Forge persistent data whenever the key is
 * missing (creating the whole ForgeData compound first for an entity that has none). HEAD inject: when SoulsDespawnPeek can
 * answer without that write (the entity is not a player, its class uses Forge's own getPersistentData), the getter returns
 * the same number it would return (the stored value when the key exists, 0 otherwise) without creating or writing
 * anything; otherwise the getter runs as shipped. Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "net.soulsweaponry.entitydata.DespawnTimerData", remap = false)
public abstract class SoulsDespawnTimerMixin {
    @Inject(method = "getDespawnTicks(Lnet/minecraft/world/entity/Entity;)I", at = @At("HEAD"), cancellable = true,
            remap = false, require = 1, allow = 1)
    private static void bons$soulsweaponsCoo49DespawnPeek(Entity entity, CallbackInfoReturnable<Integer> cir) {
        long ticks = SoulsDespawnPeek.ticks(entity);
        if (ticks != SoulsDespawnPeek.RUN_ORIGINAL) cir.setReturnValue((int) ticks);
    }
}
