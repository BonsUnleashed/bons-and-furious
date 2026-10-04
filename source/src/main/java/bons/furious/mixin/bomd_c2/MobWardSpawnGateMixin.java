package bons.furious.mixin.bomd_c2;

import bons.furious.patch.bomd_c2.BlockCachePresence;
import com.bawnorton.mixinsquared.TargetHandler;
import com.cerbon.bosses_of_mass_destruction.block.custom.MobWardBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * bomd_block_cache_presence (Bosses of Mass Destruction, LGPL-3.0; 1.21.1 tested build: BOMD 1.3.3 for NeoForge 1.21;
 * both sides), part 2 of 4.
 *
 * BOMD's NaturalSpawnerMixin.canSpawn handler (at the return of NaturalSpawner.isValidSpawnPostitionForType) calls
 * MobWardBlock.canSpawn, which walks 9 x 9 chunks of the level's block cache looking for a mob ward within 64 blocks
 * and only then turns the result to false. While the level's cache holds no mob ward at all (BlockCachePresence), that
 * walk can find nothing and leaves the result as it is, so the call is skipped; otherwise it runs unchanged.
 *
 * Ported to 1.21.1: unchanged; BOMD 1.3.3's handler (same name and descriptor) and MobWardBlock.canSpawn are the same code.
 */
@Mixin(value = NaturalSpawner.class, priority = 1500, remap = false)
public abstract class MobWardSpawnGateMixin {
    @TargetHandler(mixin = "com.cerbon.bosses_of_mass_destruction.mixin.NaturalSpawnerMixin", name = "canSpawn")
    @Redirect(method = "@MixinSquared:Handler", at = @At(value = "INVOKE",
            target = "Lcom/cerbon/bosses_of_mass_destruction/block/custom/MobWardBlock;canSpawn(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos$MutableBlockPos;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V"))
    private static void bons$wardScanUnlessNone(ServerLevel level, BlockPos.MutableBlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (BlockCachePresence.noMobWard(level)) return;
        MobWardBlock.canSpawn(level, pos, cir);
    }
}
