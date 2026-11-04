package bons.furious.mixin.hostilenetworks_coo49;

import bons.furious.patch.hostilenetworks_coo49.HnnClientCache;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * hostilenetworks_client_entity_cache_lean (Hostile Neural Networks 5.3.3 ClientEntityCache; client). computeIfAbsent
 * answers a cached (level, type) from two plain map reads; anything else runs the original. The START-phase tick adds one
 * to every cached entity's tickCount with two loops instead of a stream; the END phase runs the original. See
 * HnnClientCache for why the answers are the same. No Hostile Neural Networks code is carried.
 */
@Mixin(targets = "dev.shadowsoffire.hostilenetworks.util.ClientEntityCache", remap = false)
public abstract class HnnClientEntityCacheMixin {
    @Shadow
    @Final
    private static Map<Level, Map<EntityType<?>, LivingEntity>> CACHE;

    @WrapMethod(method = "computeIfAbsent(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/entity/LivingEntity;")
    private static LivingEntity bons$hostilenetworksCoo49Lookup(EntityType<?> type, Level level, CompoundTag displayNbt, Operation<LivingEntity> original) {
        if (HnnClientCache.enabled) {
            LivingEntity cached = HnnClientCache.hit(CACHE, type, level);
            if (cached != null) return cached;
        }
        return original.call(type, level, displayNbt);
    }

    @WrapMethod(method = "tick(Lnet/minecraftforge/event/TickEvent$ClientTickEvent;)V")
    private static void bons$hostilenetworksCoo49Tick(TickEvent.ClientTickEvent event, Operation<Void> original) {
        if (HnnClientCache.enabled && event.phase == TickEvent.Phase.START) {
            HnnClientCache.tick(CACHE);
            return;
        }
        original.call(event);
    }
}
