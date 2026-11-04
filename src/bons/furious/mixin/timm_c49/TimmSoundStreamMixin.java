package bons.furious.mixin.timm_c49;

import bons.furious.patch.timm_c49.TimmStreamMusic;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * timm_stream_music (The Immersive Music Mod 1.1.0, client, since 1.0.49; kind fix). @Inject at the return of Sound's
 * constructor: a sound located in TIMM's songs (namespace "timm", path "music/...") gets its stream flag (f_119775_, what
 * shouldStream() returns) set; every other sound keeps the flag its definition gave it (see TimmStreamMusic). Decided once
 * per sound definition at resource load, so shouldStream() itself stays the shipped field read. Minecraft: no code carried.
 * The switch's guard lines name TIMM classes, so it is not applied in a pack without TIMM.
 */
@Mixin(value = Sound.class, remap = false)
public abstract class TimmSoundStreamMixin {
    @Shadow
    @Final
    private ResourceLocation f_119770_;

    @Shadow
    @Final
    @Mutable
    private boolean f_119775_;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bons$timmC49StreamSongs(CallbackInfo ci) {
        if (!this.f_119775_ && TimmStreamMusic.shouldStream(false, this.f_119770_)) this.f_119775_ = true;
    }
}
