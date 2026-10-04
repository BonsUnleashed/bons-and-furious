package bons.furious.mixin.embeddium_sprites;

import bons.furious.patch.embeddium_sprites.SpriteFrameTimes;
import com.bawnorton.mixinsquared.TargetHandler;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.embeddedt.embeddium.impl.Embeddium;
import org.embeddedt.embeddium.impl.render.texture.SpriteContentsExtended;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * embeddium_sprite_tick_frame_times (Embeddium 1.0.15+mc1.21.1 on Minecraft 1.21.1 / NeoForge 21.1.252, client only).
 *
 * SpriteContents$Ticker.tickAndUpload runs once per client tick for every animated sprite of every atlas. Embeddium's
 * SpriteContentsAnimatorImplMixin.preTick (merged at its HEAD, cancellable) handles the sprites that were not drawn since
 * the previous tick: ++subFrame; if (subFrame >= frames.get(frame).time) { frame = (frame + 1) % frames.size();
 * subFrame = 0; } then it cancels tickAndUpload. This injection sits at the head of that handler (MixinSquared) and does
 * exactly the same for the same sprites (same condition: Embeddium's "animate only visible textures" option on and the
 * sprite not active), but takes the frame time and the frame count from an int[] copied once from the ticker's immutable
 * frames list (SpriteFrameTimes) instead of walking AnimatedTexture -> list -> array -> FrameInfo, four dependent objects
 * that are cold in cache on every tick; then it cancels tickAndUpload (tickCi, as Embeddium does) and returns from the
 * handler. Same fields read and written, same order, same values, same cancellation (the TAIL postTick is skipped as by
 * Embeddium's own cancel). Everything else returns at once and Embeddium's handler and the vanilla body run as shipped:
 * active sprites, the option off, a list that was not copied (unknown list class, empty, null entries) and any frame index
 * outside the list (which keeps Embeddium's own exception). The owner sprite is the constructor's first argument, kept by
 * bons$rememberOwner at the constructor's RETURN exactly as Embeddium's assignParent keeps its 'parent'. The frames list
 * and FrameInfo.time are final, and the list classes accepted are immutable, so the copy cannot go stale.
 *
 * Inside Embeddium's handler rather than at tickAndUpload's own head: tickAndUpload then keeps a single handler call that is
 * taken for every sprite, so the JIT still inlines Embeddium's handler on the active-sprite path and both CallbackInfo
 * objects stay scalar-replaced (an extra head callback left that call at the visible-sprite frequency, below C2's inlining
 * threshold, and cost a 24-byte CallbackInfo per visible sprite per tick). Priority 1500: applied after Embeddium's mixin,
 * whose handler must already be merged (Embeddium 1.0.15's config plugin lists that mixin dynamically, from its
 * features.textures.animations rule, default on; the ordering by priority is the same). require = 0, expect = 0: should
 * Embeddium's tracking mixin not be applied (that rule turned off), there is no handler to sit in and the ticker keeps only
 * the owner field. Embeddium's handler is LGPL-3.0; this restates its frame advance (licence note in
 * patches/embeddium_sprites.json).
 *
 * Ported to 1.21.1: Embeddium's classes moved to org.embeddedt.embeddium.impl (handler mixin
 * ...impl.mixin.features.textures.animations.tracking.SpriteContentsAnimatorImplMixin, options holder Embeddium.options()
 * in place of SodiumClientMod.options()); vanilla 1.21.1's Ticker has no this$0 field any more, so the owner sprite is
 * captured from the constructor (the two package-private parameter types are taken as @Coerce Object).
 */
@Mixin(targets = "net.minecraft.client.renderer.texture.SpriteContents$Ticker", remap = false, priority = 1500)
public abstract class SpriteTickerFrameTimesMixin {
    @Shadow
    int frame;

    @Shadow
    int subFrame;

    /** The sprite whose ticker this is: the constructor's first argument, the object Embeddium keeps as its 'parent'. */
    @Unique
    private SpriteContents bons$owner;

    /** The ticker's frame times, copied on its first invisible tick; SpriteFrameTimes.NONE when not copied. */
    @Unique
    private int[] bons$frameTimes;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bons$rememberOwner(SpriteContents owner, @Coerce Object animation, @Coerce Object interpolation, CallbackInfo ci) {
        this.bons$owner = owner;
    }

    @TargetHandler(mixin = "org.embeddedt.embeddium.impl.mixin.features.textures.animations.tracking.SpriteContentsAnimatorImplMixin", name = "preTick")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void bons$advanceInvisible(CallbackInfo tickCi, CallbackInfo ci) {
        if (!SpriteFrameTimes.enabled) return;
        SpriteContentsExtended sprite = (SpriteContentsExtended) (Object) this.bons$owner;
        if (!Embeddium.options().performance.animateOnlyVisibleTextures || sprite.sodium$isActive()) return;
        int[] times = this.bons$frameTimes;
        if (times == null) this.bons$frameTimes = times = SpriteFrameTimes.snapshot(this);
        int frame = this.frame;
        if (frame < 0 || frame >= times.length) return;
        if (SpriteFrameTimes.SHADOW) {
            SpriteFrameTimes.shadowCheck(this, frame, times);
            return;
        }
        int subFrame = this.subFrame + 1;
        this.subFrame = subFrame;
        if (subFrame >= times[frame]) {
            this.frame = (frame + 1) % times.length;
            this.subFrame = 0;
        }
        tickCi.cancel();
        ci.cancel();
    }
}
