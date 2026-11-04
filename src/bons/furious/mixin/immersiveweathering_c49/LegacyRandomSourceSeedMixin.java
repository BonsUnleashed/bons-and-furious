package bons.furious.mixin.immersiveweathering_c49;

import bons.furious.patch.immersiveweathering_c49.SeedAccess;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * immersiveweathering_growth_roll_batch (vanilla LegacyRandomSource; server side): exposes the seed AtomicLong through
 * SeedAccess so the growth loop can roll a run of chances on a local copy and write the seed back with one
 * compare-and-set. Nothing of LegacyRandomSource changes.
 */
@Mixin(value = LegacyRandomSource.class, remap = false)
public abstract class LegacyRandomSourceSeedMixin implements SeedAccess {
    @Shadow
    @Final
    private AtomicLong f_188575_;

    @Override
    public AtomicLong bons$iwC49Seed() {
        return this.f_188575_;
    }
}
