package bons.furious.mixin.ironsspellbooks_coo49;

import bons.furious.patch.ironsspellbooks_coo49.IronsCasterGate;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * irons_spellbooks_caster_scan_gate (Iron's Spells 'n Spellbooks, All Rights Reserved; 1.21.1 tested build 1.21.1-3.16.3;
 * client): MixinExtras @WrapOperation on the ClientLevel.getEntities(Entity, AABB, Predicate) call in
 * ClientPlayerEvents.onPlayerTick(PlayerTickEvent.Pre): while nothing in the level can make the caster loop act (no
 * IMagicEntity, every player tracked and none spin-attacking or casting, no IMagicEntity part) the search answers a new
 * empty list after counting the profiler line; otherwise the original search. See IronsCasterGate. Our own logic only;
 * nothing of the mod is carried.
 *
 * Ported to 1.21.1: the listener is onPlayerTick(PlayerTickEvent.Pre) (the start of your tick; was the END phase of
 * TickEvent.PlayerTickEvent); the search and the loop lambda read the same.
 */
@Mixin(targets = "io.redspace.ironsspellbooks.player.ClientPlayerEvents", remap = false)
public abstract class IronsCasterScanMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "onPlayerTick(Lnet/neoforged/neoforge/event/tick/PlayerTickEvent$Pre;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$ironsspellbooksCoo49CasterScan(ClientLevel level, Entity except, AABB box, Predicate predicate, Operation<List> original) {
        return IronsCasterGate.search(level, except, box, predicate, original);
    }
}
