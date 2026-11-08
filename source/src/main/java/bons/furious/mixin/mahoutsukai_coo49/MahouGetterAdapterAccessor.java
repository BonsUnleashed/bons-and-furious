package bons.furious.mixin.mahoutsukai_coo49;

import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.LevelEntityGetterAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * mahoutsukai_all_entities_memo (vanilla LevelEntityGetterAdapter, client dist): read access to the adapter's entity lookup
 * (visibleEntities), whose getAllEntities() is what the adapter's getAll() returns.
 */
@Mixin(value = LevelEntityGetterAdapter.class, remap = false)
public interface MahouGetterAdapterAccessor {
    @Accessor("visibleEntities")
    EntityLookup<?> bons$mahoutsukaiCoo49Lookup();
}
