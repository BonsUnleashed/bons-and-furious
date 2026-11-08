package bons.furious.mixin.balm_c49;

import bons.furious.patch.balm_c49.BalmMemos;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * balm_config_identifier_memo (Balm 21.0.67 for NeoForge 1.21.1, both sides): MixinExtras @WrapMethod on
 * AbstractBalmConfig.getActiveConfig(Class) answers a present config from activeReflectionConfigs.get(identifier) with the
 * identifier from a ClassValue; otherwise Balm's method runs (BalmMemos). Injector only (Balm is All Rights Reserved).
 */
@Mixin(targets = "net.blay09.mods.balm.common.config.AbstractBalmConfig", remap = false)
public abstract class BalmConfigIdentifierMixin {
    @Shadow
    @Final
    private Map<ResourceLocation, Object> activeReflectionConfigs;

    @WrapMethod(method = "getActiveConfig(Ljava/lang/Class;)Ljava/lang/Object;")
    private Object bons$balmActive(Class<?> type, Operation<Object> original) {
        return BalmMemos.activeConfig(this.activeReflectionConfigs, type, original);
    }
}
