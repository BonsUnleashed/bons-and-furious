package bons.furious.mixin.valkyrienskies_mod;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.mod.common.entity.handling.VSEntityHandler;
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager;
import org.valkyrienskies.mod.common.util.AcVsSweep5;

/**
 * valkyrien_entity_handlers (Valkyrien Skies 2.4.11).
 *
 * VSEntityManager.getDefaultHandler asked a Guava cache for the default handler of the entity's type on every call,
 * which allocates a loader lambda and takes the cache's segment lock. The handler only depends on the entity type, so
 * it is kept in a ConcurrentHashMap keyed by type (AcVsSweep5); the first stored handler wins, like the cache's single
 * load per key, and a type seen for the first time is still resolved by VS's own determineDefaultHandler.
 */
@Mixin(value = VSEntityManager.class, remap = false)
public abstract class VSEntityManagerMixin {
    @Shadow
    private VSEntityHandler determineDefaultHandler(Entity entity) {
        throw new AssertionError();
    }

    /**
     * @author BonsUnleashed
     * @reason Per-type handler lookup without the Guava cache's lambda and lock on every call.
     */
    @Overwrite
    private final VSEntityHandler getDefaultHandler(Entity entity) {
        EntityType<?> type = entity.m_6095_();
        VSEntityHandler handler = AcVsSweep5.cachedDefaultHandler(type);
        if (handler != null) {
            return handler;
        }
        return AcVsSweep5.storeDefaultHandler(type, this.determineDefaultHandler(entity));
    }
}
