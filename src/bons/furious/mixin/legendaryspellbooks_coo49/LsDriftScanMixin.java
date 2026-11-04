package bons.furious.mixin.legendaryspellbooks_coo49;

import bons.furious.patch.legendaryspellbooks_coo49.LsDriftClient;
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
 * legendary_spellbooks_drift_scan_gate (Legendary Spellbooks 0.4.1, LGPL-3.0; client): MixinExtras @WrapOperation on the
 * ClientLevel.getEntities(Entity, AABB, Predicate) (m_6249_) call in FlamebornDriftSpellHandler.onPlayerTick: while nothing
 * in the level can drift (no Iron's IMagicEntity, every player tracked and none spin-attacking) the search answers a new
 * empty list after counting the profiler line; otherwise the original search. See LsDriftClient. Our own logic only;
 * nothing of the mod is carried.
 */
@Mixin(targets = "dev.higurashi.legendary_spellbooks.common.effects.handler.FlamebornDriftSpellHandler", remap = false)
public abstract class LsDriftScanMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "onPlayerTick(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;m_6249_(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$legendaryspellbooksCoo49DriftScan(ClientLevel level, Entity except, AABB box, Predicate predicate, Operation<List> original) {
        return LsDriftClient.driftSearch(level, except, box, predicate, original);
    }
}
