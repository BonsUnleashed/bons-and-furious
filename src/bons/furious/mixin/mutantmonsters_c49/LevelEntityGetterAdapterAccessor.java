package bons.furious.mixin.mutantmonsters_c49;

import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.LevelEntityGetterAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * mutantmonsters_spawn_limit_index (since 1.0.49): the EntityLookup behind a level's entity getter (the one getAllEntities
 * reads). Accessor only; its name carries the group (an accessor of one name and descriptor added by two groups to one
 * target is a duplicate member).
 */
@Mixin(value = LevelEntityGetterAdapter.class, remap = false)
public interface LevelEntityGetterAdapterAccessor {
    @Accessor(value = "f_156940_", remap = false)
    EntityLookup<?> bons$mutantVisibleEntities();
}
