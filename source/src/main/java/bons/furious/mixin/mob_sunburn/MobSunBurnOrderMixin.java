package bons.furious.mixin.mob_sunburn;

import bons.furious.patch.mob_sunburn.SunBurnOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * vanilla_sun_burn_deferred_wet_check (Minecraft 1.21.1 with NeoForge 21.1.252; both sides, acts on the server).
 *
 * Mob.isSunBurnTick computes "wet = isInWaterRainOrBubble() || isInPowderSnow || wasInPowderSnow" before
 * "light > 0.5 && roll && !wet && canSeeSky(eyePos)". For classes SunBurnOrder.movable accepts, while the mob's block-state
 * memo is already set (SunBurnOrder.memoSet), the first redirect answers false for isInWaterRainOrBubble without asking it
 * and remembers that in a byte of the mob; the second redirect, on the canSeeSky call (reached only when the light test,
 * the roll and the powder snow flags have passed), asks the question then and answers false when it is true. Same "and" of
 * the same conditions, the roll drawn exactly as before; only the question moves later, and it is not asked at all when an
 * earlier condition fails. Spawn 4.0.8's two injections into this method (HEAD: moonstone, TAIL: sunstone; checked on
 * the 1.21.1 jar) sit outside both call sites and run as before. No Minecraft code is carried.
 *
 * The byte: 0 = asked at vanilla's point (or not this method's business), 1 = moved, 2/3 = moved with the shadow answer
 * false/true taken at vanilla's point. The first redirect always runs before the second in one call (bytecode order, same
 * branch), so the second never sees a byte from an earlier call.
 *
 * Ported to 1.21.1: Mojang names; new memo condition: 1.21.1's isInBubbleColumn reads Entity.getInBlockState(), which
 * fills the inBlockState memo when it is empty, so the question is moved only while the memo is set (then it is a pure
 * read, as on 1.20.1). LivingEntity.baseTick asks isInWaterRainOrBubble every tick before aiStep, so a dry mob's memo is
 * set by the time isSunBurnTick runs; a wet mob (memo empty) keeps vanilla's order.
 */
@Mixin(value = Mob.class, remap = false)
public abstract class MobSunBurnOrderMixin {
    @Unique
    private byte bons$wetState;

    @Redirect(method = "isSunBurnTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;isInWaterRainOrBubble()Z"))
    private boolean bons$wetLater(Mob self) {
        if (SunBurnOrder.enabled && SunBurnOrder.movable(self.getClass()) && SunBurnOrder.memoSet(((EntityInBlockStateAccessor) self).bons$inBlockState())) {
            this.bons$wetState = SunBurnOrder.SHADOW ? (self.isInWaterRainOrBubble() ? (byte) 3 : (byte) 2) : (byte) 1;
            return false;
        }
        this.bons$wetState = 0;
        return self.isInWaterRainOrBubble();
    }

    @Redirect(method = "isSunBurnTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;canSeeSky(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean bons$skyAfterWet(Level level, BlockPos pos) {
        byte state = this.bons$wetState;
        if (state != 0) {
            this.bons$wetState = 0;
            boolean wet = ((Mob) (Object) this).isInWaterRainOrBubble();
            if (state >= 2) SunBurnOrder.shadow(this, state == 3, wet);
            if (wet) return false;
        }
        return level.canSeeSky(pos);
    }
}
