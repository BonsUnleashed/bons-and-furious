package agentcraft.consolidated.bons_valkyrien_fixes.mixin;

import bons.pure.trackwork.ModelParentRepair;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps the existing mod_resources priority; higher user resource packs win. */
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

    @Unique private static IoSupplier<InputStream> bons$model(String path) {
        var file = ModList.get().getModFileById("trackwork").getFile()
            .findResource("assets", "trackwork", path);
        return () -> new ByteArrayInputStream(ModelParentRepair.repair(path, Files.readAllBytes(file)));
    }

    @Inject(method = "m_214146_", at = @At("HEAD"), cancellable = true, remap = false)
    private void bons$lookup(PackType type, ResourceLocation id,
                            CallbackInfoReturnable<IoSupplier<InputStream>> cir) {
        if (bons$active(type) && id.m_135827_().equals("trackwork") && bons$models.contains(id.m_135815_()))
            cir.setReturnValue(bons$model(id.m_135815_()));
    }

    @Inject(method = "m_8031_", at = @At("RETURN"), remap = false)
    private void bons$list(PackType type, String namespace, String prefix,
                          PackResources.ResourceOutput output, CallbackInfo ci) {
        if (bons$active(type) && namespace.equals("trackwork"))
            for (String path : bons$models)
                if (path.startsWith(prefix + "/") || prefix.isEmpty())
                    output.accept(new ResourceLocation(namespace, path), bons$model(path));
    }
}
