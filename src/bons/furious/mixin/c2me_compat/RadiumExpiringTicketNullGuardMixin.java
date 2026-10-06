package bons.furious.mixin.c2me_compat;

import bons.furious.patch.c2me_compat.RadiumExpiringTickets;
import com.bawnorton.mixinsquared.TargetHandler;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.DistanceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Switch radium_experimental_tickets_spawning (Radium Re-Reforged 0.14.3), since 1.0.34: the null check that Radium's
 * experimental chunk_tickets handler lacks (Lithium #535/#571, fixed the same way in later Lithium).
 *
 * Radium's ChunkTicketManagerMixin.unregisterExpiringTicket (merged into DistanceManager.removeTicket at its updateLevel call)
 * takes positionWithExpiringTicket.get(pos) for a ticket that can expire and passes it to canNoneExpire, which calls
 * isEmpty() on it. removeTicket reaches that call for every removal, also of a timed ticket that was never added there or
 * whose position Radium's purge already dropped from that map: then get answers null and the server thread stops with a
 * NullPointerException. This redirect makes the same get call and hands the handler an empty set in place of null
 * (RadiumExpiringTickets.NONE): canNoneExpire answers true and the handler's positionWithExpiringTicket.remove(pos) finds
 * no entry and changes nothing. Every non-null answer is passed on as it is, so Radium's handler behaves exactly as before
 * wherever it did not throw. Vanilla's removeTicket around the handler is untouched.
 *
 * Decided by C2meCompatPlugin: applied whenever Radium's own chunk_tickets mixin applies (its effective option, read after
 * RadiumExperimentalOptionTrigger) and the switch's guards match. Priority 1500: applied after Radium's mixin, whose handler
 * must already be merged. require = 0, expect = 0: should Radium's handler ever not be there (its mixin dropped or
 * cancelled), there is nothing to guard and DistanceManager stays exactly as the other mixins make it instead of failing
 * the class. Radium is LGPL-3.0; no Radium code is carried, only the redirect of its call.
 */
@Mixin(value = DistanceManager.class, priority = 1500, remap = false)
public abstract class RadiumExpiringTicketNullGuardMixin {
    @TargetHandler(mixin = "me.jellysquid.mods.lithium.mixin.experimental.chunk_tickets.ChunkTicketManagerMixin", name = "unregisterExpiringTicket")
    @Redirect(method = "@MixinSquared:Handler", require = 0, expect = 0,
            at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;get(J)Ljava/lang/Object;"))
    private Object bons$expiringTicketsOrNone(Long2ObjectOpenHashMap<?> positionWithExpiringTicket, long pos) {
        Object tickets = positionWithExpiringTicket.get(pos);
        return tickets != null ? tickets : RadiumExpiringTickets.NONE;
    }
}
