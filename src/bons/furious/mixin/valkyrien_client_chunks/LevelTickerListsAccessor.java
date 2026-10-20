package bons.furious.mixin.valkyrien_client_chunks;

import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * valkyrien_client_ship_chunk_unload (Valkyrien Skies 2.4.11), client only: read access to Level's two block-entity ticker
 * lists (blockEntityTickers f_151512_, pendingBlockEntityTickers f_151503_) for the shadow verification of
 * bons.furious.patch.valkyrien_client_chunks.ShipChunkUnload. Nothing is changed; the accessors are only called when
 * -Dbons_and_furious.valkyrienClientShipChunkUnload.shadow=true.
 */
@Mixin(value = Level.class, remap = false)
public interface LevelTickerListsAccessor {
    @Accessor("f_151512_")
    List<TickingBlockEntity> bons$vccTickers();

    @Accessor("f_151503_")
    List<TickingBlockEntity> bons$vccPendingTickers();
}
