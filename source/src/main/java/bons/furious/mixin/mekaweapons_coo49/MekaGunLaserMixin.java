package bons.furious.mixin.mekaweapons_coo49;

import bons.furious.patch.mekaweapons_coo49.MekaWeaponsFixes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * mekaweapons_gun_laser_server_only (Mekanism Weapons 4.2.1 for NeoForge 1.21.1; string target + @Pseudo as
 * MekaGunSidesMixin): HEAD of ItemMekaGun.fireLaser (private, called only from use) returns `to` - the method's own answer
 * when nothing is hit - without running on a client-side level, whose result only feeds sendLaserDataToPlayers (a no-op off
 * a ServerLevel). The client no longer searches for the hit entity or sets its copy of the hit mob on fire. See
 * MekaWeaponsFixes. A HEAD injection composes.
 */
@Pseudo
@Mixin(targets = "meranha.mekaweapons.items.ItemMekaGun", remap = false)
public abstract class MekaGunLaserMixin {
    @Inject(method = "fireLaser(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void bons$gunLaserServerOnly(Level level, ItemStack stack, Player owner, Vec3 from, Vec3 to, CallbackInfoReturnable<Vec3> cir) {
        if (MekaWeaponsFixes.gunLaser && level.isClientSide) {
            MekaWeaponsFixes.announce(2, "mekaweapons_gun_laser_server_only", "the Meka-Gun laser hits on the server only");
            cir.setReturnValue(to);
        }
    }
}
