package bons.furious.mixin.mcreator_constant_tag_keys;

import bons.furious.patch.mcreator_constant_tag_keys.TagCallers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * cnc_constant_tag_keys (Critters n' Crawlers 2.2.5 for Minecraft 1.21.1; both sides; since 1.0.49), on the
 * mcreator_constant_tag_keys layer.
 *
 * SasquatchWoodKnockProcedure.execute walks a 6 x 6 x 6 box around every sasquatch every tick and tests each block state
 * against {@code BlockTags.create(ResourceLocation.parse("minecraft:logs"))}: 216 locations and interner lookups per sasquatch
 * per tick. The first location factory call of the method (ResourceLocation.parse of that id; the guard pins the bytecode)
 * and the BlockTags.create call are redirected to the layer: the location and the canonical block tag key (BlockTags.LOGS
 * itself), made once. The block reads, the tag tests, the random knock and the knock sound's own location (the second
 * parse) are unchanged. Our own logic only; the mod's code stays in its jar.
 */
@Mixin(targets = "net.imasillylittleguy.cnc.procedures.SasquatchWoodKnockProcedure", remap = false)
public abstract class CncSasquatchKnockTagMixin {
    private static final String EXECUTE = "execute(Lnet/neoforged/bus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V";

    @Redirect(method = EXECUTE, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/resources/ResourceLocation;parse(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;", ordinal = 0),
            require = 1, allow = 1, remap = false)
    private static ResourceLocation bons$tagId(String id) {
        return TagCallers.CNC.location(id);
    }

    @Redirect(method = EXECUTE, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/tags/BlockTags;create(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/tags/TagKey;"),
            require = 1, allow = 1, remap = false)
    private static TagKey<Block> bons$blockTag(ResourceLocation id) {
        return TagCallers.CNC.blockTag(id);
    }
}
