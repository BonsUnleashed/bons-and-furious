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
 * vanilla_template_top_air_trim (Minecraft 1.20.1, both sides; kind change, off by default). @Inject at the RETURN of
 * StructureTemplate.load (m_246595_): when the template is being read by StructureTemplateManager.loadFromResource on this
 * thread (StructureTemplateManagerResourceMixin), each palette's block list (f_74645_, through PaletteBlocksAccess) drops
 * its air entries above the highest non-air block, keeping room above the highest entity (TopAirTrim). Size, entities and
 * every other block are unchanged. Only our own logic is carried. See TopAirTrim.
 */
@Mixin(value = StructureTemplate.class, remap = false)
public abstract class StructureTemplateTopAirMixin {
    @Shadow
    @Final
    private List<StructureTemplate.Palette> f_74482_;
    @Shadow
    @Final
    private List<StructureTemplate.StructureEntityInfo> f_74483_;

    @Inject(method = "m_246595_", at = @At("RETURN"))
    private void bons$trimTopAir(HolderGetter<Block> blocks, CompoundTag tag, CallbackInfo ci) {
        if (!TopAirTrim.fromResource()) return;
        int entityTop = Integer.MIN_VALUE;
        for (StructureTemplate.StructureEntityInfo e : this.f_74483_) entityTop = Math.max(entityTop, e.f_74684_.m_123342_());
        for (StructureTemplate.Palette p : this.f_74482_) TopAirTrim.trim(((PaletteBlocksAccess) (Object) p).bons$blocks(), entityTop);
        TopAirTrim.TEMPLATES.incrementAndGet();
    }
}
