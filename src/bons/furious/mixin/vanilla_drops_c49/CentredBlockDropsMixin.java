package bons.furious.mixin.vanilla_drops_c49;

import bons.furious.patch.vanilla_drops_c49.CentredDrops;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * vanilla_block_drops_centred (Minecraft 1.20.1 / Forge 47.4.28, server; a trade-off, off by default): the three random
 * offsets of Block.popResource(Level, BlockPos, ItemStack) (m_49840_) read 0, and its item supplier (m_152407_) builds the
 * item entity with the straight motion (0, 0.2, 0) instead of a random horizontal one (CentredDrops). Injectors only.
 */
@Mixin(value = Block.class, remap = false)
public abstract class CentredBlockDropsMixin {
    @Redirect(method = "m_49840_(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;m_216263_(Lnet/minecraft/util/RandomSource;DD)D"),
            require = 3, allow = 3)
    private static double bons$centredOffset(RandomSource random, double min, double max) {
        return CentredDrops.offset(random, min, max);
    }

    @Redirect(method = "m_152407_(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At(value = "NEW", target = "(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/item/ItemEntity;"),
            require = 1, allow = 1)
    private static ItemEntity bons$straightHop(Level level, double x, double y, double z, ItemStack stack) {
        return CentredDrops.item(level, x, y, z, stack);
    }
}
