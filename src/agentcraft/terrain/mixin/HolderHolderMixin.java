package agentcraft.terrain.mixin;
import agentcraft.terrain.MemoizingVisitor;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=DensityFunctions.HolderHolder.class,remap=false)
public abstract class HolderHolderMixin {
    @Inject(method="m_207456_",at=@At("HEAD"),cancellable=true,require=1)
    private void acTerrain$reuseMappedReference(DensityFunction.Visitor visitor,CallbackInfoReturnable<DensityFunction> cir) {
        if(visitor instanceof MemoizingVisitor memo)cir.setReturnValue(memo.map((DensityFunctions.HolderHolder)(Object)this));
    }
}
