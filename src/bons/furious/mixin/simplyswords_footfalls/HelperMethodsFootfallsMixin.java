package bons.furious.mixin.simplyswords_footfalls;

import bons.furious.patch.simplyswords_footfalls.FootfallsUnheld;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * simplyswords_footfalls_unheld_skip (Simply Swords 1.56.0-1.20.1, Forge; both sides; since 1.0.49).
 *
 * HelperMethods.createFootfalls(entity, stack, level, ...) is called by the inventoryTick of every Simply Swords unique (40
 * classes), by Simply More's footfalls helper and by Simply Swords: Overhaul, for every inventory slot holding one of those
 * swords, every tick, on both sides. Its whole body sits behind
 * {@code entity instanceof Player p && Config.getBoolean("enableWeaponFootfalls", "General", ...) && p.getItemBySlot(MAINHAND) == stack},
 * and Config.getBoolean re-reads Simply Swords' general config file on every call. For a player whose main hand does not hold
 * this very stack the method therefore only reads that file and returns. The handler hands the method a null entity in that
 * case, so its own first test returns at once (FootfallsUnheld.entity); every other call runs the original unchanged. A
 * variable modifier, not a cancellable injection: it runs per slot per tick and must not allocate. Our own logic only; the
 * mod's code stays in its jar (Timefall Development License).
 */
@Mixin(targets = "net.sweenus.simplyswords.util.HelperMethods", remap = false)
public abstract class HelperMethodsFootfallsMixin {
    @ModifyVariable(method = "createFootfalls(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;ILnet/minecraft/core/particles/SimpleParticleType;Lnet/minecraft/core/particles/SimpleParticleType;Lnet/minecraft/core/particles/SimpleParticleType;Z)V",
            at = @At("HEAD"), argsOnly = true, remap = false)
    private static Entity bons$skipUnheldFootfalls(Entity value, Entity entity, ItemStack stack) {
        return FootfallsUnheld.entity(value, stack);
    }
}
