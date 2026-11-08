package bons.furious.mixin.mowziesmobs_c49;

import bons.furious.patch.mowziesmobs_c49.LeaderCache;
import com.bobmowzie.mowziesmobs.server.entity.umvuthana.EntityUmvuthanaFollower;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * mowziesmobs_umvuthana_leader_cache (Mowzie's Mobs, custom licence: no code carried; 1.21.1 tested build: Mowzie's Mobs
 * 1.21.1-1.8.2; both sides, since 1.0.49, off by default): getLeader answers the leader its last search found while
 * bons.furious.patch.mowziesmobs_c49.LeaderCache.keeps accepts it; otherwise the original search runs and its answer is
 * kept (one @Unique field).
 *
 * Ported to 1.21.1: getLeader is the same search (getEntitiesOfClass(leaderClass, box inflated by 32), first entity with
 * the leader id); getLeaderUUID still reads the synced LEADER entry.
 */
@Mixin(value = EntityUmvuthanaFollower.class, remap = false)
public abstract class UmvuthanaFollowerLeaderMixin {
    @Unique
    private LivingEntity bons$knownLeader;

    @WrapMethod(method = "getLeader()Lnet/minecraft/world/entity/LivingEntity;", remap = false)
    private LivingEntity bons$leader(Operation<LivingEntity> original) {
        if (LeaderCache.umvuthanaEnabled) {
            LivingEntity known = this.bons$knownLeader;
            if (LeaderCache.keeps((LivingEntity) (Object) this, ((EntityUmvuthanaFollower<?>) (Object) this).getLeaderUUID(), known)) {
                LeaderCache.announceUmvuthana();
                return known;
            }
        }
        LivingEntity found = original.call();
        this.bons$knownLeader = found;
        return found;
    }
}
