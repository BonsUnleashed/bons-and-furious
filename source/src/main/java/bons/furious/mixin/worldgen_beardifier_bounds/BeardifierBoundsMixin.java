package bons.furious.mixin.worldgen_beardifier_bounds;

import bons.furious.patch.worldgen_beardifier_bounds.BeardifierBounds;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_beardifier_influence_bounds (Minecraft 1.21.1 world generation, server side; tested build NeoForge 21.1.252):
 * inside Beardifier.compute, every read of the rigid and junction iterator fields returns a view over only the terms
 * whose influence box holds the block (see BeardifierBounds for the boxes and why the sum is bit-identical). The loops
 * and any other mod's handlers run unchanged; no injection cancels. Decided once per block position: every read at the
 * same position returns the same view (the loops' own next/back calls move it), so a switch flip never splits one
 * compute call.
 *
 * Minecraft is no-copy: the handlers carry only our own logic; vanilla's term methods are only called (shadow check).
 *
 * Ported to 1.21.1: getBuryContribution takes three doubles (BURY passes dy / 2.0 itself, ENCAPSULATE passes all three
 * distances halved), so the shadowed method and Kernels.bons$bury take doubles. compute is selected by its descriptor.
 * The @Local ints 0, 1, 2 at the six iterator-field reads are still blockX, blockY, blockZ (the loop bodies' own int
 * locals are not live at the loop heads, at next() or at back()).
 */
@Mixin(value = Beardifier.class, remap = false)
public abstract class BeardifierBoundsMixin implements BeardifierBounds.Kernels {
    @Shadow
    @Final
    protected ObjectListIterator<Beardifier.Rigid> pieceIterator;
    @Shadow
    @Final
    protected ObjectListIterator<JigsawJunction> junctionIterator;

    @Unique
    private BeardifierBounds.Plan bons$plan;
    @Unique
    private BeardifierBounds.View bons$rigidView, bons$junctionView;
    @Unique
    private boolean bons$filtering;
    @Unique
    private int bons$px = Integer.MIN_VALUE, bons$py = Integer.MIN_VALUE, bons$pz = Integer.MIN_VALUE;

    @Shadow
    protected static double getBuryContribution(double dx, double dy, double dz) {
        throw new AssertionError();
    }

    @Shadow
    protected static double getBeardContribution(int dx, int dy, int dz, int yToGround) {
        throw new AssertionError();
    }

    /** The rigid piece list compute walks: a view for this block, or the original iterator. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @ModifyExpressionValue(method = "compute(Lnet/minecraft/world/level/levelgen/DensityFunction$FunctionContext;)D", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/world/level/levelgen/Beardifier;pieceIterator:Lit/unimi/dsi/fastutil/objects/ObjectListIterator;"))
    private ObjectListIterator<Beardifier.Rigid> bons$rigids(ObjectListIterator<Beardifier.Rigid> original,
                                                              @Local(ordinal = 0) int x, @Local(ordinal = 1) int y, @Local(ordinal = 2) int z) {
        return this.bons$at(x, y, z) ? (ObjectListIterator) this.bons$rigidView : original;
    }

    /** The jigsaw junction list compute walks: a view for this block, or the original iterator. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @ModifyExpressionValue(method = "compute(Lnet/minecraft/world/level/levelgen/DensityFunction$FunctionContext;)D", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/world/level/levelgen/Beardifier;junctionIterator:Lit/unimi/dsi/fastutil/objects/ObjectListIterator;"))
    private ObjectListIterator<JigsawJunction> bons$junctions(ObjectListIterator<JigsawJunction> original,
                                                               @Local(ordinal = 0) int x, @Local(ordinal = 1) int y, @Local(ordinal = 2) int z) {
        return this.bons$at(x, y, z) ? (ObjectListIterator) this.bons$junctionView : original;
    }

    /** True when compute at (x, y, z) walks the views; prepares them on the first read at a new position. */
    @Unique
    private boolean bons$at(int x, int y, int z) {
        if (x == this.bons$px && y == this.bons$py && z == this.bons$pz) return this.bons$filtering;
        this.bons$px = x;
        this.bons$py = y;
        this.bons$pz = z;
        BeardifierBounds.Plan plan = this.bons$plan;
        if (plan == null) {
            plan = this.bons$plan = BeardifierBounds.plan(this, this.pieceIterator, this.junctionIterator);
            if (plan != BeardifierBounds.NONE) {
                this.bons$rigidView = new BeardifierBounds.View();
                this.bons$junctionView = new BeardifierBounds.View();
            }
        }
        if (plan == BeardifierBounds.NONE || !BeardifierBounds.enabled) return this.bons$filtering = false;
        if (BeardifierBounds.SHADOW) {
            BeardifierBounds.shadow((BeardifierBounds.Kernels) (Object) this, plan, x, y, z, this.bons$rigidView, this.bons$junctionView);
            return this.bons$filtering = false;   // shadow: compute walks the original lists
        }
        BeardifierBounds.select(plan, x, y, z, this.bons$rigidView, this.bons$junctionView);
        return this.bons$filtering = true;
    }

    /** Vanilla's BURY / ENCAPSULATE term method (shadow check only). */
    public double bons$bury(double dx, double dy, double dz) {
        return getBuryContribution(dx, dy, dz);
    }

    /** Vanilla's beard / junction term method (shadow check only). */
    public double bons$beard(int dx, int dy, int dz, int yToGround) {
        return getBeardContribution(dx, dy, dz, yToGround);
    }
}
