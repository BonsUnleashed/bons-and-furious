package bons.furious.mixin.darkdoppelganger_coo49;

import bons.furious.patch.darkdoppelganger_coo49.DoppelMinions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * darkdoppelganger_single_minion_add (Dark Doppelganger 9.9.0, All Rights Reserved; server; gameplay fix, off by default):
 * MixinExtras @WrapOperation on both Level.addFreshEntity calls of DarkDoppelgangerEntity.summonMinions (the summon loop
 * has its whole minion setup and add pasted twice). The first add of a minion runs as shipped and, when it succeeded, the
 * minion is remembered (a @Unique field of the boss); the second add of that same minion is then left out and answers
 * false, the answer the shipped second add gives once the first succeeded (it is discarded anyway). A first add that
 * failed keeps its second attempt. Our own logic only.
 */
@Mixin(targets = "net.bandit.darkdoppelganger.entity.DarkDoppelgangerEntity", remap = false)
public abstract class SingleMinionAddMixin {
    @Unique
    private Entity bons$darkdoppelgangerCoo49Added;

    @WrapOperation(method = "summonMinions()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_7967_(Lnet/minecraft/world/entity/Entity;)Z", ordinal = 0),
            require = 1, allow = 1)
    private boolean bons$darkdoppelgangerCoo49FirstAdd(Level level, Entity minion, Operation<Boolean> original) {
        boolean added = original.call(level, minion);
        this.bons$darkdoppelgangerCoo49Added = added ? minion : null;
        return added;
    }

    @WrapOperation(method = "summonMinions()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_7967_(Lnet/minecraft/world/entity/Entity;)Z", ordinal = 1),
            require = 1, allow = 1)
    private boolean bons$darkdoppelgangerCoo49SecondAdd(Level level, Entity minion, Operation<Boolean> original) {
        Entity added = this.bons$darkdoppelgangerCoo49Added;
        this.bons$darkdoppelgangerCoo49Added = null;
        if (added == minion && DoppelMinions.skipSecondAdd()) return false;
        return original.call(level, minion);
    }
}
