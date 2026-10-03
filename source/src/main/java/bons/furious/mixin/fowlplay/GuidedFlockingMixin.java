package bons.furious.mixin.fowlplay;

import aqario.fowlplay.common.entity.bird.FlyingBirdEntity;
import aqario.fowlplay.common.entity.ai.brain.behaviour.GuidedFlocking;
import java.util.List;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * fowlplay_flock_vectors (Fowl Play 1.1.2+1.20.1-forge), GuidedFlocking half; LeaderlessFlockingMixin has the same body.
 *
 * getHeading built the separation, alignment and cohesion vectors through chains of temporary Vec3 objects for every
 * neighbour on every tick. The same arithmetic now runs on plain doubles: each Vec3 operation is written out per
 * component in the original order (a - b as a + -b, float reciprocals, the sqrt comparison), and the six random draws
 * happen in the same order, so the heading is bit-for-bit the same.
 */
@Mixin(value = GuidedFlocking.class, remap = false)
public abstract class GuidedFlockingMixin {
    @Shadow
    public float coherence;

    @Shadow
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
            Vec3 otherPos = other.position(), birdPos = bird.position();
            double dx = otherPos.x + -birdPos.x, dy = otherPos.y + -birdPos.y, dz = otherPos.z + -birdPos.z;
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) < this.separationRange) {
                otherPos = other.position();
                birdPos = bird.position();
                separationX += -(otherPos.x + -birdPos.x);
                separationY += -(otherPos.y + -birdPos.y);
                separationZ += -(otherPos.z + -birdPos.z);
            }
            Vec3 velocity = other.getDeltaMovement();
            alignmentX += velocity.x;
            alignmentY += velocity.y;
            alignmentZ += velocity.z;
            otherPos = other.position();
            cohesionX += otherPos.x;
            cohesionY += otherPos.y;
            cohesionZ += otherPos.z;
        }
        double alignmentScale = 1.0F / (float) this.nearbyBirds.size();
        alignmentX *= alignmentScale;
        alignmentY *= alignmentScale;
        alignmentZ *= alignmentScale;
        double cohesionScale = 1.0F / (float) this.nearbyBirds.size();
        cohesionX *= cohesionScale;
        cohesionY *= cohesionScale;
        cohesionZ *= cohesionScale;
        Vec3 birdPos = bird.position();
        cohesionX += -birdPos.x;
        cohesionY += -birdPos.y;
        cohesionZ += -birdPos.z;
        cohesionX *= this.coherence;
        cohesionY *= this.coherence;
        cohesionZ *= this.coherence;
        alignmentX *= this.alignment;
        alignmentY *= this.alignment;
        alignmentZ *= this.alignment;
        separationX *= this.separation;
        separationY *= this.separation;
        separationZ *= this.separation;
        double randomX = bird.getRandom().nextFloat() - bird.getRandom().nextFloat();
        double randomY = bird.getRandom().nextFloat() - bird.getRandom().nextFloat();
        double randomZ = bird.getRandom().nextFloat() - bird.getRandom().nextFloat();
        randomX *= 0.5;
        randomY *= 0.5;
        randomZ *= 0.5;
        return new Vec3(cohesionX + separationX + alignmentX + randomX,
                cohesionY + separationY + alignmentY + randomY,
                cohesionZ + separationZ + alignmentZ + randomZ);
    }
}
