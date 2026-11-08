package bons.furious.mixin.structurify_set_reuse;

import bons.furious.patch.structurify_set_reuse.SetEntries;
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * structurify_set_entries_reuse (Structurify 2.0.42 on Minecraft 1.21.1 / NeoForge, server side incl. the integrated
 * server). MixinSquared injections into Structurify's handler structurify$getStructures (its @WrapMethod on
 * StructureSet.structures()): at the handler's HEAD the answer kept from its last run is returned when SetEntries says it
 * is the one the handler would return (same immutable record list, Structurify's own answer field unchanged; the field is
 * read through StructurifyAnswerAccess); at its RETURN (MixinExtras @ModifyReturnValue) the answer and the record list it
 * belongs to are kept. Priority 1500, after Structurify's mixin (1000). Only our own logic is carried (CC BY-NC-ND
 * target: no derivative of its code). See SetEntries.
 */
@Mixin(value = StructureSet.class, priority = 1500, remap = false)
public abstract class StructureSetEntriesReuseMixin {
    @Shadow
    @Final
    private List<StructureSet.StructureSelectionEntry> structures;
    /** Structurify's last answer and the record list it was computed for (one immutable holder). */
    @Unique
    private volatile SetEntries.Seen bons$seenEntries;

    @SuppressWarnings({"rawtypes", "unchecked"})
    @TargetHandler(mixin = "com.faboslav.structurify.common.mixin.structure.StructureSetMixin", name = "structurify$getStructures")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
    private void bons$reuseEntries(Operation<List<StructureSet.StructureSelectionEntry>> original, CallbackInfoReturnable<List<StructureSet.StructureSelectionEntry>> cir) {
        SetEntries.Seen seen = this.bons$seenEntries;
        if (seen == null) return;
        List kept = SetEntries.reuse(seen, this.structures, ((StructurifyAnswerAccess) (Object) this).bons$structurifyAnswer());
        if (kept != null) cir.setReturnValue(kept);
    }

    @TargetHandler(mixin = "com.faboslav.structurify.common.mixin.structure.StructureSetMixin", name = "structurify$getStructures")
    @ModifyReturnValue(method = "@MixinSquared:Handler", at = @At("RETURN"))
    private List<StructureSet.StructureSelectionEntry> bons$keepEntries(List<StructureSet.StructureSelectionEntry> answer) {
        SetEntries.shadow(answer);
        if (SetEntries.enabled) {
            SetEntries.Seen seen = this.bons$seenEntries;
            if (seen == null || seen.original() != this.structures || seen.answer() != answer) this.bons$seenEntries = SetEntries.seen(this.structures, answer);
        }
        return answer;
    }
}
