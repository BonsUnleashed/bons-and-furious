package bons.furious.mixin.mahoutsukai_coo49;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.LevelEntityGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * mahoutsukai_all_entities_memo (vanilla Level, client dist): calls the level's getEntities() (getEntities, protected), the
 * getter whose getAll() Mahou Tsukai's ClientProxy.getAllEntities walks, so MahouEntitySets keys its memo on that getter's
 * lookup.
 */
@Mixin(value = Level.class, remap = false)
public interface MahouLevelEntitiesInvoker {
    @Invoker("getEntities")
    LevelEntityGetter<Entity> bons$mahoutsukaiCoo49Entities();
}
