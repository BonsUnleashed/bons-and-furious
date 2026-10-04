package bons.furious.mixin.beardifier;

import bons.furious.patch.beardifier.EmptyBeardifiers;
import bons.furious.patch.beardifier.EmptyFillBeardifier;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * worldgen_empty_beardifier_marker (Minecraft 1.21.1 world generation, server side; tested with NeoForge 21.1.252):
 * Beardifier gets a flag, read access to vanilla's two iterators, and its own fillArray.
 *
 * Beardifier declares no fillArray; it inherits DensityFunction.SimpleFunction's, which is
 * {@code provider.fillAllDirectly(values, this)}. The fillArray added here does exactly that, except for a Beardifier
 * that EmptyBeardifiers.mark flagged as adding nothing anywhere: then EmptyBeardifiers.fill writes the +0.0 values and the
 * NoiseChunk state the loop would have produced (see there for why that is identical) and the per-block compute calls
 * are skipped. An unflagged Beardifier (any chunk with a structure piece near it, every Beardifier other code builds)
 * and every direct compute call behave as before. Minecraft is no-copy: the added method carries only our own logic,
 * the else branch is the one-call contract of SimpleFunction.fillArray.
 *
 * Ported to 1.21.1: Mojang member names (NeoForge runs them in production too); Beardifier still declares no fillArray
 * and keeps the same two protected final iterators (guarded by the declared-method set).
 */
@Mixin(value = Beardifier.class, remap = false)
public abstract class BeardifierEmptyFillMixin implements EmptyFillBeardifier {
    @Shadow
    @Final
    protected ObjectListIterator<Beardifier.Rigid> pieceIterator;
    @Shadow
    @Final
    protected ObjectListIterator<JigsawJunction> junctionIterator;
    @Unique
    private boolean bons$emptyFill;

    @Override
    public void bons$markEmptyFill() {
        this.bons$emptyFill = true;
    }

    @Override
    public boolean bons$isEmptyFill() {
        return this.bons$emptyFill;
    }

    @Override
    public ObjectListIterator<?> bons$pieceIterator() {
        return this.pieceIterator;
    }

    @Override
    public ObjectListIterator<?> bons$junctionIterator() {
        return this.junctionIterator;
    }

    /** DensityFunction.fillArray for Beardifier (was SimpleFunction's default). */
    public void fillArray(double[] values, DensityFunction.ContextProvider provider) {
        DensityFunction self = (DensityFunction) (Object) this;
        if (!this.bons$emptyFill || !EmptyBeardifiers.fill(values, provider, self)) {
            provider.fillAllDirectly(values, self);
        }
    }
}
