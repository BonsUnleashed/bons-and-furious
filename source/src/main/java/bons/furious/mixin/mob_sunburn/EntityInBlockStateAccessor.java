package bons.furious.mixin.mob_sunburn;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * vanilla_sun_burn_deferred_wet_check (Minecraft 1.21.1 with NeoForge 21.1.252; both sides, acts on the server): read-only
 * accessor for Entity's block-state memo (inBlockState, filled by getInBlockState(), cleared by baseTick and by setPosRaw
 * when the block position changes). New in the 1.21.1 port: 1.21.1's isInBubbleColumn reads the block through that memo,
 * so the wet question has a side effect (it may fill the memo) unless the memo is already set; MobSunBurnOrderMixin only
 * moves the question when it is set (see SunBurnOrder). Read only; carries no Minecraft code.
 */
@Mixin(value = Entity.class, remap = false)
public interface EntityInBlockStateAccessor {
    @Accessor("inBlockState")
    BlockState bons$inBlockState();
}
