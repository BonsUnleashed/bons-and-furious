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
 * vanilla_sun_burn_deferred_wet_check (Minecraft 1.20.1 on Forge 47.4.16; both sides, acts on the server).
 *
 * Mob.isSunBurnTick (m_21527_) computes "wet = isInWaterRainOrBubble() || isInPowderSnow || wasInPowderSnow" before
 * "light > 0.5 && roll && !wet && canSeeSky(eyePos)". For classes SunBurnOrder.movable accepts, the first redirect answers
 * false for isInWaterRainOrBubble without asking it and remembers that in a byte of the mob; the second redirect, on the
 * canSeeSky call (reached only when the light test, the roll and the powder snow flags have passed), asks the question then
 * and answers false when it is true. Same "and" of the same conditions, the roll drawn exactly as before; only the question
 * moves later, and it is not asked at all when an earlier condition fails. Spawn 4.0.7's two injections into this method
 * (HEAD: moonstone, TAIL: sunstone) sit outside both call sites and run as before. No Minecraft code is carried.
 *
 * The byte: 0 = asked at vanilla's point (or not this method's business), 1 = moved, 2/3 = moved with the shadow answer
 * false/true taken at vanilla's point. The first redirect always runs before the second in one call (bytecode order, same
 * branch), so the second never sees a byte from an earlier call.
 */
@Mixin(value = Mob.class, remap = false)
public abstract class MobSunBurnOrderMixin {
    @Unique
    private byte bons$wetState;

    @Redirect(method = "m_21527_", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;m_20071_()Z"))
    private boolean bons$wetLater(Mob self) {
        if (SunBurnOrder.enabled && SunBurnOrder.movable(self.getClass())) {
            this.bons$wetState = SunBurnOrder.SHADOW ? (self.m_20071_() ? (byte) 3 : (byte) 2) : (byte) 1;
            return false;
        }
        this.bons$wetState = 0;
        return self.m_20071_();
    }

    @Redirect(method = "m_21527_", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_45527_(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean bons$skyAfterWet(Level level, BlockPos pos) {
        byte state = this.bons$wetState;
        if (state != 0) {
            this.bons$wetState = 0;
            boolean wet = ((Mob) (Object) this).m_20071_();
            if (state >= 2) SunBurnOrder.shadow(this, state == 3, wet);
            if (wet) return false;
        }
        return level.m_45527_(pos);
    }
}
