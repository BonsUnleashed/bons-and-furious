package bons.furious.mixin.presencefootsteps;

import agentcraft.pure.AcFootstepSet;
import eu.ha3.presencefootsteps.PFConfig;
import eu.ha3.presencefootsteps.sound.PFIsolator;
import eu.ha3.presencefootsteps.sound.SoundEngine;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * presencefootsteps_duplicate_tracking (Presence Footsteps 1.20.1-1.9.1-beta.1, client).
 *
 * When more entities are near than the stepping cap, getTargets keeps one entity per (type, block position) and
 * tracked those pairs as Objects.hash(type, pos) in a HashSet: a boxed argument array, a boxed Integer and a HashMap
 * node per entity, every frame. AcFootstepSet.addPair computes the same 32-bit hash without the array and stores it in
 * a primitive int set that is only allocated when the first pair is added. Which entities are selected is unchanged.
 */
@Mixin(value = SoundEngine.class, remap = false)
public abstract class SoundEngineMixin {
    @Shadow
    private PFIsolator isolator;

    @Shadow
    @Final
    private PFConfig config;

    /**
     * @author BonsUnleashed
     * @reason Track the (type, position) pairs of the capped selection in an AcFootstepSet.
     */
    @Overwrite
    private Stream<? extends Entity> getTargets(Entity cameraEntity) {
        List<? extends Entity> entities = cameraEntity.m_9236_().m_6249_((Entity) null, cameraEntity.m_20191_().m_82400_(16), e -> {
            return e instanceof LivingEntity
                    && !(e instanceof WaterAnimal)
                    && !(e instanceof FlyingMob)
                    && !(e instanceof Shulker)
                    && !(e instanceof ArmorStand)
                    && !(e instanceof Boat)
                    && !(e instanceof AbstractMinecart)
                    && !this.isolator.getGolemMap().contains(e.m_6095_())
                    && !e.m_20159_()
                    && !((LivingEntity) e).m_5803_()
                    && (!(e instanceof Player) || !e.m_5833_())
                    && e.m_20280_(cameraEntity) <= 256
                    && this.config.getEntitySelector().test(e);
        });

        Comparator<Entity> nearest = Comparator.comparingDouble(e -> e.m_20280_(cameraEntity));

        if (entities.size() < this.config.getMaxSteppingEntities()) {
            return entities.stream();
        }

        Set<Integer> alreadyVisited = new AcFootstepSet();
        return entities.stream()
                .sorted(nearest)
                .filter(e -> e == cameraEntity || e instanceof Player
                        || alreadyVisited.size() < this.config.getMaxSteppingEntities()
                        && AcFootstepSet.addPair(alreadyVisited, e.m_6095_(), e.m_20183_()));
    }

    /**
     * Presence Footsteps compiles the last filter of getTargets to the synthetic method lambda$getTargets$3. It gets
     * the same body as the filter above, so no copy of the Objects.hash tracking stays in the class.
     *
     * @author BonsUnleashed
     * @reason Same AcFootstepSet tracking as in getTargets.
     */
    @Overwrite(aliases = "lambda$getTargets$3")
    private boolean keepTarget(Entity cameraEntity, Set<Integer> alreadyVisited, Entity e) {
        return e == cameraEntity || e instanceof Player
                || alreadyVisited.size() < this.config.getMaxSteppingEntities()
                && AcFootstepSet.addPair(alreadyVisited, e.m_6095_(), e.m_20183_());
    }
}
