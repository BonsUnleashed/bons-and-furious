package agentcraft.consolidated.bons_valkyrien_fixes.mixin;

import bons.pure.trackwork.ModelParentRepair;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.resource.DelegatingPackResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the existing mod_resources priority; higher user resource packs win.
 * 1.0.34: only what this pack really serves is repaired - the supplier its own lookup or listing found, read through
 * ModelParentRepair, which changes only Trackwork's reviewed original bytes. 1.0.33 claimed the five paths from
 * Trackwork's jar whether or not the file exists there (a phantom model and a load error with another Trackwork build)
 * and over any other copy this pack would have served.
 */
@Mixin(value = DelegatingPackResources.class, remap = false)
public abstract class TrackworkResources {
    @Unique private static final Set<String> bons$models = Set.of(
        "models/block/oleo_wheel_single.json", "models/block/oleo_wheel_twin.json",
        "models/block/small_simple_wheel.json", "models/block/track_link.json",
        "models/block/wrapped_link.json");

    @Unique private boolean bons$active(PackType type) {
        return type == PackType.CLIENT_RESOURCES
            && ((PackResources)(Object)this).m_5542_().equals("mod_resources")
            && ModList.get() != null && ModList.get().isLoaded("trackwork");
    }

    /** 1.0.34: the supplier the pack found (never null here), its bytes passed through the reviewed repair. */
    @Unique private static IoSupplier<InputStream> bons$model(String path, IoSupplier<InputStream> found) {
        return () -> {
            try (InputStream in = found.m_247737_()) {   // IoSupplier.get
                return new ByteArrayInputStream(ModelParentRepair.repair(path, in.readAllBytes()));
            }
        };
    }

    // 1.0.34: at RETURN, so a path the pack does not serve stays absent (was: HEAD, Trackwork's file claimed unconditionally)
    @Inject(method = "m_214146_", at = @At("RETURN"), cancellable = true, remap = false)
    private void bons$lookup(PackType type, ResourceLocation id,
                            CallbackInfoReturnable<IoSupplier<InputStream>> cir) {
        IoSupplier<InputStream> found = cir.getReturnValue();
        if (found != null && bons$active(type) && id.m_135827_().equals("trackwork") && bons$models.contains(id.m_135815_()))
            cir.setReturnValue(bons$model(id.m_135815_(), found));
    }

    // 1.0.34: wraps the entries the delegates list (was: the five paths output again after them, listed or not)
    @ModifyVariable(method = "m_8031_", at = @At("HEAD"), argsOnly = true, remap = false)
    private PackResources.ResourceOutput bons$list(PackResources.ResourceOutput output, PackType type, String namespace) {
        if (!bons$active(type) || !namespace.equals("trackwork")) return output;
        return (id, found) -> output.accept(id, bons$models.contains(id.m_135815_()) ? bons$model(id.m_135815_(), found) : found);
    }
}
