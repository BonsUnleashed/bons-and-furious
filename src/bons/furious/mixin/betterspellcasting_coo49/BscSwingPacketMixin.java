package bons.furious.mixin.betterspellcasting_coo49;

import bons.furious.patch.betterspellcasting_coo49.BscSpells;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * better_spellcasting_server_swing_only (Better Spellcasting 1.2.0, LGPL-2.1-or-later; client; gameplay fix, off by
 * default): MixinExtras @ModifyExpressionValue on the Level.isClientSide() test at the top of
 * AttackSpellHandler.onClientAttackEntity (the client half of an entity hit, whose only work is sending a SwingMessage):
 * while the switch is on the test reads false, so the handler returns before the send, as it does on a server level.
 * onLeftClickEmpty (swings at the air) keeps sending. See BscSpells. Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "net.kayn.better_spellcasting.event.AttackSpellHandler", remap = false)
public abstract class BscSwingPacketMixin {
    @ModifyExpressionValue(method = "onClientAttackEntity(Lnet/minecraftforge/event/entity/player/AttackEntityEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_5776_()Z"),
            require = 1, allow = 1)
    private static boolean bons$betterspellcastingCoo49HitSwingPacket(boolean clientSide) {
        return BscSpells.sendHitSwing(clientSide);
    }
}
