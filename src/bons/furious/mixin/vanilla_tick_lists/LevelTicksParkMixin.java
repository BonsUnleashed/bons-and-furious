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
 * vanilla_level_ticks_blocked_park (Minecraft 1.20.1 on Forge 47.4.28, server side incl. the integrated server):
 * LevelTicks keeps its park state (TickPark.Parked, null = stock walk). sortContainersToTick (m_193216_): at HEAD the
 * entries whose inputs changed are unparked; the tick check call (LongPredicate.test, the only one in the method) is
 * redirected to the same call plus, on a false answer, parking the walk's current entry (a put on its existing key:
 * the value is replaced in place, no structural change of the map). addContainer (m_193231_) puts a parked value back
 * before it runs; removeContainer (m_193229_) and updateContainerScheduling (m_193279_) drop the kept value of the entry
 * they overwrite or remove. See TickPark for why the executed ticks are identical. No other mod in the pack mixes into
 * LevelTicks (both mixin indexes and the C2ME nested modules).
 */
@Mixin(value = LevelTicks.class, remap = false)
public abstract class LevelTicksParkMixin implements TickParkHost {
    @Shadow
    @Final
    private Long2LongMap f_193203_;   // nextTickForContainer
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

    @Inject(method = "m_193216_", at = @At("HEAD"))
    private void bons$unparkChanged(long gameTime, CallbackInfo ci) {
        TickPark.Parked p = this.bons$tickPark;
        if (p != null) {
            p.beforeWalk(this.f_193203_);
        }
    }

    @Redirect(method = "m_193216_", at = @At(value = "INVOKE", target = "Ljava/util/function/LongPredicate;test(J)Z"))
    private boolean bons$tickCheck(LongPredicate check, long chunk) {
        boolean answer = check.test(chunk);
        TickPark.Parked p = this.bons$tickPark;
        if (p != null) {
            if (TickPark.SHADOW) {
                p.shadowVisit(chunk, answer, this.f_193203_);
            } else if (!answer) {
                p.park(chunk, this.f_193203_);
            }
        }
        return answer;
    }

    @Inject(method = "m_193231_", at = @At("HEAD"))
    private void bons$beforeAddContainer(ChunkPos pos, LevelChunkTicks<?> ticks, CallbackInfo ci) {
        TickPark.Parked p = this.bons$tickPark;
        if (p != null) {
            p.unparkNow(this.f_193203_, pos.m_45588_());
        }
    }

    @Inject(method = "m_193229_", at = @At("HEAD"))
    private void bons$beforeRemoveContainer(ChunkPos pos, CallbackInfo ci) {
        TickPark.Parked p = this.bons$tickPark;
        if (p != null) {
            p.forget(pos.m_45588_());
        }
    }

    @Inject(method = "m_193279_", at = @At("HEAD"))
    private void bons$beforeReschedule(ScheduledTick<?> tick, CallbackInfo ci) {
        TickPark.Parked p = this.bons$tickPark;
        if (p != null) {
            p.forget(ChunkPos.m_151388_(tick.f_193377_()));
        }
    }
}
