package bons.furious.mixin.mowziesmobs_c49;

import bons.furious.patch.mowziesmobs_c49.LeaderCache;
import com.bobmowzie.mowziesmobs.server.entity.elokosa.EntityElokosaFollower;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * mowziesmobs_elokosa_leader_reuse (Mowzie's Mobs, custom licence: no code carried; 1.21.1 tested build: Mowzie's Mobs
 * 1.21.1-1.8.2; both sides, since 1.0.49, off by default): getLeader answers the leader its last search found while
 * bons.furious.patch.mowziesmobs_c49.LeaderCache.keeps accepts it; otherwise the original search runs and its answer is
 * kept (one @Unique field).
 *
 * Ported to 1.21.1: getLeader is the same search as in the Forge 1.8.2 build; hasRestriction and getRestrictCenter (the
 * leap and wander restriction of the follower) still call it, up to three times per question.
 */
@Mixin(value = EntityElokosaFollower.class, remap = false)
public abstract class ElokosaFollowerLeaderMixin {
    @Unique
    private LivingEntity bons$knownLeader;

    @WrapMethod(method = "getLeader()Lnet/minecraft/world/entity/LivingEntity;", remap = false)
    private LivingEntity bons$leader(Operation<LivingEntity> original) {
        if (LeaderCache.elokosaEnabled) {
            LivingEntity known = this.bons$knownLeader;
            if (LeaderCache.keeps((LivingEntity) (Object) this, ((EntityElokosaFollower<?>) (Object) this).getLeaderUUID(), known)) {
                LeaderCache.announceElokosa();
                return known;
            }
        }
        LivingEntity found = original.call();
        this.bons$knownLeader = found;
        return found;
    }
}
