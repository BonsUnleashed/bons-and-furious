package bons.furious.mixin.basalt_guards;

import bons.furious.patch.basalt_guards.BasaltGuards;
import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.BasaltColumnsFeature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * basalt_guards_vanilla_first (Minecraft 1.21.1, tested build NeoForge 21.1.252; logical server): BasaltColumnsFeature.canPlaceAt,
 * wrapped as a whole with MixinExtras @WrapMethod (so the structure guards other mods inject at its HEAD are inside the
 * wrapped call). Vanilla's two-read test is evaluated first through vanilla's own helper (isAirOrLavaOcean) and list
 * (CANNOT_PLACE_ON), the position moved down and back up as vanilla does; when it says no the answer is false and the
 * guards do not run, otherwise the original runs unchanged. The guards can only answer false (BasaltGuards checks every
 * merged handler once and steps aside for anything unknown), so the result is the original's for every input. See
 * BasaltGuards.
 *
 * Ported to 1.21.1: canPlaceAt, isAirOrLavaOcean and CANNOT_PLACE_ON are unchanged; Mojang names, nothing else changed
 * here (the known guards are 1.21.1 builds now, see BasaltGuards).
 */
@Mixin(value = BasaltColumnsFeature.class, remap = false)
public abstract class BasaltColumnsFeatureVanillaFirstMixin {
    @Shadow
    @Final
    private static ImmutableList<Block> CANNOT_PLACE_ON;

    @Shadow
    private static boolean isAirOrLavaOcean(LevelAccessor level, int seaLevel, BlockPos pos) {
        throw new AssertionError();
    }

    @WrapMethod(method = "canPlaceAt")
    private static boolean bons$vanillaFirst(LevelAccessor level, int seaLevel, BlockPos.MutableBlockPos pos, Operation<Boolean> original) {
        if (!BasaltGuards.vanillaFirst(BasaltColumnsFeature.class)) return original.call(level, seaLevel, pos);
        boolean vanilla = bons$vanillaTest(level, seaLevel, pos);
        if (BasaltGuards.SHADOW) {
            boolean answer = original.call(level, seaLevel, pos);
            BasaltGuards.shadow(vanilla, answer);
            return answer;
        }
        return vanilla && original.call(level, seaLevel, pos);
    }

    /** Vanilla's test: the spot is air or lava ocean, and the block below is neither air nor in CANNOT_PLACE_ON. */
    @Unique
    private static boolean bons$vanillaTest(LevelAccessor level, int seaLevel, BlockPos.MutableBlockPos pos) {
        if (!isAirOrLavaOcean(level, seaLevel, pos)) return false;
        BlockState below = level.getBlockState(pos.move(Direction.DOWN));
        pos.move(Direction.UP);
        return !below.isAir() && !CANNOT_PLACE_ON.contains(below.getBlock());
    }
}
