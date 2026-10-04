package bons.furious.mixin.vanilla_search;

import bons.furious.patch.vanilla_search.RepellentSearch;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.sensing.HoglinSpecificSensor;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * vanilla_repellent_search_sections (Minecraft 1.21.1 with NeoForge 21.1.252; server side): wraps
 * HoglinSpecificSensor.findNearestRepellent (BlockPos.findClosestMatch(hoglin.blockPosition(), 8, 4, test)).
 * RepellentSearch.find visits the same 2,601 positions in the same order with the same chunk lookups and runs the
 * sensor's own test (the original's lambda lambda$findNearestRepellent$1, same name and descriptor in 1.21.1) wherever a
 * section may hold a hoglin_repellents state; other positions are a pure false and are skipped; when it cannot prove
 * that, the original method runs. Our own logic only; no Minecraft code is carried.
 *
 * Ported to 1.21.1: Mojang names; findNearestRepellent, its lambda and findClosestMatch/withinManhattan are unchanged
 * (the withinManhattan iterator is the anonymous class BlockPos$3 on 1.21.1, was BlockPos$2).
 */
@Mixin(value = HoglinSpecificSensor.class, remap = false)
public abstract class HoglinRepellentSearchMixin {
    @Shadow
    private static boolean lambda$findNearestRepellent$1(ServerLevel level, BlockPos pos) {
        throw new AssertionError();
    }

    @WrapMethod(method = "findNearestRepellent")
    private Optional<BlockPos> bons$findNearestRepellent(ServerLevel level, Hoglin hoglin, Operation<Optional<BlockPos>> original) {
        return RepellentSearch.find(level, hoglin, BlockTags.HOGLIN_REPELLENTS, p -> lambda$findNearestRepellent$1(level, p), original);
    }
}
