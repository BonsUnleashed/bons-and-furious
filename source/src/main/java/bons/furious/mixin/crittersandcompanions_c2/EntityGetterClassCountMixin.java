package bons.furious.mixin.crittersandcompanions_c2;

import bons.furious.patch.crittersandcompanions_c2.EntityClassCounts;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.LevelEntityGetterAdapter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * vanilla_entity_class_count_layer (Minecraft 1.21.1 + NeoForge 21.1.252, both sides), part 4 of 4: LevelEntityGetterAdapter
 * (the getter both section managers hand out) answers the section storage its get(EntityTypeTest, AABB, consumer) searches.
 * Adds one method; nothing the game calls changes.
 * Ported to 1.21.1: same final sectionStorage field and same get(EntityTypeTest, AABB, consumer) body; no change beyond
 * Javadoc.
 */
@Mixin(value = LevelEntityGetterAdapter.class, remap = false)
public abstract class EntityGetterClassCountMixin implements EntityClassCounts.CountedGetter {
    @Shadow
    @Final
    private EntitySectionStorage<?> sectionStorage;

    @Override
    public EntityClassCounts.Storage bons$classCountStorage() {
        return this.sectionStorage instanceof EntityClassCounts.Storage storage ? storage : null;
    }
}
