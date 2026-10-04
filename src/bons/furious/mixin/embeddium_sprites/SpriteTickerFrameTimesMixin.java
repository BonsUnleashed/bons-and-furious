package bons.furious.mixin.embeddium_sprites;

import bons.furious.patch.embeddium_sprites.SpriteFrameTimes;
import com.bawnorton.mixinsquared.TargetHandler;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.render.texture.SpriteContentsExtended;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * embeddium_sprite_tick_frame_times (Embeddium 0.3.31+mc1.20.1 on Minecraft 1.20.1 / Forge 47.4.16, client only).
 *
 * SpriteContents$Ticker.tickAndUpload (m_247697_) runs once per client tick for every animated sprite of every atlas.
 * Embeddium's SpriteContentsAnimatorImplMixin.preTick (merged at its HEAD, cancellable) handles the sprites that were not
 * drawn since the previous tick: ++subFrame; if (subFrame >= frames.get(frame).time) { frame = (frame + 1) %
 * frames.size(); subFrame = 0; } then it cancels tickAndUpload. This injection sits at the head of that handler (MixinSquared)
 * and does exactly the same for the same sprites (same condition: Embeddium's "animate only visible textures" option on and
 * the sprite not active), but takes the frame time and the frame count from an int[] copied once from the ticker's
 * immutable frames list (SpriteFrameTimes) instead of walking AnimatedTexture -> list -> array -> FrameInfo, four dependent
 * objects that are cold in cache on every tick; then it cancels tickAndUpload (tickCi, as Embeddium does) and returns from
 * the handler. Same fields read and written, same order, same values, same cancellation (the TAIL postTick is skipped as
 * by Embeddium's own cancel). Everything else returns at once and Embeddium's handler and the vanilla body run as shipped:
 * active sprites, the option off, a list that was not copied (unknown list class, empty, null entries) and any frame index
 * outside the list (which keeps Embeddium's own exception). f_243791_ (the ticker's outer SpriteContents) is the object
 * Embeddium stores as its parent: both are the constructor's first argument. The frames list and FrameInfo.time are final,
 * and the list classes accepted are immutable, so the copy cannot go stale.
 *
 * Inside Embeddium's handler rather than at tickAndUpload's own head: tickAndUpload then keeps a single handler call that is
 * taken for every sprite, so the JIT still inlines Embeddium's handler on the active-sprite path and both CallbackInfo
 * objects stay scalar-replaced (an extra head callback left that call at the visible-sprite frequency, below C2's inlining
 * threshold, and cost a 24-byte CallbackInfo per visible sprite per tick). Priority 1500: applied after Embeddium's mixin,
 * whose handler must already be merged. require = 0, expect = 0: should Embeddium's tracking mixin ever not be applied (its
 * own config can drop mixins), there is no handler to sit in and the ticker stays exactly as shipped instead of failing the
 * class (offline apply without Embeddium's mixin: the ticker transforms cleanly with nothing of ours in it).
 * Embeddium's handler is LGPL-3.0; this restates its frame advance (licence note in patches/embeddium_sprites.json).
 */
@Mixin(targets = "net.minecraft.client.renderer.texture.SpriteContents$Ticker", remap = false, priority = 1500)
public abstract class SpriteTickerFrameTimesMixin {
    @Shadow
    int f_244631_;          // frame

    @Shadow
    int f_244511_;          // subFrame

    @Shadow
    @Final
    SpriteContents f_243791_;   // the sprite whose ticker this is (Embeddium's 'parent')

    /** The ticker's frame times, copied on its first invisible tick; SpriteFrameTimes.NONE when not copied. */
    @Unique
    private int[] bons$frameTimes;

    @TargetHandler(mixin = "me.jellysquid.mods.sodium.mixin.features.textures.animations.tracking.SpriteContentsAnimatorImplMixin", name = "preTick")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void bons$advanceInvisible(CallbackInfo tickCi, CallbackInfo ci) {
        if (!SpriteFrameTimes.enabled) return;
        SpriteContentsExtended sprite = (SpriteContentsExtended) this.f_243791_;
        if (!SodiumClientMod.options().performance.animateOnlyVisibleTextures || sprite.sodium$isActive()) return;
        int[] times = this.bons$frameTimes;
        if (times == null) this.bons$frameTimes = times = SpriteFrameTimes.snapshot(this);
        int frame = this.f_244631_;
        if (frame < 0 || frame >= times.length) return;
        if (SpriteFrameTimes.SHADOW) {
            SpriteFrameTimes.shadowCheck(this, frame, times);
            return;
        }
        int subFrame = this.f_244511_ + 1;
        this.f_244511_ = subFrame;
        if (subFrame >= times[frame]) {
            this.f_244631_ = (frame + 1) % times.length;
            this.f_244511_ = 0;
        }
        tickCi.cancel();
        ci.cancel();
    }
}
