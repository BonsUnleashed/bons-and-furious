package bons.furious.mixin.cerbonsapi_mop49;

import bons.furious.patch.cerbonsapi_mop49.MultipartHooks;
import com.cerbon.cerbons_api.api.multipart_entities.entity.MultipartAwareEntity;
import com.cerbon.cerbons_api.api.multipart_entities.entity.MultipartEntity;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * cerbonsapi_multipart_hooks_lean (Cerbon's API; 1.21.1 tested build: Cerbon's API 1.3.0 for NeoForge 1.21; both sides;
 * since 1.0.49). With this switch Cerbon's API's own EntityMixin (com.cerbon.cerbons_api.mixin.multipart_entities.EntityMixin)
 * is left out (its "cancel" line) and these two hooks make its two calls:
 *  - getBoundingBox (one return): Cerbon's RETURN inject replaces the box of a MultipartEntity with
 *    getCompoundBoundingBox(box) through a CallbackInfoReturnable built on every call; here a @ModifyReturnValue does the
 *    same without the callback object.
 *  - setPosRaw TAIL: Cerbon's inject calls onSetPos(x, y, z) on a MultipartAwareEntity; the same here.
 * Both ask the entity's kind (MultipartHooks.kindOf: the same two instanceof tests) once and keep it in the entity: the
 * class of an entity object never changes, so the kept kind is always the answer the tests give. Runtime flag off = the
 * instanceof tests on every call (Cerbon's own logic, still without the callback object).
 *
 * Ported to 1.21.1: Cerbon's API 1.3.0 has the same two handlers (getBoundingBox RETURN with a cancellable
 * CallbackInfoReturnable, setPosRaw TAIL) on the same interfaces; 1.21.1's Entity.getBoundingBox still returns this.bb and
 * setPosRaw still ends in one return.
 */
@Mixin(value = Entity.class, remap = false)
public abstract class EntityMultipartHooksMixin {
    @Unique
    private byte bons$cerbonsapiMop49Kind;

    @Unique
    private byte bons$cerbonsapiMop49KindOf() {
        byte k = this.bons$cerbonsapiMop49Kind;
        if (k != MultipartHooks.UNKNOWN && MultipartHooks.enabled) return k;
        k = MultipartHooks.kindOf(this);
        this.bons$cerbonsapiMop49Kind = k;
        return k;
    }

    @ModifyReturnValue(method = "getBoundingBox()Lnet/minecraft/world/phys/AABB;", at = @At("RETURN"))
    private AABB bons$cerbonsapiMop49Box(AABB box) {
        return this.bons$cerbonsapiMop49KindOf() >= MultipartHooks.MULTIPART ? ((MultipartEntity) (Object) this).getCompoundBoundingBox(box) : box;
    }

    @Inject(method = "setPosRaw(DDD)V", at = @At("TAIL"))
    private void bons$cerbonsapiMop49Pos(double x, double y, double z, CallbackInfo ci) {
        if (this.bons$cerbonsapiMop49KindOf() == MultipartHooks.AWARE) ((MultipartAwareEntity) (Object) this).onSetPos(x, y, z);
    }
}
