package bons.furious.mixin.vanilla_structure_ring;

import bons.furious.patch.vanilla_structure_ring.StructureRing;
import com.mojang.datafixers.util.Pair;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * vanilla_structure_ring_search (Minecraft 1.21.1, tested build NeoForge 21.1.252; server side): ChunkGenerator's nearest
 * random-spread structure search per ring (the static getNearestGeneratedStructure for RandomSpreadStructurePlacement).
 *
 * A cancellable HEAD injection walks the ring's border cells directly, in the original order, and makes the original
 * calls for each (RandomSpreadStructurePlacement.getPotentialStructureChunk, then getStructureGeneratingAt, shadowed here),
 * returning the first hit or null as the original does. The interior cells the original loop visits and skips are not
 * visited. Why the behaviour is identical is documented on StructureRing. The ConcentricRings overload and the caller are
 * untouched.
 *
 * Ported to 1.21.1: the random-spread search loop is unchanged; getStructureGeneratingAt changed inside (1.21.1 passes
 * the placement to checkStructurePresence) but keeps its signature, and it is called unchanged. Structurify 2.0.42 wraps
 * the getStructureGeneratingAt call inside this very loop (it re-resolves the candidate chunk for its placement-attempt
 * option), so with Structurify installed this switch steps aside (patches/vanilla_structure_ring.json yield): calling
 * getStructureGeneratingAt from here would bypass that wrapper.
 */
@Mixin(value = ChunkGenerator.class, remap = false)
public abstract class StructureRingSearchMixin {
    @Shadow
    private static Pair<BlockPos, Holder<Structure>> getStructureGeneratingAt(Set<Holder<Structure>> structures, LevelReader level, StructureManager manager,
                                                                boolean skipKnown, StructurePlacement placement, ChunkPos chunk) {
        throw new AssertionError();
    }

    @Inject(method = "getNearestGeneratedStructure(Ljava/util/Set;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/world/level/StructureManager;IIIZJLnet/minecraft/world/level/levelgen/structure/placement/RandomSpreadStructurePlacement;)Lcom/mojang/datafixers/util/Pair;",
            at = @At("HEAD"), cancellable = true)
    private static void bons$ringBorder(Set<Holder<Structure>> structures, LevelReader level, StructureManager manager, int sectionX, int sectionZ,
                                        int radius, boolean skipKnown, long seed, RandomSpreadStructurePlacement placement,
                                        CallbackInfoReturnable<Pair<BlockPos, Holder<Structure>>> cir) {
        if (!StructureRing.enabled || radius < 0 || radius > 0x3FFFFFFF) return;           // (2 * radius must not overflow)
        if (StructureRing.SHADOW && !StructureRing.shadowAgrees(placement, seed, sectionX, sectionZ, radius)) return;
        int spacing = placement.spacing();
        for (int dx = -radius; dx <= radius; ++dx) {
            int step = (dx == -radius || dx == radius) ? 1 : 2 * radius;      // inner rows: first and last column only
            for (int dz = -radius; dz <= radius; dz += step) {
                ChunkPos chunk = placement.getPotentialStructureChunk(seed, sectionX + spacing * dx, sectionZ + spacing * dz);
                Pair<BlockPos, Holder<Structure>> found = getStructureGeneratingAt(structures, level, manager, skipKnown, placement, chunk);
                if (found != null) {
                    StructureRing.served();
                    cir.setReturnValue(found);
                    return;
                }
            }
        }
        StructureRing.served();
        cir.setReturnValue(null);
    }
}
