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
 * vanilla_structure_template_hits (Minecraft 1.21.1, tested build NeoForge 21.1.252 with or without ModernFix 5.27.24;
 * server side incl. the integrated server): the structureRepository.computeIfAbsent(id, this::tryLoad) call in
 * StructureTemplateManager.get asks the map plainly first and returns a loaded template without the cache's lock
 * (StructureTemplateHits); absent, collected or loading entries take the original call. ModernFix's <init> inject (the
 * map it installs) is untouched. No Minecraft code.
 *
 * Ported to 1.21.1: get is unchanged (still the one computeIfAbsent call); the class uses the map the same five ways.
 * Generator Accelerator 1.6.2 installs a size-bounded (LRU) Guava cache there instead, which the exactness argument does
 * not cover, so the switch steps aside next to it (patches/vanilla_structure_templates.json yield).
 */
@Mixin(value = StructureTemplateManager.class, remap = false)
public abstract class StructureTemplateManagerHitsMixin {
    @WrapOperation(method = "get", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
    @SuppressWarnings("rawtypes")
    private Object bons$plainHit(Map map, Object key, Function loader, Operation<Object> original) {
        return StructureTemplateHits.get(map, key, loader, original);
    }
}
