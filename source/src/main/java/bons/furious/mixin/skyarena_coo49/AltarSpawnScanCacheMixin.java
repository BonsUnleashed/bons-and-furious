package bons.furious.mixin.skyarena_coo49;

import bons.furious.patch.skyarena_coo49.SkyarenaCoo49MemoHolder;
import bons.furious.patch.skyarena_coo49.SkyarenaSpawnCache;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.jrdemiurge.skyarena.block.entity.AltarBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * skyarena_spawn_scan_cache_ticks (Sky Arena 1.3.6-1.21.1, MIT; server; trade-off, off by default): MixinExtras
 * @WrapOperation on the findValidSpawnPositions call in AltarBlockEntity.updateSummonedMobs (the battle tick's stray-mob
 * pull-back): the altar answers from its kept sweep for the configured number of ticks (SkyarenaSpawnCache: the block
 * tests are kept, the player test is applied fresh on every call). The kept sweep lives in a field of the block entity
 * (this mixin). The wave start (AltarBlock.use, reached through useItemOn and the auto wave) is not touched. Our own
 * logic only.
 */
@Mixin(targets = "net.jrdemiurge.skyarena.block.entity.AltarBlockEntity", remap = false)
public abstract class AltarSpawnScanCacheMixin implements SkyarenaCoo49MemoHolder {
    @Unique
    private Object bons$skyarenaCoo49MemoField;

    @Override
    public Object bons$skyarenaCoo49Memo() {
        return this.bons$skyarenaCoo49MemoField;
    }

    @Override
    public void bons$skyarenaCoo49SetMemo(Object memo) {
        this.bons$skyarenaCoo49MemoField = memo;
    }

    @WrapOperation(method = "updateSummonedMobs()V",
            at = @At(value = "INVOKE", target = "Lnet/jrdemiurge/skyarena/block/entity/AltarBlockEntity;findValidSpawnPositions(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)Ljava/util/List;"),
            require = 1, allow = 1)
    private List<BlockPos> bons$skyarenaCoo49Cached(AltarBlockEntity self, Level level, BlockPos center, Player player,
                                                     Operation<List<BlockPos>> original) {
        return SkyarenaSpawnCache.answer(self, level, center, player, original);
    }
}
