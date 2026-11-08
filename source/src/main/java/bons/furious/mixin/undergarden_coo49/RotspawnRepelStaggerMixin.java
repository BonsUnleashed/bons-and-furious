package bons.furious.mixin.undergarden_coo49;

import bons.furious.patch.undergarden_coo49.UndergardenScans;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import quek.undergarden.entity.monster.rotspawn.RotspawnMonster;

/**
 * undergarden_rotspawn_repel_stagger (The Undergarden 0.9.6 for NeoForge 1.21.1, trade-off, off by default; both sides): the
 * tickCount read of RotspawnMonster.aiStep's "tickCount % 40 == 0 && fleeTime == 0" test gets a fixed per-mob offset
 * (floorMod(id * 31, 40)), so a group spawned on the same tick no longer runs its repellent searches on one tick; each mob
 * still searches once per 40 ticks. What differs: a rotspawn checks for repellents up to 39 ticks earlier or later.
 */
@Mixin(value = RotspawnMonster.class, remap = false)
public abstract class RotspawnRepelStaggerMixin {
    @ModifyExpressionValue(method = "aiStep()V", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
            target = "Lquek/undergarden/entity/monster/rotspawn/RotspawnMonster;tickCount:I"), require = 1, allow = 1)
    private int bons$undergardenCoo49RepelStagger(int tickCount) {
        return UndergardenScans.staggeredTick(tickCount, (Entity) (Object) this);
    }
}
