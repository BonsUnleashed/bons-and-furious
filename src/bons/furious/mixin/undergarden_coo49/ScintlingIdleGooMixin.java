package bons.furious.mixin.undergarden_coo49;

import bons.furious.patch.undergarden_coo49.ScintlingGooState;
import bons.furious.patch.undergarden_coo49.UndergardenScans;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import quek.undergarden.entity.animal.Scintling;

/**
 * undergarden_scintling_idle_goo_interval (The Undergarden 0.8.14, trade-off, off by default): the
 * ForgeEventFactory.getMobGriefingEvent call in Scintling.aiStep (m_8107_). While the scintling stands exactly where its
 * last goo attempt was and fewer than N ticks have passed, the call answers false without the event (aiStep then returns
 * before its goo loop); any movement attempts at once. What differs: goo mined from under a motionless scintling grows
 * back up to N - 1 ticks later, and listeners see fewer mob griefing events. The attempt state lives in @Unique fields.
 */
@Mixin(value = Scintling.class, remap = false)
public abstract class ScintlingIdleGooMixin implements ScintlingGooState {
    @Unique
    private boolean bons$undergardenCoo49Tried;
    @Unique
    private double bons$undergardenCoo49X;
    @Unique
    private double bons$undergardenCoo49Y;
    @Unique
    private double bons$undergardenCoo49Z;
    @Unique
    private int bons$undergardenCoo49Tick;

    @WrapOperation(method = "m_8107_()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/event/ForgeEventFactory;getMobGriefingEvent(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;)Z"),
            require = 1, allow = 1)
    private boolean bons$undergardenCoo49IdleGoo(Level level, Entity scintling, Operation<Boolean> original) {
        return UndergardenScans.idleGriefing(this, level, scintling, original);
    }

    @Override
    public boolean bons$undergardenCoo49GooTried() {
        return this.bons$undergardenCoo49Tried;
    }

    @Override
    public double bons$undergardenCoo49GooX() {
        return this.bons$undergardenCoo49X;
    }

    @Override
    public double bons$undergardenCoo49GooY() {
        return this.bons$undergardenCoo49Y;
    }

    @Override
    public double bons$undergardenCoo49GooZ() {
        return this.bons$undergardenCoo49Z;
    }

    @Override
    public int bons$undergardenCoo49GooTick() {
        return this.bons$undergardenCoo49Tick;
    }

    @Override
    public void bons$undergardenCoo49GooAttempt(double x, double y, double z, int tick) {
        this.bons$undergardenCoo49Tried = true;
        this.bons$undergardenCoo49X = x;
        this.bons$undergardenCoo49Y = y;
        this.bons$undergardenCoo49Z = z;
        this.bons$undergardenCoo49Tick = tick;
    }
}
