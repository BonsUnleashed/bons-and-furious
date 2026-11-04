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
 * vanilla_template_top_air_trim (Minecraft 1.20.1, both sides; kind change, off by default). @Inject at HEAD and RETURN of
 * StructureTemplateManager.loadFromResource (m_230427_, the source that reads templates from mod jars and data packs)
 * marks the templates loaded in between on this thread for StructureTemplateTopAirMixin. HEAD and RETURN injections, so
 * they also sit in ModernFix's replacement of this method (cache_upgraded_structures). The world's generated folder and
 * the test structures are other sources and are never trimmed. Only our own logic is carried. See TopAirTrim.
 */
@Mixin(value = StructureTemplateManager.class, remap = false)
public abstract class StructureTemplateManagerResourceMixin {
    @Inject(method = "m_230427_", at = @At("HEAD"))
    private void bons$resourceStart(ResourceLocation id, CallbackInfoReturnable<Optional<StructureTemplate>> cir) {
        TopAirTrim.enterResource();
    }

    @Inject(method = "m_230427_", at = @At("RETURN"))
    private void bons$resourceEnd(ResourceLocation id, CallbackInfoReturnable<Optional<StructureTemplate>> cir) {
        TopAirTrim.exitResource();
    }
}
