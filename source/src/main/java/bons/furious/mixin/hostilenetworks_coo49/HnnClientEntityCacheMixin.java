package bons.furious.mixin.hostilenetworks_coo49;

import bons.furious.patch.hostilenetworks_coo49.HnnClientCache;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.shadowsoffire.hostilenetworks.util.DisplayEntity;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * hostilenetworks_client_entity_cache_lean (Hostile Neural Networks, MIT; 1.21.1 tested build 1.21.1-6.5.1
 * ClientEntityCache; client). computeIfAbsent answers a cached (level, display entry) from two plain map reads; anything
 * else runs the original. The ClientTickEvent.Pre listener adds one to every cached entity's tickCount with two loops
 * instead of a stream. See HnnClientCache for why the answers are the same. No Hostile Neural Networks code is carried.
 *
 * Ported to 1.21.1: the cache is keyed by DisplayEntity (the display record: type, nbt, scale, offsets) and holds Entity
 * (was EntityType -> LivingEntity); computeIfAbsent takes (DisplayEntity, Level); the tick listener is
 * tick(ClientTickEvent.Pre), so there is no END phase to hand back to the original.
 */
@Mixin(targets = "dev.shadowsoffire.hostilenetworks.util.ClientEntityCache", remap = false)
public abstract class HnnClientEntityCacheMixin {
    @Shadow
    @Final
    private static Map<Level, Map<DisplayEntity, Entity>> CACHE;

    @WrapMethod(method = "computeIfAbsent(Ldev/shadowsoffire/hostilenetworks/util/DisplayEntity;Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;")
    private static Entity bons$hostilenetworksCoo49Lookup(DisplayEntity display, Level level, Operation<Entity> original) {
        if (HnnClientCache.enabled) {
            Entity cached = HnnClientCache.hit(CACHE, display, level);
            if (cached != null) return cached;
        }
        return original.call(display, level);
    }

    @WrapMethod(method = "tick(Lnet/neoforged/neoforge/client/event/ClientTickEvent$Pre;)V")
    private static void bons$hostilenetworksCoo49Tick(ClientTickEvent.Pre event, Operation<Void> original) {
        if (HnnClientCache.enabled) {
            HnnClientCache.tick(CACHE);
            return;
        }
        original.call(event);
    }
}
