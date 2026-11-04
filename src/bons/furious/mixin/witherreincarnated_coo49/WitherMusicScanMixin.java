package bons.furious.mixin.witherreincarnated_coo49;

import bons.furious.patch.witherreincarnated_coo49.WrWitherScans;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * witherreincarnated_wither_scan_presence_gate (Wither Reincarnated 1.0.5, All Rights Reserved; client): MixinExtras
 * @WrapOperation on the ClientLevel.entitiesForRendering (m_104735_) call in HandleWitherMusicEvent.tickMusic: while
 * vanilla_entity_class_count_layer says the client level holds no WitherBoss, the loop (which acts on WitherBoss
 * instances only) gets an empty list. See WrWitherScans. Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "com.alexander.witherreincarnated.events.HandleWitherMusicEvent", remap = false)
public abstract class WitherMusicScanMixin {
    @WrapOperation(method = "tickMusic(Lnet/minecraftforge/event/TickEvent$ClientTickEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;m_104735_()Ljava/lang/Iterable;"),
            require = 1, allow = 1)
    private static Iterable<Entity> bons$witherreincarnatedCoo49MusicScan(ClientLevel level, Operation<Iterable<Entity>> original) {
        return WrWitherScans.musicEntities(level, original);
    }
}
