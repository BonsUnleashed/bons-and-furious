package bons.furious.mixin.kubejs_c49;

import bons.furious.patch.kubejs_c49.GeneratedPackIndex;
import dev.latvian.mods.kubejs.script.data.GeneratedData;
import dev.latvian.mods.kubejs.script.data.KubeFileResourcePack;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * kubejs_generated_pack_index (KubeJS, LGPL-3.0; 1.21.1 tested build neoforge 2101.7.2-build.381; both sides): a
 * cancellable HEAD inject on KubeFileResourcePack.listResources. For the pack's own type it normalises the folder as KubeJS
 * does, calls getGenerated() once as KubeJS does, and visits the matches from the generated map's sorted index (rebuilt
 * when the map object changes); any other type falls through to KubeJS's code (which visits nothing). Why the visits are
 * the same: GeneratedPackIndex.
 *
 * Ported to 1.21.1: KubeJS 7 calls the file pack (kubejs/data, kubejs/assets) KubeFileResourcePack (was
 * GeneratedResourcePack); what scripts generate lives in its separate VirtualResourcePack, which this switch leaves alone.
 */
@Mixin(value = KubeFileResourcePack.class, remap = false)
public abstract class GeneratedPackIndexMixin {
    @Shadow
    @Final
    private PackType packType;

    @Shadow
    public abstract Map<ResourceLocation, GeneratedData> getGenerated();

    @Unique
    private volatile GeneratedPackIndex.Index bons$kubejsC49Index;

    @Inject(method = "listResources(Lnet/minecraft/server/packs/PackType;Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/server/packs/PackResources$ResourceOutput;)V",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void bons$kubejsC49List(PackType type, String namespace, String path, PackResources.ResourceOutput visitor, CallbackInfo ci) {
        if (!GeneratedPackIndex.enabled || type != this.packType) return;
        String prefix = path.endsWith("/") ? path : path + "/";
        Map<ResourceLocation, GeneratedData> generated = this.getGenerated();
        GeneratedPackIndex.Index index = this.bons$kubejsC49Index;
        if (index == null || !index.of(generated)) this.bons$kubejsC49Index = index = GeneratedPackIndex.build(generated);
        if (GeneratedPackIndex.SHADOW) GeneratedPackIndex.shadow(generated, index, namespace, prefix);
        GeneratedPackIndex.visit(index, namespace, prefix, visitor);
        ci.cancel();
    }
}
