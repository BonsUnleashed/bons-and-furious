package bons.furious.mixin.compositematerial_coo49;

import bons.furious.patch.compositematerial_coo49.HealScanGate;
import io.github.rcneg.compositematerial.common.init.ItemRegistry;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * compositematerial_heal_scan_player_gate (Composite Material 1.3.0, both sides, server levels only): HealEvents.onLivingHeal's
 * one search for living entities around the healed entity (the event captured from the handler's argument) answers an
 * empty list when HealScanGate.decide says nobody can act (no player of the level uses the Dungeon Shield, or the healed
 * entity is a player blocking with it), searches for players only while one of them uses it, and stands aside (the
 * shipped search) otherwise.
 */
@Mixin(targets = "io.github.rcneg.compositematerial.common.events.HealEvents", remap = false)
public abstract class HealScanGateMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(method = "onLivingHeal(Lnet/minecraftforge/event/entity/living/LivingHealEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$compositematerial_coo49Scan(Level level, Class type, AABB box, LivingHealEvent event) {
        int d = HealScanGate.decide(level, event.getEntity(), ItemRegistry.DUNGEON_SHIELD);
        return d == HealScanGate.NONE ? HealScanGate.none() : level.m_45976_(d == HealScanGate.PLAYERS ? Player.class : type, box);
    }
}
