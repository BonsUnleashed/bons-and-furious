package bons.furious.patch.supplementaries_lean;

import com.mojang.logging.LogUtils;
import java.util.concurrent.atomic.AtomicLong;
import net.mehvahdjukaar.supplementaries.platform.CapabilityHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.slf4j.Logger;

/**
 * Bons and Furious switch supplementaries_sign_ink_sync_filter (Supplementaries 1.21.1-4.0.2 for NeoForge 1.21.1; server
 * side, including the integrated server).
 *
 * What Supplementaries does. Its neoforge ChunkHolderMixin.sendBlockEntityCaps runs at the HEAD of
 * ClientboundLevelChunkPacketData.BlockEntityInfo.create, once for every block entity of every chunk packet built. For a
 * block entity of a ServerLevel it builds a lambda and a TickTask and queues them on the server (MinecraftServer.tell: a
 * queue node and a wake-up of the server thread); the server runs the task within the next few ticks. The task asks the
 * level for Supplementaries' ANTIQUE_TEXT_CAP block capability (level.getCapability(cap, pos, be.getBlockState(), be,
 * null)) and, only when it answers, sends ClientBoundSyncAntiqueInk to the players tracking that chunk. Supplementaries
 * registers that capability (CapabilityHandler.register) only for SignBlock blocks, with a provider that answers null for
 * any block entity that is not a SignBlockEntity (hanging signs included as SignBlockEntity).
 *
 * What the switch does. On the server thread, for a block entity that is not a SignBlockEntity, the same capability lookup
 * is made at once, with the same block entity, position and block entity state; when it answers null the handler returns
 * before the lambda and the TickTask are created. The task would have made the same lookup a few ticks later and done
 * nothing: NeoForge's BlockCapability.getCapability, with the state and block entity given, reads nothing from the level
 * and asks only the providers registered for the state's block, which are fixed once RegisterCapabilitiesEvent has run;
 * for a block without providers the answer is null without side effects, and Supplementaries' own provider answers null
 * for every non-sign block entity. Dropping a task that does nothing changes no other task's order, only the server thread
 * is not woken for it. Signs, block entities whose lookup answers, other threads and the runtime switch off take
 * Supplementaries' path unchanged (a non-sign block entity whose lookup answers is asked once more, by the task, as
 * before).
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
        Level level = te.getLevel();
        if (!(level instanceof ServerLevel serverLevel) || !serverLevel.getServer().isSameThread()) return false;
        boolean absent = serverLevel.getCapability(CapabilityHandler.ANTIQUE_TEXT_CAP, te.getBlockPos(), te.getBlockState(), te, null) == null;
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
