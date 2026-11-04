package bons.furious.mixin.tact_coo49;

import bons.furious.patch.tact_coo49.CompendiumSaves;
import com.github.alexmodguy.alexscaves.server.misc.CaveBookProgress;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.telepathicgrunt.tact.CompendiumUnlock;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * tact_compendium_unlock_single_save (TACT 1.3.0 / 1.5.3 with Alex's Caves 2.0.2, server): playerLoggedIn is wrapped so
 * the saveCaveBookProgress calls inside it are held and the last one runs once at its end. See CompendiumSaves.
 */
@Mixin(value = CompendiumUnlock.class, remap = false)
public abstract class CompendiumSingleSaveMixin {
    @WrapMethod(method = "playerLoggedIn")
    private static void bons$saveOnce(PlayerEvent.PlayerLoggedInEvent event, Operation<Void> original) {
        CompendiumSaves.around(event, original);
    }

    @WrapOperation(method = "playerLoggedIn", at = @At(value = "INVOKE",
            target = "Lcom/github/alexmodguy/alexscaves/server/misc/CaveBookProgress;saveCaveBookProgress(Lcom/github/alexmodguy/alexscaves/server/misc/CaveBookProgress;Lnet/minecraft/world/entity/player/Player;)V"),
            require = 1, allow = 1)
    private static void bons$holdSave(CaveBookProgress progress, Player player, Operation<Void> save) {
        CompendiumSaves.save(progress, player, save);
    }
}
