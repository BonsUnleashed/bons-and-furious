package bons.furious.mixin.valkyrienskies_core;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.Comparator;
import java.util.List;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.internal.collision.VsiConvexPolygonc;
import org.valkyrienskies.mod.common.util.AcVsAxes;
import org.valkyrienskies.mod.common.util.AcVsSweep2;

/**
 * valkyrien_collision_axes (Valkyrien Skies 2.4.11, VS core EntityPolygonColliderImpl, obfuscated as Dk).
 *
 * Entity-versus-ship collision runs a separating-axis test against every ship polygon near the entity. collide
 * (Dk.a(AABBdc, List, Vector3dc, double, Vector3dc)) and canStep (Dk.a(AABBdc, List, double)) sorted the polygons
 * with a boxing Kotlin comparator and rebuilt the candidate axis list for every polygon. Now each call sorts with
 * AcVsSweep2.sortByDistance (same key, same order, ties kept in input order) and keeps one AcVsAxes for the call
 * (only when there are four or more polygons) that reuses the axis list while consecutive polygons have the same
 * normals. The axes, the order and every collision result stay the same.
 *
 * The axis cache must live exactly as long as one call (collide calls canStep in its loop, and each has its own), so
 * it is a MixinExtras @Share local; MixinExtras allocates one small holder per call for it.
 */
@Mixin(targets = "org.valkyrienskies.core.impl.shadow.Dk", remap = false)
public abstract class EntityPolygonColliderMixin {
    /** The unit axes X, Y, Z (UNIT_NORMALS): the fixed part of every candidate axis list. */
    @Shadow
    @Final
    private static Vector3dc[] e;

    /** First thing in collide and canStep: this call's axis cache, or none when there are fewer than four polygons. */
    @ModifyVariable(method = {
            "a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",
            "a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z"},
            at = @At("HEAD"), argsOnly = true, require = 2)
    private List<? extends VsiConvexPolygonc> bons$startAxisCache(List<? extends VsiConvexPolygonc> polygons,
                                                                   @Share("axes") LocalRef<AcVsAxes> axes) {
        axes.set(AcVsAxes.forCandidates(polygons.size()));
        return polygons;
    }

    /**
     * Sorts the polygons by the signed distance of their centre to the entity's feet box without boxing, instead of
     * Kotlin's sortedWith. The feet box is the one the Kotlin comparator was built with (see DistanceComparatorAccessor).
     */
    @Redirect(method = {
            "a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",
            "a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z"},
            at = @At(value = "INVOKE", target = "Lkotlin/collections/CollectionsKt;sortedWith(Ljava/lang/Iterable;Ljava/util/Comparator;)Ljava/util/List;"),
            require = 2)
    private List<VsiConvexPolygonc> bons$sortByDistance(Iterable<? extends VsiConvexPolygonc> polygons, Comparator<?> byDistance) {
        return AcVsSweep2.sortByDistance(polygons, ((DistanceComparatorAccessor) byDistance).bons$feetBox());
    }

    /** One polygon's candidate axes (generateAllNormals), served from this call's cache when the normals repeat. */
    @Redirect(method = {
            "a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",
            "a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z"},
            at = @At(value = "INVOKE", target = "Lorg/valkyrienskies/core/impl/shadow/Dk;a(Ljava/lang/Iterable;)Ljava/util/List;"),
            require = 3)
    private List<Vector3dc> bons$candidateAxes(Iterable<? extends Vector3dc> normals, @Share("axes") LocalRef<AcVsAxes> axes) {
        return AcVsAxes.axes(normals, axes.get(), e);
    }
}
