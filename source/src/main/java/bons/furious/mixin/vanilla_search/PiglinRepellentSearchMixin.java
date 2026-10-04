package bons.furious.mixin.vanilla_search;

import bons.furious.patch.vanilla_search.RepellentSearch;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.PiglinSpecificSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * vanilla_repellent_search_sections (Minecraft 1.21.1 with NeoForge 21.1.252; server side): wraps
 * PiglinSpecificSensor.findNearestRepellent (BlockPos.findClosestMatch(entity.blockPosition(), 8, 4, test)).
 * RepellentSearch.find visits the same 2,601 positions in the same order with the same chunk lookups and runs the
 * sensor's own test (isValidRepellent, the method the original's lambda calls) wherever a section may hold a
 * piglin_repellents state; other positions are a pure false and are skipped; when it cannot prove that, the original
 * method runs. Our own logic only; no Minecraft code is carried.
 *
 * Ported to 1.21.1: Mojang names; findNearestRepellent, isValidRepellent and findClosestMatch/withinManhattan are
 * unchanged.
 */
@Mixin(value = PiglinSpecificSensor.class, remap = false)
public abstract class PiglinRepellentSearchMixin {
    @Shadow
    private static boolean isValidRepellent(ServerLevel level, BlockPos pos) {
        throw new AssertionError();
    }

    @WrapMethod(method = "findNearestRepellent")
    private static Optional<BlockPos> bons$findNearestRepellent(ServerLevel level, LivingEntity entity, Operation<Optional<BlockPos>> original) {
        return RepellentSearch.find(level, entity, BlockTags.PIGLIN_REPELLENTS, p -> isValidRepellent(level, p), original);
    }
}
