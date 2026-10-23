package bons.furious.mixin.noise;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(value=ImprovedNoise.class,remap=false)
public interface ImprovedNoiseInvoker {
    @Invoker("sampleAndLerp")
    double bons$originalSample(int ix,int iy,int iz,double x,double y,double z,double fadeY);
}
