package bons.furious.mixin.mcreator_constant_tag_keys;

import bons.furious.patch.mcreator_constant_tag_keys.TagCallers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * skarriermobs_constant_tag_keys (Skarrier Mobs 1.1.2 and 1.0.6; both sides; since 1.0.49), on the mcreator_constant_tag_keys
 * layer.
 *
 * ResisteelToolsEffectProcedure.execute tests {@code mainHandItem.is(ItemTags.create(new
 * ResourceLocation("skarrier_mobs:resisteel_tools")))} for every living entity (every tick in 1.0.6, every 10 ticks in 1.1.2).
 * The two calls are redirected to the layer: the location of the id and the canonical item tag key, both made once. The tag
 * test and everything after it are unchanged. The method has exactly one location constructor and one ItemTags.create (the
 * guard pins its bytecode). Our own logic only; the mod's code stays in its jar.
 */
@Mixin(targets = "net.mcreator.skarriermobs.procedures.ResisteelToolsEffectProcedure", remap = false)
public abstract class SkarrierResisteelToolsTagMixin {
    private static final String EXECUTE = "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/entity/Entity;)V";

    @Redirect(method = EXECUTE, at = @At(value = "NEW", target = "(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"),
            require = 1, allow = 1, remap = false)
    private static ResourceLocation bons$tagId(String id) {
        return TagCallers.SKARRIER.location(id);
    }

    @Redirect(method = EXECUTE, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/tags/ItemTags;create(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/tags/TagKey;"),
            require = 1, allow = 1, remap = false)
    private static TagKey<Item> bons$itemTag(ResourceLocation id) {
        return TagCallers.SKARRIER.itemTag(id);
    }
}
