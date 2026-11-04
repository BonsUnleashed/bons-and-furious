package bons.furious.mixin.sanguinenetworks_coo49;

import bons.furious.patch.sanguinenetworks_coo49.SacrificerSync;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.luna.sanguine_networks.block.entity.VSBlockEntity;
import dev.shadowsoffire.placebo.cap.ModifiableEnergyStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * sanguinenetworks_sync_relevant_only (Sanguine Neural Networks 2.0.1, server side; kind change, off by default; no Sanguine
 * code is carried). As sanguinenetworks_sync_dedup, but the comparison leaves the energy buffer out and looks at the progress
 * only for whether it is zero, so a running sacrificer's packet goes out when a cycle starts or ends or anything else
 * changes, not every tick. Trade-off: the client's copy of the progress and energy is refreshed only with those packets. See
 * SacrificerSync.
 */
@SuppressWarnings({"rawtypes"})
@Mixin(value = VSBlockEntity.class, remap = false)
public abstract class SacrificerSyncRelevantMixin extends BlockEntity {
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

    @Unique
    private SacrificerSync.Snapshot bons$sanguineRelevantSnapshot;
    @Unique
    private boolean bons$sanguineRelevantMustSend;

    protected SacrificerSyncRelevantMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "m_58483_()Lnet/minecraft/network/protocol/Packet;", at = @At("HEAD"), cancellable = true)
    private void bons$sanguineRelevantPacket(CallbackInfoReturnable<Packet> cir) {
        SacrificerSync.Snapshot s = this.bons$sanguineRelevantSnapshot;
        if (!SacrificerSync.relevantEnabled) {
            if (s != null) this.bons$sanguineRelevantSnapshot = null;
            return;
        }
        if (s == null || this.bons$sanguineRelevantMustSend || ((Object) this).getClass() != VSBlockEntity.class
                || !SacrificerSync.valuesSame(s, true, this.energyStorage.getEnergyStored(), this.catalystUses, this.maxCatalystUses, this.catalystMult,
                        this.toProduce, this.progress, this.maxProgress, this.altarPos)
                || !SacrificerSync.slotsSame(s, this, this.getCapabilities(), this.itemHandler)) return;
        SacrificerSync.leftOut(1);
        cir.setReturnValue(null);
    }

    @ModifyReturnValue(method = "m_58483_()Lnet/minecraft/network/protocol/Packet;", at = @At("RETURN"))
    private Packet bons$sanguineRelevantSent(Packet p) {
        if (!SacrificerSync.relevantEnabled || p == null || ((Object) this).getClass() != VSBlockEntity.class) return p;
        this.bons$sanguineRelevantMustSend = false;
        SacrificerSync.Snapshot s = this.bons$sanguineRelevantSnapshot;
        if (s == null) s = this.bons$sanguineRelevantSnapshot = new SacrificerSync.Snapshot();
        SacrificerSync.capture(s, this.energyStorage.getEnergyStored(), this.catalystUses, this.maxCatalystUses, this.catalystMult, this.toProduce,
                this.progress, this.maxProgress, this.altarPos, SacrificerSync.tagOf(p));
        return p;
    }

    @ModifyReturnValue(method = "m_5995_()Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
    private CompoundTag bons$sanguineRelevantTag(CompoundTag tag) {
        SacrificerSync.Snapshot s = this.bons$sanguineRelevantSnapshot;
        if (s == null || this.bons$sanguineRelevantMustSend || !SacrificerSync.relevantEnabled) return tag;
        if (!SacrificerSync.valuesSame(s, true, this.energyStorage.getEnergyStored(), this.catalystUses, this.maxCatalystUses, this.catalystMult,
                this.toProduce, this.progress, this.maxProgress, this.altarPos) || !SacrificerSync.slotsSame(s, this, this.getCapabilities(), this.itemHandler))
            this.bons$sanguineRelevantMustSend = true;
        return tag;
    }
}
