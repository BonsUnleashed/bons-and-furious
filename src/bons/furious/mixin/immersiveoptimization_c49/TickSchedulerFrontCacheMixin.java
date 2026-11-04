package bons.furious.mixin.immersiveoptimization_c49;

import bons.furious.patch.immersiveoptimization_c49.IoFrontCache;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import net.conczin.immersive_optimization.TickScheduler;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * immersiveoptimization_be_priority_front_cache (Immersive Optimization 0.2.0, GPL-3.0; client side): call-site wrappers.
 * In shouldTick(Level, long): getLevelData and the blockEntityPriorities.computeIfAbsent answer from the front caches when
 * the generation is unchanged (else they call through and refill them). In tickLevel and reset: the priority-map clear,
 * the level-map clear and a LevelData creation raise the generation before and after. Why that equals the original:
 * IoFrontCache.
 */
@Mixin(value = TickScheduler.class, remap = false)
public abstract class TickSchedulerFrontCacheMixin {
    @Unique
    private final AtomicInteger bons$ioC49Generation = new AtomicInteger();
    @Unique
    private Thread bons$ioC49Owner;
    @Unique
    private boolean bons$ioC49Valid;
    @Unique
    private int bons$ioC49CachedGen;
    @Unique
    private Level bons$ioC49Level;
    @Unique
    private TickScheduler.LevelData bons$ioC49Data;
    @Unique
    private Long2IntOpenHashMap bons$ioC49Priorities;

    @Unique
    private boolean bons$ioC49Mine() {
        Thread t = Thread.currentThread();
        if (this.bons$ioC49Owner == null) this.bons$ioC49Owner = t;
        return this.bons$ioC49Owner == t;
    }

    @WrapOperation(method = "shouldTick(Lnet/minecraft/world/level/Level;J)Z",
            at = @At(value = "INVOKE", target = "Lnet/conczin/immersive_optimization/TickScheduler;getLevelData(Lnet/minecraft/world/level/Level;)Lnet/conczin/immersive_optimization/TickScheduler$LevelData;"),
            require = 1, allow = 1)
    private TickScheduler.LevelData bons$ioC49LevelData(TickScheduler self, Level level, Operation<TickScheduler.LevelData> original) {
        if (!IoFrontCache.enabled || !this.bons$ioC49Mine()) return original.call(self, level);
        int gen = this.bons$ioC49Generation.get();
        if (this.bons$ioC49Valid && gen == this.bons$ioC49CachedGen && level == this.bons$ioC49Level) {
            TickScheduler.LevelData data = this.bons$ioC49Data;
            if (IoFrontCache.SHADOW) IoFrontCache.shadow(data, original.call(self, level), "level data");
            return data;
        }
        TickScheduler.LevelData data = original.call(self, level);
        this.bons$ioC49Level = level;
        this.bons$ioC49Data = data;
        this.bons$ioC49CachedGen = gen;
        this.bons$ioC49Valid = true;
        if (this.bons$ioC49Priorities == null) {
            this.bons$ioC49Priorities = new Long2IntOpenHashMap();
            this.bons$ioC49Priorities.defaultReturnValue(Integer.MIN_VALUE);
        } else {
            this.bons$ioC49Priorities.clear();
        }
        return data;
    }

    @WrapOperation(method = "shouldTick(Lnet/minecraft/world/level/Level;J)Z",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"),
            require = 1, allow = 1)
    private Object bons$ioC49Priority(Map<Object, Object> map, Object key, Function<Object, Object> compute, Operation<Object> original) {
        if (!IoFrontCache.enabled || !this.bons$ioC49Mine() || !this.bons$ioC49Valid || !(key instanceof Long pos)) return original.call(map, key, compute);
        int gen = this.bons$ioC49Generation.get();
        TickScheduler.LevelData data = this.bons$ioC49Data;
        if (gen != this.bons$ioC49CachedGen || data == null || (Object) map != data.blockEntityPriorities) return original.call(map, key, compute);
        long p = pos.longValue();
        int cached = this.bons$ioC49Priorities.get(p);
        if (cached != Integer.MIN_VALUE) {
            IoFrontCache.announce();
            Integer answer = Integer.valueOf(cached);
            if (IoFrontCache.SHADOW) IoFrontCache.shadow(answer, original.call(map, key, compute), "priority of chunk " + p);
            return answer;
        }
        Object result = original.call(map, key, compute);
        if (result instanceof Integer value && this.bons$ioC49Generation.get() == gen) this.bons$ioC49Priorities.put(p, value.intValue());
        return result;
    }

    @Unique
    private void bons$ioC49Bump() {
        this.bons$ioC49Generation.incrementAndGet();
    }

    @WrapOperation(method = "tickLevel(Lnet/minecraft/server/level/ServerLevel;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;clear()V"), require = 1, allow = 1)
    private void bons$ioC49ClearPriorities(Map<?, ?> map, Operation<Void> original) {
        this.bons$ioC49Bump();
        original.call(map);
        this.bons$ioC49Bump();
    }

    @WrapOperation(method = "tickLevel(Lnet/minecraft/server/level/ServerLevel;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"),
            require = 1, allow = 1)
    private Object bons$ioC49CreateLevelData(Map<Object, Object> map, Object key, Function<Object, Object> create, Operation<Object> original) {
        boolean had = map.containsKey(key);
        if (!had) this.bons$ioC49Bump();
        Object result = original.call(map, key, create);
        if (!had) this.bons$ioC49Bump();
        return result;
    }

    @WrapOperation(method = "reset()V", at = @At(value = "INVOKE", target = "Ljava/util/Map;clear()V"), require = 1, allow = 1)
    private void bons$ioC49ClearLevels(Map<?, ?> map, Operation<Void> original) {
        this.bons$ioC49Bump();
        original.call(map);
        this.bons$ioC49Bump();
    }
}
