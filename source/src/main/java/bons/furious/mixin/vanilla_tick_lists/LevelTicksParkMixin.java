package bons.furious.mixin.vanilla_tick_lists;

import bons.furious.patch.vanilla_tick_lists.TickPark;
import bons.furious.patch.vanilla_tick_lists.TickParkHost;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import java.util.function.LongPredicate;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_level_ticks_blocked_park (Minecraft 1.21.1 on NeoForge 21.1.252, server side incl. the integrated server):
 * LevelTicks keeps its park state (TickPark.Parked, null = stock walk). sortContainersToTick: at HEAD the entries whose
 * inputs changed are unparked; the tick check call (LongPredicate.test, the only one in the method) is redirected to the
 * same call plus, on a false answer, parking the walk's current entry (a put on its existing key: the value is replaced in
 * place, no structural change of the map). addContainer puts a parked value back before it runs; removeContainer and
 * updateContainerScheduling drop the kept value of the entry they overwrite or remove. See TickPark for why the executed
 * ticks are identical. No other mod among the 1.21.1 targets mixes into LevelTicks (Lithium 0.15.4 and Radium 0.13.1 replace
 * LevelChunkTicks' queue but keep its schedule callback and its peek).
 */
@Mixin(value = LevelTicks.class, remap = false)
public abstract class LevelTicksParkMixin implements TickParkHost {
    @Shadow
    @Final
    private Long2LongMap nextTickForContainer;
    @Unique
    private TickPark.Parked bons$tickPark;

    @Override
    public TickPark.Parked bons$tickPark() {
        return this.bons$tickPark;
    }

    @Override
    public void bons$setTickPark(TickPark.Parked parked) {
        this.bons$tickPark = parked;
    }

    @Inject(method = "sortContainersToTick(J)V", at = @At("HEAD"))
    private void bons$unparkChanged(long gameTime, CallbackInfo ci) {
        TickPark.Parked p = this.bons$tickPark;
        if (p != null) {
            p.beforeWalk(this.nextTickForContainer);
        }
    }

    @Redirect(method = "sortContainersToTick(J)V", at = @At(value = "INVOKE", target = "Ljava/util/function/LongPredicate;test(J)Z"))
    private boolean bons$tickCheck(LongPredicate check, long chunk) {
        boolean answer = check.test(chunk);
        TickPark.Parked p = this.bons$tickPark;
        if (p != null) {
            if (TickPark.SHADOW) {
                p.shadowVisit(chunk, answer, this.nextTickForContainer);
            } else if (!answer) {
                p.park(chunk, this.nextTickForContainer);
            }
        }
        return answer;
    }

    @Inject(method = "addContainer(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/ticks/LevelChunkTicks;)V", at = @At("HEAD"))
    private void bons$beforeAddContainer(ChunkPos pos, LevelChunkTicks<?> ticks, CallbackInfo ci) {
        TickPark.Parked p = this.bons$tickPark;
        if (p != null) {
            p.unparkNow(this.nextTickForContainer, pos.toLong());
        }
    }

    @Inject(method = "removeContainer(Lnet/minecraft/world/level/ChunkPos;)V", at = @At("HEAD"))
    private void bons$beforeRemoveContainer(ChunkPos pos, CallbackInfo ci) {
        TickPark.Parked p = this.bons$tickPark;
        if (p != null) {
            p.forget(pos.toLong());
        }
    }

    @Inject(method = "updateContainerScheduling(Lnet/minecraft/world/ticks/ScheduledTick;)V", at = @At("HEAD"))
    private void bons$beforeReschedule(ScheduledTick<?> tick, CallbackInfo ci) {
        TickPark.Parked p = this.bons$tickPark;
        if (p != null) {
            p.forget(ChunkPos.asLong(tick.pos()));
        }
    }
}
