package bons.furious.mixin.vanilla_tick_lists;

import bons.furious.patch.vanilla_tick_lists.TickPark;
import bons.furious.patch.vanilla_tick_lists.TickParkAttach;
import bons.furious.patch.vanilla_tick_lists.TickParkRouted;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelTicks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_level_ticks_blocked_park (Minecraft 1.20.1 on Forge 47.4.28, server side): at the end of the ServerLevel
 * constructor TickParkAttach wires the level's two LevelTicks (f_8553_ blocks, f_184047_ fluids, both created with this
 * level's isPositionTickingWithEntitiesLoaded, m_184110_) and the parts that check reads. startTickingChunk (m_184102_)
 * runs inside the chunk's ticking future just before it completes, which is when that check's last input (the holder's
 * ticking future) turns true for the chunk: its HEAD tells the router. Constructor RETURN and method HEAD injections only.
 */
@Mixin(value = ServerLevel.class, remap = false)
public abstract class ServerLevelTickParkMixin implements TickParkRouted {
    @Shadow
    @Final
    private LevelTicks<Block> f_8553_;
    @Shadow
    @Final
    private LevelTicks<Fluid> f_184047_;
    @Shadow
    @Final
    private ServerChunkCache f_8547_;
    @Shadow
    @Final
    private PersistentEntitySectionManager<Entity> f_143244_;
    @Unique
    private TickPark.Router bons$tickParkRouter;

    @Override
    public void bons$setTickParkRouter(TickPark.Router router) {
        this.bons$tickParkRouter = router;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bons$attachTickPark(CallbackInfo ci) {
        TickParkAttach.attach((ServerLevel) (Object) this, this.f_8553_, this.f_184047_, this.f_8547_, this.f_143244_);
    }

    @Inject(method = "m_184102_", at = @At("HEAD"))
    private void bons$chunkStartsTicking(LevelChunk chunk, CallbackInfo ci) {
        TickPark.Router r = this.bons$tickParkRouter;
        if (r != null) {
            r.trigger(chunk.m_7697_().m_45588_());
        }
    }
}
