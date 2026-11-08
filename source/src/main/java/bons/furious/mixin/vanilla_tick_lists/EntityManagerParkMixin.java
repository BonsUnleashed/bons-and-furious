package bons.furious.mixin.vanilla_tick_lists;

import bons.furious.patch.vanilla_tick_lists.TickPark;
import bons.furious.patch.vanilla_tick_lists.TickParkRouted;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_level_ticks_blocked_park (Minecraft 1.21.1 on NeoForge 21.1.252, server side): processPendingLoads is the only
 * place that writes LOADED into chunkLoadStatuses, i.e. the only way areEntitiesLoaded(chunk) turns true. Its one
 * Long2ObjectMap.put call runs unchanged and then tells the level's router (set by TickParkAttach on the ServerLevel's
 * entity manager only). MixinExtras @WrapOperation at priority 1001, so it also applies after another mod's rewrite of the
 * method at the default priority that keeps the put; no mod among the 1.21.1 targets rewrites or hooks it.
 */
@Mixin(value = PersistentEntitySectionManager.class, remap = false, priority = 1001)
public abstract class EntityManagerParkMixin implements TickParkRouted {
    @Unique
    private TickPark.Router bons$tickParkRouter;

    @Override
    public void bons$setTickParkRouter(TickPark.Router router) {
        this.bons$tickParkRouter = router;
    }

    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "processPendingLoads()V", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;put(JLjava/lang/Object;)Ljava/lang/Object;"))
    private Object bons$entitiesLoaded(Long2ObjectMap statuses, long chunk, Object status, Operation<Object> original) {
        Object previous = original.call(statuses, chunk, status);
        TickPark.Router r = this.bons$tickParkRouter;
        if (r != null) {
            r.trigger(chunk);
        }
        return previous;
    }
}
