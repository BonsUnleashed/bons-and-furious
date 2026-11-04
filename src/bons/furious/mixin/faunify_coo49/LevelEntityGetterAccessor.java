package bons.furious.mixin.faunify_coo49;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.LevelEntityGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * faunify_millipede_parent_uuid_lookup (Faunify 1.3.3, client): Level's own protected getEntities() (the level's entity
 * getter), for ParentLookup's UUID lookup. Reads only.
 */
@Mixin(value = Level.class, remap = false)
public interface LevelEntityGetterAccessor {
    @Invoker("m_142646_")
    LevelEntityGetter<Entity> bons$entityGetter();
}
