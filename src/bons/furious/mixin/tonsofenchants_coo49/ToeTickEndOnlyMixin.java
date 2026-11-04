package bons.furious.mixin.tonsofenchants_coo49;

import bons.furious.patch.tonsofenchants_coo49.ToeLean;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * tonsofenchants_player_tick_end_only (Tons Of Enchants 1.0, MIT; both sides; change, off by default): a cancellable
 * @Inject at the HEAD of each of the mod's 17 PlayerTickEvent listeners (none of them checks the event's phase, so each
 * runs at the start and at the end of every player tick). With the switch on, the START phase returns at once
 * (ToeLean.skipStartPhase); the END phase runs the listener as shipped. Trade-off: the enchantments that act on a timer or
 * at random (Vitality's heal, Nutrition's feeding, Hunger's exhaustion, Spilling's item drops, Decay, Praise the Sun...)
 * act half as often as before, because both phases of one tick see the same tick count and game time; the enchantments
 * that set a state every tick (Slowness, Swimmer's Boots, Enhanced Handling, Lava Walker, Magnetize, Night Vision, Water
 * Breathing...) hold the same state at the end of each tick. Our own logic only.
 */
@Mixin(targets = {
        "com.deltajay.tonsofenchants.enchantments.ArmorEnchantments.chest.Vitality",
        "com.deltajay.tonsofenchants.enchantments.ArmorEnchantments.feet.HorseShoe",
        "com.deltajay.tonsofenchants.enchantments.ArmorEnchantments.feet.LavaWalker",
        "com.deltajay.tonsofenchants.enchantments.ArmorEnchantments.feet.Magnetize",
        "com.deltajay.tonsofenchants.enchantments.ArmorEnchantments.feet.SwimmersBoots",
        "com.deltajay.tonsofenchants.enchantments.ArmorEnchantments.head.FinalStand",
        "com.deltajay.tonsofenchants.enchantments.ArmorEnchantments.head.Nutrition",
        "com.deltajay.tonsofenchants.enchantments.ArmorEnchantments.head.enchNightVision",
        "com.deltajay.tonsofenchants.enchantments.ArmorEnchantments.head.enchWaterBreathing",
        "com.deltajay.tonsofenchants.enchantments.EEnchantments.EnhancedHandling",
        "com.deltajay.tonsofenchants.enchantments.Enchantments.BlessedItem",
        "com.deltajay.tonsofenchants.enchantments.Enchantments.Frostbite",
        "com.deltajay.tonsofenchants.enchantments.Enchantments.PraiseTheSun",
        "com.deltajay.tonsofenchants.enchantments.NegativeEnchantments.Decay",
        "com.deltajay.tonsofenchants.enchantments.NegativeEnchantments.Hunger",
        "com.deltajay.tonsofenchants.enchantments.NegativeEnchantments.Slowness",
        "com.deltajay.tonsofenchants.enchantments.NegativeEnchantments.Spilling"}, remap = false)
public abstract class ToeTickEndOnlyMixin {
    @Inject(method = {
            "regen(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V",
            "horseShoe(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V",
            "lavaWalk(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V",
            "magnetize(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V",
            "swimFaster(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V",
            "checkIf(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V",
            "feedPlayer(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V",
            "seeNight(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V",
            "breatheWater(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V",
            "handleEnhancedHandling(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V",
            "makeHeal(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V",
            "doWhenFrostbit(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V",
            "repairArmor(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V",
            "decayDurability(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V",
            "onPlayerTick(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V",
            "handleSlowness(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V",
            "spillItems(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V"},
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$tonsofenchantsCoo49TickEndOnly(TickEvent.PlayerTickEvent event, CallbackInfo ci) {
        if (ToeLean.skipStartPhase(event)) ci.cancel();
    }
}
