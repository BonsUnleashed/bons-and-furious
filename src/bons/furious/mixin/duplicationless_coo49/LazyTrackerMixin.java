package bons.furious.mixin.duplicationless_coo49;

import bons.furious.patch.duplicationless_coo49.LazyTracker;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * duplicationless_lazy_tracker (Duplicationless 1.20.1-1.2.1, both sides; kind change, off by default; no Duplicationless code
 * carried). On AbstractEntityTracker (server and client trackers): update() queues nothing for a dimension nobody has asked
 * about; the first question (chunkSections, which every query passes) builds that dimension from the level's entities;
 * reset() forgets the built dimensions. See LazyTracker.
 */
@Mixin(targets = "me.kall.duplicationless.data.AbstractEntityTracker", remap = false)
public abstract class LazyTrackerMixin implements LazyTracker.Host {
    @Shadow
    @Final
    protected ConcurrentLinkedQueue<Runnable> UPDATE_TASKS;

    @Unique
    private final Set<ResourceLocation> bons$duplicationlessBuiltDims = ConcurrentHashMap.newKeySet();

    @Unique
    private volatile int bons$duplicationlessModeValue;

    @Shadow
    protected abstract void update(Entity entity, Level level, boolean add, ObjectList<ResourceLocation> filters);

    @Shadow
    public abstract void drainUpdateTasks();

    @Override
    public int bons$duplicationlessMode() {
        return this.bons$duplicationlessModeValue;
    }

    @Override
    public void bons$duplicationlessSetMode(int mode) {
        this.bons$duplicationlessModeValue = mode;
    }

    @Override
    public Set<ResourceLocation> bons$duplicationlessBuilt() {
        return this.bons$duplicationlessBuiltDims;
    }

    @Override
    public void bons$duplicationlessAdd(Entity entity, Level level, ObjectList<ResourceLocation> filters) {
        this.update(entity, level, true, filters);
    }

    @Override
    public ConcurrentLinkedQueue<Runnable> bons$duplicationlessTasks() {
        return this.UPDATE_TASKS;
    }

    @Override
    public void bons$duplicationlessDrain() {
        this.drainUpdateTasks();
    }

    @Inject(method = "update(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/Level;ZLit/unimi/dsi/fastutil/objects/ObjectList;)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void bons$duplicationlessLazyUpdate(Entity entity, Level level, boolean add, ObjectList<ResourceLocation> filters, CallbackInfo ci) {
        if (LazyTracker.skipUpdate(this, level)) ci.cancel();
    }

    @Inject(method = "chunkSections(Lnet/minecraft/world/level/Level;J)Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;", at = @At("HEAD"), require = 1)
    private void bons$duplicationlessLazyQuestion(Level level, long chunk, CallbackInfoReturnable<?> cir) {
        LazyTracker.beforeQuestion(this, level);
    }

    @Inject(method = "reset()V", at = @At("RETURN"), require = 1)
    private void bons$duplicationlessLazyReset(CallbackInfo ci) {
        LazyTracker.reset(this);
    }
}
