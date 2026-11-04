package bons.furious.mixin.mcreator_constant_tag_keys;

import bons.furious.patch.mcreator_constant_tag_keys.TagCallers;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * skarriermobs_constant_tag_keys (Skarrier Mobs 1.1.2 and 1.0.6; both sides; since 1.0.49), on the mcreator_constant_tag_keys
 * layer.
 *
 * MobsBurningInSunProcedure.execute tests {@code entity.getType().is(TagKey.create(Registries.ENTITY_TYPE, new
 * ResourceLocation("skarrier_mobs:burns_in_daylight")))} for every living entity (every tick in 1.0.6, every 20 ticks in 1.1.2).
 * The two calls of that expression are redirected to the layer: the location of the id and the canonical tag key, both made
 * once (TagKey.create interns, so the key is the very object the original call returns). The tag test is unchanged. The
 * method has exactly one location constructor and one TagKey.create (the guard pins its bytecode). Our own logic only; the
 * mod's code stays in its jar.
 */
@Mixin(targets = "net.mcreator.skarriermobs.procedures.MobsBurningInSunProcedure", remap = false)
public abstract class SkarrierDaylightBurnTagMixin {
    private static final String EXECUTE = "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/Entity;)V";

    @Redirect(method = EXECUTE, at = @At(value = "NEW", target = "(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"),
            require = 1, allow = 1, remap = false)
    private static ResourceLocation bons$tagId(String id) {
        return TagCallers.SKARRIER.location(id);
    }

    @Redirect(method = EXECUTE, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/tags/TagKey;m_203882_(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/tags/TagKey;"),
            require = 1, allow = 1, remap = false)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static TagKey<?> bons$tagKey(ResourceKey registry, ResourceLocation id) {
        return TagCallers.SKARRIER.tag(registry, id);
    }
}
