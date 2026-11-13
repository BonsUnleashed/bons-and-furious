package bons.furious.mixin.mobgrindingutils_coo49;

import bons.furious.patch.mobgrindingutils_coo49.FanSync;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import mob_grinding_utils.tile.TileEntityFan;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * mobgrindingutils_fan_sync_dedup (Mob Grinding Utils 1.1.10+mc1.21.1, server side; NeoForge 21.1; no Mob Grinding Utils
 * code is carried). TileEntityFan.getUpdatePacket (asked by the chunk holder after the fan's per-tick sendBlockUpdated)
 * returns null when the values its update tag is made of equal those of the last packet this fan sent and no different
 * state was handed out since (getUpdateTag, e.g. the chunk data of a player who starts tracking the fan, is watched for
 * that), without building the tag. Otherwise the packet goes out: for a fan without NeoForge persistent data or data
 * attachments it is ClientboundBlockEntityDataPacket.create(this), the packet the mod's method returns, without the tag the
 * mod's method builds first and drops (a pure computation); any other fan runs the mod's own method and is never left out.
 * Exact variant: identical packets are not sent (clients keep their fan's objects); every client's fan state after each
 * broadcast is the original's. See FanSync.
 */
@Mixin(value = TileEntityFan.class, remap = false)
public abstract class FanSyncDedupMixin extends BlockEntity {
    @Shadow public boolean showRenderBox;
    @Shadow float xPos;
    @Shadow float yPos;
    @Shadow float zPos;
    @Shadow float xNeg;
    @Shadow float yNeg;
    @Shadow float zNeg;

    /** The values of the last packet this fan sent (null: none kept, the next packet goes out). */
    @Unique
    private FanSync.Snapshot bons$mobgrindingutilsFanSnapshot;
    /** A state different from the kept one was handed out since: the next packet goes out. */
    @Unique
    private boolean bons$mobgrindingutilsFanMustSend;
    /** This fan is building its own packet (its getUpdateTag call belongs to it, not to a reader). */
    @Unique
    private boolean bons$mobgrindingutilsFanBuilding;
    /** Shadow mode only: the tag of the last packet sent. */
    @Unique
    private CompoundTag bons$mobgrindingutilsFanShadowTag;
    /** Requests left out in a row (the FanSync.REFRESH-th one in a row goes out). */
    @Unique
    private int bons$mobgrindingutilsFanQuiet;
    /** The game time of the last request (a second request at the same game time did not come from the chunk holder's broadcast). */
    @Unique
    private long bons$mobgrindingutilsFanAsked = Long.MIN_VALUE;
    /** The fan had NeoForge persistent data (never taken away) or data attachments: the mod's own method from then on. */
    @Unique
    private boolean bons$mobgrindingutilsFanOwn;

    protected FanSyncDedupMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @WrapMethod(method = "getUpdatePacket()Lnet/minecraft/network/protocol/game/ClientboundBlockEntityDataPacket;")
    private ClientboundBlockEntityDataPacket bons$mobgrindingutilsFanPacket(Operation<ClientboundBlockEntityDataPacket> original) {
        if (this.bons$mobgrindingutilsFanOwn) return original.call();
        if (!FanSync.enabled) {
            this.bons$mobgrindingutilsFanSnapshot = null;
            return original.call();
        }
        if (!FanSync.blockEntityPlain(this)) {
            this.bons$mobgrindingutilsFanOwn = true;
            this.bons$mobgrindingutilsFanSnapshot = null;
            return original.call();
        }
        Container inv = (Container) (Object) this;
        FanSync.Snapshot s = this.bons$mobgrindingutilsFanSnapshot;
        // FanSync, "A client that lost its copy": a second request at the same game time and every FanSync.REFRESH-th request in a
        // row are answered in full
        long now = this.level == null ? Long.MIN_VALUE : this.level.getGameTime();
        boolean again = now == this.bons$mobgrindingutilsFanAsked;
        this.bons$mobgrindingutilsFanAsked = now;
        if (s != null && !this.bons$mobgrindingutilsFanMustSend
                && FanSync.unchanged(s, this.showRenderBox, this.xPos, this.yPos, this.zPos, this.xNeg, this.yNeg, this.zNeg, inv)) {
            if (FanSync.SHADOW) {
                ClientboundBlockEntityDataPacket p = original.call();
                FanSync.shadowCheck(this.bons$mobgrindingutilsFanShadowTag, FanSync.tagOf(p));
                return p;
            }
            if (!again && ++this.bons$mobgrindingutilsFanQuiet < FanSync.REFRESH) {
                FanSync.leftOut();
                return null;
            }
        }
        ClientboundBlockEntityDataPacket p;
        this.bons$mobgrindingutilsFanBuilding = true;
        try {
            p = ClientboundBlockEntityDataPacket.create(this);
        } finally {
            this.bons$mobgrindingutilsFanBuilding = false;
        }
        this.bons$mobgrindingutilsFanQuiet = 0;
        // a request that did not come from the per-tick broadcast went to one receiver only: the next broadcast goes out as well
        this.bons$mobgrindingutilsFanMustSend = again;
        if (s == null) s = this.bons$mobgrindingutilsFanSnapshot = new FanSync.Snapshot();
        FanSync.capture(s, this.showRenderBox, this.xPos, this.yPos, this.zPos, this.xNeg, this.yNeg, this.zNeg, inv);
        if (FanSync.SHADOW) this.bons$mobgrindingutilsFanShadowTag = FanSync.tagOf(p);
        return p;
    }

    @ModifyReturnValue(method = "getUpdateTag(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
    private CompoundTag bons$mobgrindingutilsFanTag(CompoundTag tag) {
        FanSync.Snapshot s = this.bons$mobgrindingutilsFanSnapshot;
        if (s == null || this.bons$mobgrindingutilsFanBuilding || this.bons$mobgrindingutilsFanMustSend || !FanSync.enabled) return tag;
        if (!FanSync.blockEntityPlain(this)
                || !FanSync.unchanged(s, this.showRenderBox, this.xPos, this.yPos, this.zPos, this.xNeg, this.yNeg, this.zNeg, (Container) (Object) this))
            this.bons$mobgrindingutilsFanMustSend = true;
        return tag;
    }
}
