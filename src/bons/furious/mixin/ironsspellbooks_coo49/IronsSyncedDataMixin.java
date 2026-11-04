package bons.furious.mixin.ironsspellbooks_coo49;

import bons.furious.patch.ironsspellbooks_coo49.IronsSyncedData;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
import java.util.HashMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * irons_spellbooks_synced_data_session_clear (Iron's Spells 'n Spellbooks 1.20.1-3.16.3 / 3.15.6, All Rights Reserved;
 * client): @Inject at the RETURN of ClientMagicData's static initializer hands its private static player synced-data map to
 * IronsSyncedData, whose login / logout listeners empty it at session edges. Nothing of the class is changed.
 */
@Mixin(targets = "io.redspace.ironsspellbooks.player.ClientMagicData", remap = false)
public abstract class IronsSyncedDataMixin {
    @Shadow
    @Final
    private static HashMap<Integer, SyncedSpellData> playerSyncedDataLookup;

    @Inject(method = "<clinit>", at = @At("RETURN"), require = 1, allow = 1)
    private static void bons$ironsspellbooksCoo49SyncedDataTrack(CallbackInfo ci) {
        IronsSyncedData.track(playerSyncedDataLookup);
    }
}
