package bons.furious.mixin.revelationfix_coo49;

import bons.furious.patch.revelationfix_coo49.RfOutline;
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.ints.Int2CharOpenHashMap;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * revelationfix_outline_precheck (RevelationFix 4.8 and 4.7 with EndingLib 2.2.2, client; RevelationFix is all rights
 * reserved, EndingLib MIT: no code of either is carried). A MixinSquared @WrapOperation inside RevelationFix's
 * gr.FontMixin.drawOutLine handler (merged into Font.drawInBatch(FormattedCharSequence, ...) around its drawInternal
 * call) on its TextColorUtils.getColorChars(sequence) call: RfOutline.colorChars hands over an empty map when no
 * character of the sequence carries RevelationFix's APOLLYON or EDEN colour, and a map equal to getColorChars' otherwise,
 * filled in the same pass (RfOutline explains why the handler draws exactly as before). Priority 1500: applied after
 * RevelationFix's mixin (default mixin priority 1000). require = 0 and expect = 0: where the handler is not merged
 * (RevelationFix absent, or Modern UI installed, for which RevelationFix leaves this mixin out) nothing happens; the
 * switch's guard lines pin the handler and the group's proof checks the merged handler calls this wrapper.
 */
@Mixin(value = Font.class, priority = 1500, remap = false)
public abstract class RfOutlineScanMixin {
    @TargetHandler(mixin = "com.mega.revelationfix.mixin.gr.FontMixin", name = "drawOutLine")
    @WrapOperation(method = "@MixinSquared:Handler",
            at = @At(value = "INVOKE", target = "Lcom/mega/endinglib/api/client/text/TextColorUtils;getColorChars(Lnet/minecraft/util/FormattedCharSequence;)Lit/unimi/dsi/fastutil/ints/Int2CharOpenHashMap;"),
            require = 0, expect = 0, allow = 1)
    private Int2CharOpenHashMap bons$revelationfixOutlinePrecheck(FormattedCharSequence sequence, Operation<Int2CharOpenHashMap> original) {
        return RfOutline.colorChars(sequence, original);
    }
}
