package bons.furious.mixin.endinglibrary_coo49;

import bons.furious.patch.endinglibrary_coo49.CapFallbacks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * endinglibrary_cap_fallback_lazy (EndingLibrary 2.2.2 and EndingLibraryTweaked 2.2.1fix, both sides): the end of
 * CommonProxy's static initialiser hands its four static LazyOptionals to CapFallbacks, and the five capability getters
 * are wrapped: when the field still holds that optional and it holds the capability (CapFallbacks.present), the getter's
 * body runs with it and without building the unused orElse fallback, else the getter runs as shipped. MIT target: the
 * one-line getter bodies are repeated here; nothing else of EndingLibrary is carried. getCameraCap, whose return type is
 * EndingLibrary's own class, gets a cancellable HEAD inject instead of a wrapper (no EndingLibrary class is named in our
 * code: the build compiles without it); the getter's checkcast still applies to the answer at its return.
 */
@Mixin(targets = "com.mega.endinglib.proxy.CommonProxy", remap = false)
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class CommonProxyCapMixin {
    @Shadow
    public static LazyOptional PLAYER_CAP;
    @Shadow
    public static LazyOptional LIVING_CAP;
    @Shadow
    public static LazyOptional ENTITY_CAP;
    @Shadow
    public static LazyOptional TEXT_CAP;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void bons$endinglibrary_coo49Capture(CallbackInfo ci) {
        CapFallbacks.capture(PLAYER_CAP, LIVING_CAP, ENTITY_CAP, TEXT_CAP);
    }

    @Inject(method = "getCameraCap(Lnet/minecraft/world/entity/player/Player;)Lcom/mega/endinglib/common/capability/EndingLibraryPlayerCapability;",
            at = @At("HEAD"), cancellable = true)
    private static void bons$endinglibrary_coo49CameraCap(Player player, CallbackInfoReturnable cir) {
        Object c = CapFallbacks.present(PLAYER_CAP, 0);
        if (c == null) return;
        LazyOptional<Object> cap = player.getCapability((Capability<Object>) c);
        cir.setReturnValue(cap.orElseThrow(NullPointerException::new));
    }

    @WrapMethod(method = "getCameraCapOptional(Lnet/minecraft/world/entity/player/Player;)Lnet/minecraftforge/common/util/LazyOptional;")
    private static LazyOptional bons$endinglibrary_coo49CameraCapOptional(Player player, Operation<LazyOptional> original) {
        Object c = CapFallbacks.present(PLAYER_CAP, 0);
        return c == null ? original.call(player) : player.getCapability((Capability) c);
    }

    @WrapMethod(method = "getLivingCapOptional(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraftforge/common/util/LazyOptional;")
    private static LazyOptional bons$endinglibrary_coo49LivingCapOptional(LivingEntity entity, Operation<LazyOptional> original) {
        Object c = CapFallbacks.present(LIVING_CAP, 1);
        return c == null ? original.call(entity) : entity.getCapability((Capability) c);
    }

    @WrapMethod(method = "getEntityCapOptional(Lnet/minecraft/world/entity/Entity;)Lnet/minecraftforge/common/util/LazyOptional;")
    private static LazyOptional bons$endinglibrary_coo49EntityCapOptional(Entity entity, Operation<LazyOptional> original) {
        Object c = CapFallbacks.present(ENTITY_CAP, 2);
        return c == null ? original.call(entity) : entity.getCapability((Capability) c);
    }

    @WrapMethod(method = "getTextCapOptional(Lnet/minecraft/world/entity/Display$TextDisplay;)Lnet/minecraftforge/common/util/LazyOptional;")
    private static LazyOptional bons$endinglibrary_coo49TextCapOptional(Display.TextDisplay entity, Operation<LazyOptional> original) {
        Object c = CapFallbacks.present(TEXT_CAP, 3);
        return c == null ? original.call(entity) : entity.getCapability((Capability) c);
    }
}
