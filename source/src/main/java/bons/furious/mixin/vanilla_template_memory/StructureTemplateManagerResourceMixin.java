package bons.furious.mixin.vanilla_template_memory;

import bons.furious.patch.vanilla_template_memory.TopAirTrim;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * vanilla_template_top_air_trim (Minecraft 1.21.1 with NeoForge 21.1.252, both sides; kind change, off by default). @Inject
 * at HEAD and RETURN of StructureTemplateManager.loadFromResource (the source that reads templates from mod jars and data
 * packs: load(() -> resourceManager.open(...)) -> readStructure -> StructureTemplate.load) marks the templates loaded in
 * between on this thread for StructureTemplateTopAirMixin. Its read (load) catches every Throwable and returns
 * Optional.empty(), so RETURN follows HEAD whenever a template was read. The world's generated folder (loadFromGenerated,
 * asked first) and the test structures are other sources and are never trimmed; ModernFix 5.27.24 only replaces the
 * manager's template map (dynamic_structure_manager), not this method. Only our own logic is carried. See TopAirTrim.
 */
@Mixin(value = StructureTemplateManager.class, remap = false)
public abstract class StructureTemplateManagerResourceMixin {
    @Inject(method = "loadFromResource(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;", at = @At("HEAD"))
    private void bons$resourceStart(ResourceLocation id, CallbackInfoReturnable<Optional<StructureTemplate>> cir) {
        TopAirTrim.enterResource();
    }

    @Inject(method = "loadFromResource(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;", at = @At("RETURN"))
    private void bons$resourceEnd(ResourceLocation id, CallbackInfoReturnable<Optional<StructureTemplate>> cir) {
        TopAirTrim.exitResource();
    }
}
