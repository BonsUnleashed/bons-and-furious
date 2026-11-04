package bons.furious.mixin.terracurio_coo49;

import bons.furious.patch.terracurio_coo49.TerraAttributeSnapshot;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Hashtable;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * terracurio_attribute_map_snapshot (Terra Curio 0.2.0, LGPL-3.0; both sides): ModAttributes.readJsonConfig's HEAD and
 * RETURN tell TerraAttributeSnapshot when the replacement table may change and when it is complete; MixinExtras
 * @WrapOperation on the Hashtable.get call of getCustomAttribute and of hasCustomAttribute answers from the published copy
 * (see TerraAttributeSnapshot for why the answers are identical). Our own logic only.
 */
@Mixin(targets = "org.confluence.terra_curio.misc.ModAttributes", remap = false)
public abstract class TerraAttributeMapSnapshotMixin {
    @Shadow
    @Final
    private static Hashtable<Attribute, Attribute> MAP;

    @Inject(method = "readJsonConfig()V", at = @At("HEAD"), require = 1, allow = 1, remap = false)
    private static void bons$terracurioCoo49SnapshotEnter(CallbackInfo ci) {
        TerraAttributeSnapshot.enter();
    }

    @Inject(method = "readJsonConfig()V", at = @At("RETURN"), require = 1, remap = false)
    private static void bons$terracurioCoo49SnapshotLeave(CallbackInfo ci) {
        TerraAttributeSnapshot.leave(MAP);
    }

    @WrapOperation(method = "getCustomAttribute(Lnet/minecraft/world/entity/ai/attributes/Attribute;)Lnet/minecraft/world/entity/ai/attributes/Attribute;",
            at = @At(value = "INVOKE", target = "Ljava/util/Hashtable;get(Ljava/lang/Object;)Ljava/lang/Object;"), require = 1, allow = 1, remap = false)
    private static Object bons$terracurioCoo49SnapshotCustom(Hashtable<?, ?> map, Object key, Operation<Object> original) {
        return TerraAttributeSnapshot.get(map, key, original);
    }

    @WrapOperation(method = "hasCustomAttribute(Lnet/minecraft/world/entity/ai/attributes/Attribute;)Z",
            at = @At(value = "INVOKE", target = "Ljava/util/Hashtable;get(Ljava/lang/Object;)Ljava/lang/Object;"), require = 1, allow = 1, remap = false)
    private static Object bons$terracurioCoo49SnapshotHas(Hashtable<?, ?> map, Object key, Operation<Object> original) {
        return TerraAttributeSnapshot.get(map, key, original);
    }
}
