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
 * irons_spellbooks_caster_scan_gate (Iron's Spells 'n Spellbooks 1.20.1-3.16.3 / 3.15.6, All Rights Reserved; client):
 * MixinExtras @WrapOperation on the ClientLevel.getEntities(Entity, AABB, Predicate) (m_6249_) call in
 * ClientPlayerEvents.onPlayerTick: while nothing in the level can make the caster loop act (no IMagicEntity, every player
 * tracked and none spin-attacking or casting, no IMagicEntity part) the search answers a new empty list after counting the
 * profiler line; otherwise the original search. See IronsCasterGate. Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "io.redspace.ironsspellbooks.player.ClientPlayerEvents", remap = false)
public abstract class IronsCasterScanMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "onPlayerTick(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;m_6249_(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$ironsspellbooksCoo49CasterScan(ClientLevel level, Entity except, AABB box, Predicate predicate, Operation<List> original) {
        return IronsCasterGate.search(level, except, box, predicate, original);
    }
}
