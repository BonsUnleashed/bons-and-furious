package bons.furious.mixin.valkyrienskies_mod;

import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.util.Collections;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.joml.primitives.AABBdc;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.AcVsHotPaths;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

/**
 * valkyrien_entity_base_tick (Valkyrien Skies 2.4.11).
 *
 * VS's entity.MixinEntity runs two handlers for every entity every tick. onBaseTick (HEAD of baseTick) works out
 * whether the entity is in a sealed air pocket on a ship; when air pockets are disabled or the level has no ships
 * (AcVsHotPaths.skipSealedCheck) the answer is "not sealed", so the entity is marked not sealed, VS's position cache is
 * reset and the rest of the handler is skipped. afterCheckInside (TAIL of checkInsideBlocks) repeats the inside-block
 * check in every ship the entity touches; with no ships in the level it finds none. Both are MixinSquared injections
 * into VS's handlers, without a CallbackInfo per call.
 */
@Mixin(value = Entity.class, priority = 1500, remap = false)
public abstract class EntityBaseTickMixin {
    /**
     * The first statement of onBaseTick is "if (this.level != null && !this.isRemoved()) { ... }". When the check can
     * be skipped, this records the 1.0.19 result and hands the handler a null level, so it returns at that test.
     */
    @TargetHandler(mixin = "org.valkyrienskies.mod.mixin.entity.MixinEntity", name = "onBaseTick")
    @ModifyExpressionValue(method = "@MixinSquared:Handler",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Entity;f_19853_:Lnet/minecraft/world/level/Level;",
                    opcode = Opcodes.GETFIELD, ordinal = 0))
    private Level bons$skipSealedCheck(Level level) {
        if (AcVsHotPaths.skipSealedCheck(level)) {
            ((IEntityDraggingInformationProvider) this).vs$setInSealedArea(false);
            ((EntitySealedPosAccess) this).bons$setLastCheckedSealedPos(BlockPos.f_121853_);
            return null;
        }
        return level;
    }

    /** With no ships in the level, afterCheckInside's loop over the intersecting ships has nothing to visit. */
    @TargetHandler(mixin = "org.valkyrienskies.mod.mixin.entity.MixinEntity", name = "afterCheckInside")
    @Redirect(method = "@MixinSquared:Handler",
            at = @At(value = "INVOKE", target = "Lorg/valkyrienskies/mod/common/VSGameUtilsKt;getShipsIntersecting(Lnet/minecraft/world/level/Level;Lorg/joml/primitives/AABBdc;)Ljava/lang/Iterable;"))
    private Iterable<Ship> bons$shipsIntersectingUnlessNone(Level level, AABBdc boundingBox) {
        if (AcVsHotPaths.noShips(level)) {
            return Collections.emptyList();
        }
        return VSGameUtilsKt.getShipsIntersecting(level, boundingBox);
    }
}
