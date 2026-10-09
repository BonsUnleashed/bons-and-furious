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
 * vanilla_structure_ring_search (Minecraft 1.20.1, tested build 1.20.1 SRG; server side): ChunkGenerator's nearest
 * random-spread structure search per ring (getNearestGeneratedStructure, m_223188_).
 *
 * A cancellable HEAD injection walks the ring's border cells directly, in the original order, and makes the original
 * calls for each (RandomSpreadStructurePlacement.getPotentialStructureChunk, then getStructureGeneratingAt, shadowed here),
 * returning the first hit or null as the original does. The interior cells the original loop visits and skips are not
 * visited. Why the behaviour is identical is documented on StructureRing. No mod in the pack hooks this method (mixin
 * index 2026-10-09, c2meF nested jars); the ConcentricRings overload (m_223181_) and the caller are untouched.
 */
@Mixin(value = ChunkGenerator.class, remap = false)
public abstract class StructureRingSearchMixin {
    @Shadow
    private static Pair<BlockPos, Holder<Structure>> m_223198_(Set<Holder<Structure>> structures, LevelReader level, StructureManager manager,
                                                                boolean skipKnown, StructurePlacement placement, ChunkPos chunk) {
        throw new AssertionError();
    }

    @Inject(method = "m_223188_", at = @At("HEAD"), cancellable = true)
    private static void bons$ringBorder(Set<Holder<Structure>> structures, LevelReader level, StructureManager manager, int sectionX, int sectionZ,
                                        int radius, boolean skipKnown, long seed, RandomSpreadStructurePlacement placement,
                                        CallbackInfoReturnable<Pair<BlockPos, Holder<Structure>>> cir) {
        if (!StructureRing.enabled || radius < 0 || radius > 0x3FFFFFFF) return;           // (2 * radius must not overflow)
        if (StructureRing.SHADOW && !StructureRing.shadowAgrees(placement, seed, sectionX, sectionZ, radius)) return;
        int spacing = placement.m_205003_();
        for (int dx = -radius; dx <= radius; ++dx) {
            int step = (dx == -radius || dx == radius) ? 1 : 2 * radius;      // inner rows: first and last column only
            for (int dz = -radius; dz <= radius; dz += step) {
                ChunkPos chunk = placement.m_227008_(seed, sectionX + spacing * dx, sectionZ + spacing * dz);
                Pair<BlockPos, Holder<Structure>> found = m_223198_(structures, level, manager, skipKnown, placement, chunk);
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
