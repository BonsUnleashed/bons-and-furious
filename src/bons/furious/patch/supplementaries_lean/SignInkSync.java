package bons.furious.patch.supplementaries_lean;

import bons.furious.mixin.supplementaries_lean.CapabilityProviderValidAccessor;
import com.mojang.logging.LogUtils;
import java.util.concurrent.atomic.AtomicLong;
import net.mehvahdjukaar.supplementaries.common.capabilities.CapabilityHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.slf4j.Logger;

/**
 * Bons and Furious switch supplementaries_sign_ink_sync_filter (Supplementaries 1.20-3.1.41 for Minecraft 1.20.1 Forge;
 * server side, including the integrated server). SRG member names.
 *
 * What Supplementaries does. Its forge ChunkHolderMixin.sendBlockEntityCaps runs at the HEAD of
 * ClientboundLevelChunkPacketData.BlockEntityInfo.create, once for every block entity of every chunk packet built. For a
 * block entity of a ServerLevel it builds a lambda and a TickTask and queues them on the server (MinecraftServer.tell: a
 * queue node and a wake-up of the server thread); the server runs the task within the next few ticks. The task asks the
 * block entity for Supplementaries' ANTIQUE_TEXT_CAP and, only when present, sends ClientBoundSyncAntiqueInk to the
 * players tracking that chunk. Supplementaries attaches that capability to SignBlockEntity (hanging signs included) only.
 *
 * What the switch does. On the server thread, for a block entity that is not a SignBlockEntity, whose capabilities are not
 * invalidated, the same getCapability(ANTIQUE_TEXT_CAP) question is asked at once; when the answer is empty the handler
 * returns before the lambda and the TickTask are created. The task would have asked the same question a few ticks later
 * and done nothing: capabilities are gathered once when the block entity is built and are never added later, so an empty
 * answer for valid capabilities stays empty (a block entity removed meanwhile only invalidates them); dropping a task that
 * does nothing changes no other task's order, only the server thread is not woken for it. Signs, block entities whose
 * capability is present, invalidated capabilities, other threads and the runtime switch off take Supplementaries' path
 * unchanged (a non-sign block entity with the capability present is asked once more, by the task, as before).
 *
 * -Dbons_and_furious.supplementariesSignInkSyncFilter=false switches it off at run time.
 * -Dbons_and_furious.supplementariesSignInkSyncFilter.shadow=true (verification runs only): nothing is skipped;
 * SHADOW_CHECKS counts the block entities the switch would have asked and SHADOW_SKIPPABLE those it would have skipped.
 */
public final class SignInkSync {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.supplementariesSignInkSyncFilter", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.supplementariesSignInkSyncFilter.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong(), SHADOW_SKIPPABLE = new AtomicLong();
    private static volatile boolean announced;

    private SignInkSync() {
    }

    /** True when Supplementaries' sendBlockEntityCaps handler may return before queueing its task for this block entity. */
    public static boolean skip(BlockEntity te) {
        if (!enabled || te == null || te instanceof SignBlockEntity) return false;
        Level level = te.m_58904_();
        if (!(level instanceof ServerLevel serverLevel) || !serverLevel.m_7654_().m_18695_()) return false;
        if (!((CapabilityProviderValidAccessor) (Object) te).bons$suppCapsValid()) return false;
        boolean absent = !te.getCapability(CapabilityHandler.ANTIQUE_TEXT_CAP).isPresent();
        if (SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            if (absent) SHADOW_SKIPPABLE.incrementAndGet();
            return false;
        }
        if (absent && !announced) {
            announced = true;
            LOGGER.info("Bons and Furious: supplementaries_sign_ink_sync_filter applies (Supplementaries' antique ink sync task is no longer queued for block entities without antique ink)");
        }
        return absent;
    }
}
