package bons.furious.mixin.cucumber_coo49;

import bons.furious.patch.cucumber_coo49.CucumberTagFiles;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * cucumber_tag_file_failure_memo (fix; Cucumber 1.21.1-8.0.16 TagMapper; both sides). getItemForTag, where it is about to look
 * at config/cucumber-tags.json (after Almost Unified and its own map: its only FMLPaths.CONFIGDIR read): answers AIR at once
 * while the file is in the state whose read failed; its "could not read" error line (the method's only slf4j
 * Logger.error(String, Throwable)) remembers that state; reloadTagMappings forgets it. See CucumberTagFiles. No Cucumber code
 * is carried.
 */
@Mixin(targets = "com.blakebr0.cucumber.crafting.TagMapper", remap = false)
public abstract class CucumberTagFileMemoMixin {
    @Inject(method = "getItemForTag(Ljava/lang/String;)Lnet/minecraft/world/item/Item;",
            at = @At(value = "FIELD", target = "Lnet/neoforged/fml/loading/FMLPaths;CONFIGDIR:Lnet/neoforged/fml/loading/FMLPaths;", opcode = Opcodes.GETSTATIC),
            cancellable = true, require = 1, allow = 1)
    private static void bons$cucumberCoo49KnownFailure(String tagId, CallbackInfoReturnable<Item> cir) {
        if (CucumberTagFiles.knownFailure()) cir.setReturnValue(Items.AIR);
    }

    @WrapOperation(method = "getItemForTag(Ljava/lang/String;)Lnet/minecraft/world/item/Item;",
            at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Throwable;)V"), require = 1, allow = 1)
    private static void bons$cucumberCoo49ReadFailed(Logger logger, String message, Throwable error, Operation<Void> original) {
        CucumberTagFiles.readFailed();
        original.call(logger, message, error);
    }

    @Inject(method = "reloadTagMappings()V", at = @At("HEAD"), require = 1, allow = 1)
    private static void bons$cucumberCoo49Reload(CallbackInfo ci) {
        CucumberTagFiles.forget();
    }
}
