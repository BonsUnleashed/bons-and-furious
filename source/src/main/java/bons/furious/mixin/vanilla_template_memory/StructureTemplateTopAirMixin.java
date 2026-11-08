package bons.furious.mixin.vanilla_template_memory;

import bons.furious.patch.vanilla_template_memory.TopAirTrim;
import java.util.List;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_template_top_air_trim (Minecraft 1.21.1 with NeoForge 21.1.252, both sides; kind change, off by default). @Inject
 * at the RETURN of StructureTemplate.load(HolderGetter, CompoundTag): when the template is being read by
 * StructureTemplateManager.loadFromResource on this thread (StructureTemplateManagerResourceMixin), each palette's block
 * list (blocks, the list buildInfoList made, through PaletteBlocksAccess) drops its air entries above the highest non-air
 * block, keeping room above the highest entity (TopAirTrim). The palettes were made by this load and their per-block
 * caches are still empty; the template is not yet in the manager's map, so no other thread reads it (C2ME's synchronized
 * palette and entity lists are read by this thread alone here). Size, entities and every other block are unchanged; the
 * other injections at the end of load (Immersive Engineering's wire connections, Cable Facades' facades) are untouched.
 * Only our own logic is carried. See TopAirTrim.
 */
@Mixin(value = StructureTemplate.class, remap = false)
public abstract class StructureTemplateTopAirMixin {
    @Shadow
    @Final
    private List<StructureTemplate.Palette> palettes;
    @Shadow
    @Final
    private List<StructureTemplate.StructureEntityInfo> entityInfoList;

    @Inject(method = "load(Lnet/minecraft/core/HolderGetter;Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    private void bons$trimTopAir(HolderGetter<Block> blocks, CompoundTag tag, CallbackInfo ci) {
        if (!TopAirTrim.fromResource()) return;
        int entityTop = Integer.MIN_VALUE;
        for (StructureTemplate.StructureEntityInfo e : this.entityInfoList) entityTop = Math.max(entityTop, e.blockPos.getY());
        for (StructureTemplate.Palette p : this.palettes) TopAirTrim.trim(((PaletteBlocksAccess) (Object) p).bons$blocks(), entityTop);
        TopAirTrim.TEMPLATES.incrementAndGet();
    }
}
