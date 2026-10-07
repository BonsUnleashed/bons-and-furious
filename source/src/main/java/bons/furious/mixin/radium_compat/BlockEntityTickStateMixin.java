package bons.furious.mixin.radium_compat;

import bons.furious.patch.radium_compat.TagEpoch;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_block_entity_tick_state (Minecraft 1.20.1 / Forge 47.4.16), part 1 of 3: the block entity tick.
 *
 * Every tick of every ticking block entity looked its own block state up in the chunk (section, palette, bit storage) and
 * then asked BlockEntityType.isValid, which five mods in this pack hook (BCLib, DeltaBox Lib, Jaden's Nether Expansion,
 * Kiwi, Spawn), so each check ran five handlers. Radium's mixin.world.block_entity_ticking.support_cache removes both
 * steps; Radium Re-Reforged ships it switched off because its LevelChunk part cannot apply on Forge (see part 3).
 *
 * The state now comes from the block entity's own blockState field, which LevelChunk.setBlockState keeps equal to the block
 * in the world (part 3 closes the two cases where vanilla leaves it behind). The field is read directly, not through
 * getBlockState(): Immersive Engineering overrides that getter to keep returning a multiblock part's old state after the
 * part became invalid, and vanilla does not tick such a part. isValid is asked once per (type, state) pair and remembered
 * by this ticker. In vanilla it is a set lookup; the five mods that extend it decide from the type and the block, and
 * DeltaBox Lib from block tags, so the answer is asked again when the state or the type changes and after every tag reload
 * (TagEpoch).
 *
 * 1.0.34: the field is trusted only after this ticker once saw it equal to the chunk's state; until then every tick looks
 * the state up in the chunk as vanilla does and uses that. A block entity can enter a chunk with another state than the
 * block in the world (made during world generation, where ProtoChunk.setBlockState never updates it: a structure's block
 * entity is created before the template waterlogs or reshapes its block; or installed with Level.setBlockEntity), and
 * LevelChunk.setBlockState binds a new ticker for every state change, so one equal look per ticker is enough.
 */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity", remap = false)
public abstract class BlockEntityTickStateMixin {
    @Shadow
    @Final
    private BlockEntity blockEntity;

    @Unique
    private BlockEntityType<?> bons$checkedType;
    @Unique
    private BlockState bons$checkedState;
    @Unique
    private boolean bons$checkedValid;
    @Unique
    private int bons$checkedEpoch;
    /** 1.0.34: the block entity's field equalled the chunk's state on a tick of this ticker. */
    @Unique
    private boolean bons$fieldVerified;

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunk;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState bons$ownState(LevelChunk chunk, BlockPos pos, Operation<BlockState> original) {
        if (this.bons$fieldVerified) return ((BlockEntityStateAccessor) this.blockEntity).bons$blockStateField();
        BlockState world = original.call(chunk, pos);   // 1.0.34: vanilla's lookup until the field is seen equal to it
        if (world == ((BlockEntityStateAccessor) this.blockEntity).bons$blockStateField()) this.bons$fieldVerified = true;
        return world;
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/BlockEntityType;isValid(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean bons$rememberedValid(BlockEntityType<?> type, BlockState state, Operation<Boolean> original) {
        int epoch = TagEpoch.epoch;
        if (state != this.bons$checkedState || type != this.bons$checkedType || epoch != this.bons$checkedEpoch) {
            this.bons$checkedValid = original.call(type, state);
            this.bons$checkedType = type;
            this.bons$checkedState = state;
            this.bons$checkedEpoch = epoch;
        }
        return this.bons$checkedValid;
    }
}
