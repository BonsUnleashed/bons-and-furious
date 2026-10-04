package bons.furious.mixin.crittersandcompanions_c2;

import bons.furious.patch.crittersandcompanions_c2.EntityClassCounts;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.LevelEntityGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * vanilla_entity_class_count_layer (Minecraft 1.20.1 + Forge 47.4.16, both sides), part 3 of 4: Level answers which
 * section storage its entity searches walk, through its own getEntities() (m_142646_: ServerLevel's entity manager, or
 * ClientLevel's), the same getter Level.getEntities(EntityTypeTest, AABB, Predicate, List, int) searches. Adds one method;
 * nothing the game calls changes. A duck interface in the patch package (not an accessor), so a gate whose key is on can
 * ask a level whose layer is off without loading a class from this mixin package.
 */
@Mixin(value = Level.class, remap = false)
public abstract class LevelClassCountMixin implements EntityClassCounts.CountedLevel {
    @Shadow
    protected abstract LevelEntityGetter<Entity> m_142646_();

    @Override
    public EntityClassCounts.Storage bons$classCountStorage() {
        return this.m_142646_() instanceof EntityClassCounts.CountedGetter getter ? getter.bons$classCountStorage() : null;
    }
}
