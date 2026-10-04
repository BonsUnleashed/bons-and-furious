package bons.furious.mixin.spawn_gate;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** vanilla_spawn_gate_visibility_memo (Minecraft 1.20.1): read access to ServerLevel.entityManager (f_143244_). */
@Mixin(value = ServerLevel.class, remap = false)
public interface ServerLevelEntityManagerAccessor {
    @Accessor("f_143244_")
    PersistentEntitySectionManager<?> bons$entityManager();
}
