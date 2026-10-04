package bons.furious.mixin.vanilla_c2;

import bons.furious.patch.vanilla_c2.SectionXOverflow;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import net.minecraft.world.level.entity.EntitySectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * vanilla_entity_section_x_overflow (a FIX; Minecraft 1.21.1, both sides; tested on Minecraft 1.21.1 with NeoForge
 * 21.1.252, which does not patch this class).
 *
 * Redirects the two LongSortedSet.subSet(long, long) calls of EntitySectionStorage: forEachAccessibleNonEmptySection
 * (one x column of an entity box query) and getChunkSections (one chunk column). Only the call whose
 * exclusive end wrapped to Long.MIN_VALUE (packed section x 2^21-1; the original always throws IllegalArgumentException
 * there) is answered with tailSet(start) (SectionXOverflow.overflow); every other call makes the same subSet call here.
 *
 * A @Redirect with the ordinary call inline, not a @WrapOperation: MixinExtras' Operation.call boxed the two longs into an
 * Object[] (909 bytes per box query on the real class), and a subSet made inside a separate helper method kept the JIT from
 * scalar-replacing the column view and its iterator (417 bytes per query). Inline, the handler is as cheap as the original
 * call. No other mod redirects or wraps these two calls (neighbour check); Radium's fast_retrieval inject and Valkyrien
 * Skies' shipyard re-query on forEachAccessibleNonEmptySection hook other points and are untouched.
 *
 * Ported to 1.21.1: Mojang names only; EntitySectionStorage, SectionPos packing (22/20/22 bits) and both subSet calls are
 * unchanged (Radium 0.13.1's EntitySectionStorage mixins still never call or redirect subSet).
 */
@Mixin(value = EntitySectionStorage.class, remap = false)
public abstract class EntitySectionXOverflowMixin {
    @Redirect(method = {"forEachAccessibleNonEmptySection", "getChunkSections"}, at = @At(value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/longs/LongSortedSet;subSet(JJ)Lit/unimi/dsi/fastutil/longs/LongSortedSet;"), require = 2, allow = 2)
    private LongSortedSet bons$columnRange(LongSortedSet keys, long from, long toExclusive) {
        if (toExclusive == Long.MIN_VALUE && from > toExclusive && SectionXOverflow.enabled) {
            return SectionXOverflow.overflow(keys, from);
        }
        return keys.subSet(from, toExclusive);
    }
}
