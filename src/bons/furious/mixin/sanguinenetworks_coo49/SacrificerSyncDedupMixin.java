package bons.furious.mixin.sanguinenetworks_coo49;

import bons.furious.patch.sanguinenetworks_coo49.SacrificerSync;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.luna.sanguine_networks.block.entity.VSBlockEntity;
import dev.shadowsoffire.placebo.cap.ModifiableEnergyStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * sanguinenetworks_sync_dedup (Sanguine Neural Networks 2.0.1, server side; kind change, off by default: exact for players,
 * but each packet that goes out costs the server about 4 % more to build than the mod's own class; no Sanguine code is
 * carried). VSBlockEntity.getUpdatePacket (asked by the chunk holder after the per-tick sync()) is the mod's
 * ClientboundBlockEntityDataPacket.create(this); the switch redirects that call: null when the values the update tag is
 * made of equal those of the last packet this block entity sent and no different state was handed out since, without
 * building the tag; otherwise the same create call, and the packet keeps its values. Every hand-out of the state
 * (getUpdateTag: the chunk data of a player who starts tracking the chunk, and the build itself, which is sent anyway)
 * that differs from the kept values makes the next packet go out. Block entities with Forge persistent data, attached
 * capabilities or stacks with capability data, and subclasses, always send. See SacrificerSync.
 */
@Mixin(value = VSBlockEntity.class, remap = false)
public abstract class SacrificerSyncDedupMixin extends BlockEntity {
    @Shadow
    @Final
    private ItemStackHandler itemHandler;
    @Shadow
    private ModifiableEnergyStorage energyStorage;
    @Shadow
    private int catalystUses;
    @Shadow
    private int maxCatalystUses;
    @Shadow
    private int progress;
    @Shadow
    private int maxProgress;
    @Shadow
    private int toProduce;
    @Shadow
    private float catalystMult;
    @Shadow
    private BlockPos altarPos;

    /** The values of the last packet this block entity sent (null: none kept, the next packet goes out). */
    @Unique
    private SacrificerSync.Snapshot bons$sanguineSyncSnapshot;
    /** A state different from the kept one was handed out since: the next packet goes out. */
    @Unique
    private boolean bons$sanguineSyncMustSend;
    /** Shadow mode only: the tag of the last packet sent. */
    @Unique
    private CompoundTag bons$sanguineSyncShadowTag;

    protected SacrificerSyncDedupMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Redirect(method = "m_58483_()Lnet/minecraft/network/protocol/Packet;", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ClientboundBlockEntityDataPacket;m_195640_(Lnet/minecraft/world/level/block/entity/BlockEntity;)Lnet/minecraft/network/protocol/game/ClientboundBlockEntityDataPacket;"),
            require = 1, allow = 1)
    private ClientboundBlockEntityDataPacket bons$sanguineSyncPacket(BlockEntity be) {
        // one call site of the mod's create for every path, so the switch on and off compile alike
        boolean on = SacrificerSync.dedupEnabled && ((Object) this).getClass() == VSBlockEntity.class;
        SacrificerSync.Snapshot s = this.bons$sanguineSyncSnapshot;
        boolean wouldSkip = false;
        if (on) {
            if (s != null && !this.bons$sanguineSyncMustSend && SacrificerSync.valuesSame(s, false, this.energyStorage.getEnergyStored(), this.catalystUses,
                    this.maxCatalystUses, this.catalystMult, this.toProduce, this.progress, this.maxProgress, this.altarPos)
                    && SacrificerSync.slotsSame(s, this, this.getCapabilities(), this.itemHandler)) {
                if (!SacrificerSync.SHADOW) {
                    SacrificerSync.leftOut(0);
                    return null;
                }
                wouldSkip = true;
            }
        } else if (s != null && !SacrificerSync.dedupEnabled) {
            this.bons$sanguineSyncSnapshot = s = null;
        }
        ClientboundBlockEntityDataPacket p = ClientboundBlockEntityDataPacket.m_195640_(be);
        if (!on) return p;
        CompoundTag tag = p.m_131708_();
        if (wouldSkip) {
            SacrificerSync.shadowCheck("sanguinenetworks_sync_dedup", this.bons$sanguineSyncShadowTag, tag);
            return p;
        }
        this.bons$sanguineSyncMustSend = false;
        if (s == null) s = this.bons$sanguineSyncSnapshot = new SacrificerSync.Snapshot();
        SacrificerSync.capture(s, this.energyStorage.getEnergyStored(), this.catalystUses, this.maxCatalystUses, this.catalystMult, this.toProduce,
                this.progress, this.maxProgress, this.altarPos, tag);
        if (SacrificerSync.SHADOW) this.bons$sanguineSyncShadowTag = tag;
        return p;
    }

    @ModifyReturnValue(method = "m_5995_()Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
    private CompoundTag bons$sanguineSyncTag(CompoundTag tag) {
        SacrificerSync.Snapshot s = this.bons$sanguineSyncSnapshot;
        if (s == null || this.bons$sanguineSyncMustSend || !SacrificerSync.dedupEnabled) return tag;
        if (!SacrificerSync.valuesSame(s, false, this.energyStorage.getEnergyStored(), this.catalystUses, this.maxCatalystUses, this.catalystMult,
                this.toProduce, this.progress, this.maxProgress, this.altarPos) || !SacrificerSync.slotsSame(s, this, this.getCapabilities(), this.itemHandler))
            this.bons$sanguineSyncMustSend = true;
        return tag;
    }
}
