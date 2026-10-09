package bons.furious.mixin.vanilla_structure_templates;

import bons.furious.patch.vanilla_structure_templates.StructureTemplateHits;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_structure_template_hits (Minecraft 1.20.1 on Forge 47.4.16; server side incl. the integrated server): the
 * templates.computeIfAbsent(id, this::tryLoad) call in StructureTemplateManager.get (m_230407_) asks the map plainly
 * first and returns a loaded template without the cache's lock (StructureTemplateHits); absent, collected or loading
 * entries take the original call. ModernFix's <init> inject (the map it installs) and @Overwrite of m_230427_ and
 * Structure Gel's tryLoad (m_230425_) inject are untouched. No Minecraft code.
 */
@Mixin(value = StructureTemplateManager.class, remap = false)
public abstract class StructureTemplateManagerHitsMixin {
    @WrapOperation(method = "m_230407_", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
    @SuppressWarnings("rawtypes")
    private Object bons$plainHit(Map map, Object key, Function loader, Operation<Object> original) {
        return StructureTemplateHits.get(map, key, loader, original);
    }
}
