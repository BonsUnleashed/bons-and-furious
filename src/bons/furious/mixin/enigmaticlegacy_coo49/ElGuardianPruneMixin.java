package bons.furious.mixin.enigmaticlegacy_coo49;

import bons.furious.patch.enigmaticlegacy_coo49.AngeredGuardians;
import com.google.common.collect.Multimap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * enigmaticlegacy_angered_guardians_prune (Enigmatic Legacy 2.30.1, server side; Enigmatic Legacy's own licence, treated
 * as All Rights Reserved: no code of the mod is carried). MixinExtras @WrapOperation on the only Multimap.put call of
 * EnigmaticEventHandler.onAttackTargetSet, the call that records a guardian in the static AGERED_GUARDIANS multimap: right
 * before the put, AngeredGuardians.prune removes the values whose guardian was killed or discarded, then the put runs as
 * shipped. The multimap is only ever read by containsEntry(player, the guardian whose target changes), which compares
 * entities by their id; a killed or discarded entity never targets again and no other entity gets its id, so every later
 * answer is the same. Player keys are never touched (a respawned player keeps its id).
 */
@Mixin(targets = "com.aizistral.enigmaticlegacy.handlers.EnigmaticEventHandler", remap = false)
public abstract class ElGuardianPruneMixin {
    @WrapOperation(method = "onAttackTargetSet(Lnet/minecraftforge/event/entity/living/LivingChangeTargetEvent;)V",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Multimap;put(Ljava/lang/Object;Ljava/lang/Object;)Z"),
            require = 1, allow = 1)
    private boolean bons$enigmaticlegacyCoo49PrunePut(Multimap<Object, Object> map, Object player, Object guardian, Operation<Boolean> original) {
        AngeredGuardians.prune(map);
        return original.call(map, player, guardian);
    }
}
