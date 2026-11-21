package bons.furious.mixin.assetsprotector_decrypt;

import bons.furious.patch.assetsprotector_decrypt.Undecryptable;
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.regex.Pattern;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * assetsprotector_undecryptable_skip (Assets Protector 1.0.0, GPL-3.0; both sides): MixinSquared @TargetHandler @WrapOperation
 * on the Pattern.compile call inside Assets Protector's MixinResource.open handler (merged into Resource.open): its one
 * Base64 expression is compiled once (see Undecryptable). No Assets Protector code is carried: our own logic only.
 */
@Mixin(value = Resource.class, priority = 1500, remap = false)
public abstract class AssetsProtectorPatternMixin {
    @TargetHandler(mixin = "com.min01.aes.mixin.MixinResource", name = "open")
    @WrapOperation(method = "@MixinSquared:Handler", require = 0, expect = 0, at = @At(value = "INVOKE",
            target = "Ljava/util/regex/Pattern;compile(Ljava/lang/String;)Ljava/util/regex/Pattern;"))
    private Pattern bons$apTailPattern(String regex, Operation<Pattern> original) {
        return Undecryptable.pattern(regex, original);
    }
}
