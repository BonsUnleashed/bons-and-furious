package bons.furious.mixin.mowziesmobs_c49;

import bons.furious.patch.mowziesmobs_c49.LeaderCache;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * mowziesmobs_elokosa_leader_reuse (Mowzie's Mobs 1.8.2, both sides, since 1.0.49, off by default; the class does not
 * exist in 1.7.3, where the switch is absent): getLeader answers the leader its last search found while
 * bons.furious.patch.mowziesmobs_c49.LeaderCache.keeps accepts it; otherwise the original search runs and its answer is
 * kept (one @Unique field). Named by string: the class is not in the Mowzie's Mobs this source compiles against.
 */
@Mixin(targets = "com.bobmowzie.mowziesmobs.server.entity.elokosa.EntityElokosaFollower", remap = false)
public abstract class ElokosaFollowerLeaderMixin {
    @Unique
    private LivingEntity bons$knownLeader;

    @Shadow(remap = false)
    public abstract Optional<UUID> getLeaderUUID();

    @WrapMethod(method = "getLeader()Lnet/minecraft/world/entity/LivingEntity;", remap = false)
    private LivingEntity bons$leader(Operation<LivingEntity> original) {
        if (LeaderCache.elokosaEnabled) {
            LivingEntity known = this.bons$knownLeader;
            if (LeaderCache.keeps((LivingEntity) (Object) this, this.getLeaderUUID(), known)) {
                LeaderCache.announceElokosa();
                return known;
            }
        }
        LivingEntity found = original.call();
        this.bons$knownLeader = found;
        return found;
    }
}
