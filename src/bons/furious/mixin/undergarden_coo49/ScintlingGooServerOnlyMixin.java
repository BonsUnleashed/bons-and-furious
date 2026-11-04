package bons.furious.mixin.undergarden_coo49;

import bons.furious.patch.undergarden_coo49.UndergardenScans;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import quek.undergarden.entity.animal.Scintling;

/**
 * undergarden_scintling_goo_server_only (The Undergarden 0.8.14, fix, on by default; acts on the client): the
 * ForgeEventFactory.getMobGriefingEvent call in Scintling.aiStep (m_8107_) answers false on a client level without
 * posting the event, so the aiStep returns before its goo loop there and the client places no goo of its own (the goo
 * arrives with the server's block update). On a server level the original call runs. See UndergardenScans.
 */
@Mixin(value = Scintling.class, remap = false)
public abstract class ScintlingGooServerOnlyMixin {
    @WrapOperation(method = "m_8107_()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/event/ForgeEventFactory;getMobGriefingEvent(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;)Z"),
            require = 1, allow = 1)
    private boolean bons$undergardenCoo49GooServerOnly(Level level, Entity scintling, Operation<Boolean> original) {
        return UndergardenScans.gooGriefing(level, scintling, original);
    }
}
