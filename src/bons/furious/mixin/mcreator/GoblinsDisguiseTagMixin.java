package bons.furious.mixin.mcreator;

import goblinstyranny.procedures.IsDisguisedProcedure;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * goblins_cached_disguise_tag (Goblins Tyranny 1.2.3).
 *
 * IsDisguisedProcedure runs for every living entity on every tick and built its entity tag from scratch each time:
 * {@code new ResourceLocation("goblins_tyranny:goblins")} and {@code TagKey.create}, which allocates a key and looks it
 * up in the global interner. The tag key is now created once, in the static field ac$goblinTag, and the per-tick
 * expression is handed that key (and its id) instead of building new ones. The tag test itself is unchanged.
 */
@Mixin(value = IsDisguisedProcedure.class, remap = false)
public abstract class GoblinsDisguiseTagMixin {
    /** The goblins_tyranny:goblins entity type tag. TagKey.create (m_203882_) interns, so this is the same key. */
    @Unique
    private static final TagKey<EntityType<?>> ac$goblinTag =
            TagKey.m_203882_(Registries.f_256939_, new ResourceLocation("goblins_tyranny:goblins"));

    // Two redirects remove the whole per-call expression TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(..)):
    // the id is no longer parsed and no key is built. Both handlers are allocation-free.

    /** new ResourceLocation("goblins_tyranny:goblins"): reuse the id the cached key already holds. */
    @Redirect(method = "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "NEW", target = "(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"))
    private static ResourceLocation bons$cachedTagId(String id) {
        return ac$goblinTag.f_203868_();   // TagKey.location()
    }

    /** TagKey.create(Registries.ENTITY_TYPE, id): the key created once in ac$goblinTag. */
    @Redirect(method = "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/tags/TagKey;m_203882_(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/tags/TagKey;"))
    private static TagKey<?> bons$cachedTag(ResourceKey<?> registry, ResourceLocation id) {
        return ac$goblinTag;
    }
}
