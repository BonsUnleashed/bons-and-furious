package agentcraft.terrain.mixin;
import agentcraft.terrain.MemoizingVisitor;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value=NoiseChunk.class, remap=false)
public abstract class NoiseChunkMixin {
    @ModifyArg(method="<init>",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/levelgen/NoiseRouter;m_224412_(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;"),index=0,require=1)
    private DensityFunction.Visitor acTerrain$memoizeRouter(DensityFunction.Visitor visitor) { return MemoizingVisitor.wrap(visitor); }
}
