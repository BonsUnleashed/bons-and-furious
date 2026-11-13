package bons.furious.mixin.flat_cache_columns;

import bons.furious.patch.flat_cache_columns.OutsideColumns;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Intrinsic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * vanilla_flat_cache_outside_columns (Minecraft 1.21.1 world generation, tested build NeoForge 21.1.252; both sides), part 3 of 3:
 * the marker record (DensityFunctions$Marker: flat_cache, cache_2d, cache_once, interpolated, cache_all_in_cell).
 *
 * A marker's mapAll is the default method of DensityFunctions.MarkerOrMarked: it maps the wrapped function,
 * puts the result in a new marker of the same type and hands that to the visitor. A NoiseChunk's visitor answers a
 * flat_cache marker with a FlatCache, and nothing tells that FlatCache which marker of the router it stands for.
 * This mixin gives the marker record its own mapAll, which runs the interface's method unchanged (whatever that is:
 * vanilla's, ours for terrain_final_density_reuse, another mod's; bound once as a special method handle in the record's
 * static initializer, so the call is a constant the JIT inlines) and then, when the result is a FlatCache, tells it
 * the marker it came from (OutsideColumns.Flat.bons$origin). The FlatCache only notes whether that marker passed the
 * router's check. Every other result (another visitor's, a marker, a cache of another kind) is returned as it is.
 * (Mixin 0.8.5 has no injectors in interfaces, and an X.super call from a mixin is re-bound by Mixin, so neither can
 * be used.) Our own logic only; no Minecraft code is carried.
 *
 * Generic stand-down, the way LevelClipMixin does it (1.0.30): the method is a soft implementation of
 * MarkerOrMarked.mapAll (@Implements prefix "bons$origin$") marked @Intrinsic, in a mixin of priority
 * 1,000,000,000, so Mixin applies it after every other mixin on the record. When another mixin has already given the
 * record its own mapAll, Mixin keeps that method and skips this one; then no FlatCache learns its marker and the
 * switch keeps the original code (one log line).
 *
 * Ported to 1.21.1: the marker record still declares no mapAll of its own and inherits MarkerOrMarked's (development and
 * production jars); Mojang names. In the 1.21.1 jars read for the port, C2ME 0.4's density function compiler
 * (opts.dfc.mixin.MixinDFTWrapping, also inside SuperChunk) gives the record its own mapAll, so next to it this method is
 * skipped and the switch keeps the original code; Bye Pregen 1.1.3.0 and our terrain_final_density_reuse change the
 * interface's method, which this one calls as it is.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Marker", priority = 1_000_000_000, remap = false)
@Implements(@Interface(iface = DensityFunctions.MarkerOrMarked.class, prefix = "bons$origin$", remap = Interface.Remap.NONE))
public abstract class MarkerOriginMixin {
    @Unique
    private static final MethodHandle bons$defaultMapAll = bons$bindDefaultMapAll();

    @Unique
    private static MethodHandle bons$bindDefaultMapAll() {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();          // merged into the record: its own lookup
            return lookup.findSpecial(DensityFunctions.MarkerOrMarked.class, "mapAll",
                            MethodType.methodType(DensityFunction.class, DensityFunction.Visitor.class), lookup.lookupClass())
                    .asType(MethodType.methodType(DensityFunction.class, DensityFunctions.MarkerOrMarked.class, DensityFunction.Visitor.class));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Bons and Furious: vanilla_flat_cache_outside_columns cannot bind the marker's mapAll", e);
        }
    }

    /** Marker.mapAll, unless another mod already gives the record its own (then Mixin keeps theirs: @Intrinsic). */
    @Intrinsic
    public DensityFunction bons$origin$mapAll(DensityFunction.Visitor visitor) {
        DensityFunction mapped;
        try {
            mapped = (DensityFunction) bons$defaultMapAll.invokeExact((DensityFunctions.MarkerOrMarked) (Object) this, visitor);
        } catch (Throwable t) {
            throw OutsideColumns.<RuntimeException>rethrow(t);  // whatever the interface's method throws, unchanged
        }
        if (mapped instanceof OutsideColumns.Flat flat) flat.bons$origin(this);
        return mapped;
    }
}
