package bons.furious.mixin.bomd_c2;

import bons.furious.patch.bomd_c2.BlockCachePresence;
import com.bawnorton.mixinsquared.TargetHandler;
import com.cerbon.bosses_of_mass_destruction.block.custom.MonolithBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * bomd_block_cache_presence (Bosses of Mass Destruction, LGPL-3.0; 1.21.1 tested build: BOMD 1.3.3 for NeoForge 1.21;
 * both sides), part 3 of 4.
 *
 * BOMD's ExplosionMixin.Explosion handler (a @ModifyVariable on the radius at the head of ServerLevel.explode) calls
 * MonolithBlock.getExplosionPower, which walks 9 x 9 chunks of the level's block cache for a monolith within 64 blocks
 * and returns the power * 1.3 when it finds one, the power otherwise. While the level's cache holds no monolith at all
 * (BlockCachePresence), that walk returns the power, so the call is skipped; otherwise it runs unchanged. Nothing in
 * Explosion or ServerLevel.explode itself is touched: only the call inside BOMD's handler.
 *
 * Ported to 1.21.1: BOMD 1.3.3's handler now targets the 1.21 explode (with the two particle options and the sound
 * holder); the handler's own name and descriptor and its getExplosionPower call are unchanged, as is getExplosionPower.
 */
@Mixin(value = ServerLevel.class, priority = 1500, remap = false)
public abstract class MonolithExplosionGateMixin {
    @TargetHandler(mixin = "com.cerbon.bosses_of_mass_destruction.mixin.ExplosionMixin", name = "Explosion")
    @Redirect(method = "@MixinSquared:Handler", at = @At(value = "INVOKE",
            target = "Lcom/cerbon/bosses_of_mass_destruction/block/custom/MonolithBlock;getExplosionPower(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;F)F"))
    private float bons$monolithScanUnlessNone(ServerLevel level, BlockPos pos, float power) {
        if (BlockCachePresence.noMonolith(level)) return power;
        return MonolithBlock.getExplosionPower(level, pos, power);
    }
}
