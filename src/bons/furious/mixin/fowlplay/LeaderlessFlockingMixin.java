package bons.furious.mixin.fowlplay;

import aqario.fowlplay.common.entity.FlyingBirdEntity;
import aqario.fowlplay.common.entity.ai.brain.behaviour.LeaderlessFlocking;
import java.util.List;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * fowlplay_flock_vectors (Fowl Play 1.1.2+1.20.1-forge), LeaderlessFlocking half; GuidedFlockingMixin has the same body.
 *
 * getHeading built the separation, alignment and cohesion vectors through chains of temporary Vec3 objects for every
 * neighbour on every tick. The same arithmetic now runs on plain doubles: each Vec3 operation is written out per
 * component in the original order (a - b as a + -b, float reciprocals, the sqrt comparison), and the six random draws
 * happen in the same order, so the heading is bit-for-bit the same.
 */
@Mixin(value = LeaderlessFlocking.class, remap = false)
public abstract class LeaderlessFlockingMixin {
    @Shadow
    @Final
    public float coherence;

    @Shadow
    @Final
    public float alignment;

    @Shadow
    @Final
    public float separation;

    @Shadow
    @Final
    public float separationRange;

    @Shadow
    private List<? extends AgeableMob> nearbyBirds;

    /**
     * @author BonsUnleashed
     * @reason Accumulate the flocking heading in primitive doubles instead of temporary Vec3 objects.
     */
    @Overwrite
    private Vec3 getHeading(FlyingBirdEntity bird) {
        double separationX = 0.0, separationY = 0.0, separationZ = 0.0;
        double alignmentX = 0.0, alignmentY = 0.0, alignmentZ = 0.0;
        double cohesionX = 0.0, cohesionY = 0.0, cohesionZ = 0.0;
        for (AgeableMob other : this.nearbyBirds) {
            Vec3 otherPos = other.m_20182_(), birdPos = bird.m_20182_();
            double dx = otherPos.f_82479_ + -birdPos.f_82479_, dy = otherPos.f_82480_ + -birdPos.f_82480_, dz = otherPos.f_82481_ + -birdPos.f_82481_;
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) < this.separationRange) {
                otherPos = other.m_20182_();
                birdPos = bird.m_20182_();
                separationX += -(otherPos.f_82479_ + -birdPos.f_82479_);
                separationY += -(otherPos.f_82480_ + -birdPos.f_82480_);
                separationZ += -(otherPos.f_82481_ + -birdPos.f_82481_);
            }
            Vec3 velocity = other.m_20184_();
            alignmentX += velocity.f_82479_;
            alignmentY += velocity.f_82480_;
            alignmentZ += velocity.f_82481_;
            otherPos = other.m_20182_();
            cohesionX += otherPos.f_82479_;
            cohesionY += otherPos.f_82480_;
            cohesionZ += otherPos.f_82481_;
        }
        double alignmentScale = 1.0F / (float) this.nearbyBirds.size();
        alignmentX *= alignmentScale;
        alignmentY *= alignmentScale;
        alignmentZ *= alignmentScale;
        double cohesionScale = 1.0F / (float) this.nearbyBirds.size();
        cohesionX *= cohesionScale;
        cohesionY *= cohesionScale;
        cohesionZ *= cohesionScale;
        Vec3 birdPos = bird.m_20182_();
        cohesionX += -birdPos.f_82479_;
        cohesionY += -birdPos.f_82480_;
        cohesionZ += -birdPos.f_82481_;
        cohesionX *= this.coherence;
        cohesionY *= this.coherence;
        cohesionZ *= this.coherence;
        alignmentX *= this.alignment;
        alignmentY *= this.alignment;
        alignmentZ *= this.alignment;
        separationX *= this.separation;
        separationY *= this.separation;
        separationZ *= this.separation;
        double randomX = bird.m_217043_().m_188501_() - bird.m_217043_().m_188501_();
        double randomY = bird.m_217043_().m_188501_() - bird.m_217043_().m_188501_();
        double randomZ = bird.m_217043_().m_188501_() - bird.m_217043_().m_188501_();
        randomX *= 0.5;
        randomY *= 0.5;
        randomZ *= 0.5;
        return new Vec3(cohesionX + separationX + alignmentX + randomX,
                cohesionY + separationY + alignmentY + randomY,
                cohesionZ + separationZ + alignmentZ + randomZ);
    }
}
