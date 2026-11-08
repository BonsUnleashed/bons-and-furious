package bons.furious.mixin.vanilla_bone_memo_c49;

import bons.furious.patch.vanilla_bone_memo_c49.BoneMemo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * vanilla_model_bone_lookup_memo (Minecraft 1.21.1, client; a trade-off, off by default), part 1 of 2: a MixinExtras
 * @WrapMethod around HierarchicalModel.getAnyDescendantWithName(String), whichever body is there (vanilla's stream walk,
 * with vanilla_model_bone_lookup's redirect at priority 1001 when that switch applies; an @Overwrite at 1000 is let in at
 * priority 1002), and the per-model memo field (BoneMemo). The wrapped body runs unchanged whenever the memo has no intact
 * answer.
 */
@Mixin(value = HierarchicalModel.class, priority = 1002, remap = false)
public abstract class BoneMemoMixin implements BoneMemo.Holder {
    @Unique
    private Map<String, BoneMemo.Entry> bons$vanilla_bone_memo_c49Bones;

    @Override
    public Map<String, BoneMemo.Entry> bons$vanilla_bone_memo_c49Memo() {
        Map<String, BoneMemo.Entry> m = this.bons$vanilla_bone_memo_c49Bones;
        if (m == null) this.bons$vanilla_bone_memo_c49Bones = m = BoneMemo.newMemo();
        return m;
    }

    @WrapMethod(method = "getAnyDescendantWithName(Ljava/lang/String;)Ljava/util/Optional;")
    private Optional<ModelPart> bons$rememberedBone(String name, Operation<Optional<ModelPart>> original) {
        return BoneMemo.lookup((HierarchicalModel<?>) (Object) this, name, original);
    }
}
