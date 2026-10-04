package bons.furious.mixin.spawn_gate;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * vanilla_spawn_gate_visibility_memo (Minecraft 1.21.1 with NeoForge 21.1.252): read access to ServerLevel.entityManager.
 *
 * Ported to 1.21.1: same field (private final PersistentEntitySectionManager&lt;Entity&gt; entityManager), Mojang name.
 */
@Mixin(value = ServerLevel.class, remap = false)
public interface ServerLevelEntityManagerAccessor {
    @Accessor("entityManager")
    PersistentEntitySectionManager<?> bons$entityManager();
}
