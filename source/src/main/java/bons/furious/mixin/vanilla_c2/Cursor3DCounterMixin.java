package bons.furious.mixin.vanilla_c2;

import bons.furious.patch.vanilla_c2.CursorCounters;
import net.minecraft.core.Cursor3D;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * vanilla_cursor_counters (Minecraft 1.21.1, both sides; tested on Minecraft 1.21.1 with NeoForge 21.1.252, which does
 * not patch Cursor3D). The constructor decides the mode once (CursorCounters.counterMode: three positive sizes, cell count
 * without int overflow); in counter mode advance() is answered at its head by stepping x and carrying into y and z, leaving
 * the same x, y, z and index the original's divisions would; in any other mode the injection returns at once and the
 * original advance runs in place. Only our own logic is carried; no other target mod mixes into Cursor3D (Spawn 4.0.8's
 * client ClientLevelMixin only constructs and reads cursors). See CursorCounters.
 *
 * Ported to 1.21.1: Mojang names only; Cursor3D's fields, constructor, advance, nextX/Y/Z and getNextType are unchanged.
 */
@Mixin(value = Cursor3D.class, remap = false)
public abstract class Cursor3DCounterMixin {
    @Shadow
    @Final
    private int width;
    @Shadow
    @Final
    private int height;
    @Shadow
    @Final
    private int depth;
    @Shadow
    @Final
    private int end;   // cell count
    @Shadow
    private int index;
    @Shadow
    private int x;
    @Shadow
    private int y;
    @Shadow
    private int z;
    @Unique
    private boolean bons$counter;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bons$counterMode(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, CallbackInfo ci) {
        this.bons$counter = CursorCounters.counterMode(this.width, this.height, this.depth, this.end);
    }

    @Inject(method = "advance", at = @At("HEAD"), cancellable = true)
    private void bons$advance(CallbackInfoReturnable<Boolean> cir) {
        if (!this.bons$counter) return;
        int index = this.index;
        if (index == this.end) {
            cir.setReturnValue(false);
            return;
        }
        // the cell of `index`: (0, 0, 0) for the first, otherwise the previous cell plus one in x, carrying into y and z
        if (index != 0 && ++this.x == this.width) {
            this.x = 0;
            if (++this.y == this.height) {
                this.y = 0;
                ++this.z;
            }
        }
        this.index = index + 1;
        cir.setReturnValue(true);
    }
}
