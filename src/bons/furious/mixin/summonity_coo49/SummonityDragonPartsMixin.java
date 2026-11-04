package bons.furious.mixin.summonity_coo49;

import bons.furious.patch.summonity_coo49.DragonParts;
import com.fevzi.summonity.entity.SummonedStardustDragonEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * summonity_dragon_parts_memo (Summonity 0.1.7.1, server side; change, off by default; All Rights Reserved: no code of the
 * mod is carried). SummonedStardustDragonEntity.getDragonParts(level) searches every Stardust Dragon entity in the head's
 * box grown by 64 blocks for the parts of this head, 2-3 times per tick per head. MixinExtras @WrapOperation on its calls:
 * the call in ensureBodyChain (the head's chain repair) searches as shipped and keeps the list on the head (two @Unique
 * fields); the calls in updateBodyFromPath and tickBodyAttack reuse the kept list for up to 5 ticks while every kept part
 * is still in the world (DragonParts.kept), otherwise search as shipped and keep. A part the head spawns (spawnPart,
 * @Inject at its start) drops the kept list. What differs: for up to 4 ticks a part that leaves the 64-block box or a part
 * added by other means (chunk loading) is seen later, and the parts keep their list order.
 */
@Mixin(targets = "com.fevzi.summonity.entity.SummonedStardustDragonEntity", remap = false)
public abstract class SummonityDragonPartsMixin {
    @Unique
    private List<?> bons$summonityCoo49Parts;
    @Unique
    private long bons$summonityCoo49PartsAt;

    @WrapOperation(method = {"updateBodyFromPath()V", "tickBodyAttack()V"},
            at = @At(value = "INVOKE", target = "Lcom/fevzi/summonity/entity/SummonedStardustDragonEntity;getDragonParts(Lnet/minecraft/server/level/ServerLevel;)Ljava/util/List;"),
            require = 2, allow = 2)
    private List<?> bons$summonityCoo49PartsKept(SummonedStardustDragonEntity self, ServerLevel level, Operation<List<?>> original) {
        if (!DragonParts.enabled) return original.call(self, level);
        long now = level.m_46467_();
        List<?> kept = this.bons$summonityCoo49Parts;
        if (DragonParts.usable(kept, this.bons$summonityCoo49PartsAt, now)) {
            DragonParts.REUSES.incrementAndGet();
            return kept;
        }
        List<?> fresh = original.call(self, level);
        this.bons$summonityCoo49Parts = fresh;
        this.bons$summonityCoo49PartsAt = now;
        return fresh;
    }

    @WrapOperation(method = "ensureBodyChain()V",
            at = @At(value = "INVOKE", target = "Lcom/fevzi/summonity/entity/SummonedStardustDragonEntity;getDragonParts(Lnet/minecraft/server/level/ServerLevel;)Ljava/util/List;"),
            require = 1, allow = 1)
    private List<?> bons$summonityCoo49PartsFresh(SummonedStardustDragonEntity self, ServerLevel level, Operation<List<?>> original) {
        List<?> fresh = original.call(self, level);
        if (DragonParts.enabled) {
            this.bons$summonityCoo49Parts = fresh;
            this.bons$summonityCoo49PartsAt = level.m_46467_();
        }
        return fresh;
    }

    @Inject(method = "spawnPart(II)V", at = @At("HEAD"), require = 1, allow = 1)
    private void bons$summonityCoo49PartSpawned(int partType, int index, CallbackInfo ci) {
        this.bons$summonityCoo49Parts = null;
    }
}
