package agentcraft.terrain.mixin;
import agentcraft.terrain.MemoizingVisitor;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value=NoiseChunk.class, remap=false)
public abstract class NoiseChunkMixin {
    // No argument index and no required injection: Mixin then finds the visitor by type even when another mod has
    // redirected this call, instead of refusing the class (bons.furious.guard.CallSites decides whether the memo runs).
    @ModifyArg(method="<init>",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/levelgen/NoiseRouter;m_224412_(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;"),require=0)
    private DensityFunction.Visitor acTerrain$memoizeRouter(DensityFunction.Visitor visitor) { return MemoizingVisitor.wrap(visitor); }
}
