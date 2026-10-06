package bons.furious.patch.c2me_compat;

import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;

/**
 * Switch radium_experimental_tickets_spawning (Radium Re-Reforged 0.14.3), since 1.0.34: helper for
 * RadiumExpiringTicketNullGuardMixin. No Radium code here.
 *
 * NONE stands in for "no entry" where Radium's experimental chunk_tickets handler looks up the expiring tickets of a position
 * it does not track: an empty set, for which Radium's canNoneExpire answers true without touching anything. It is only read
 * (isEmpty), never stored and never changed.
 */
public final class RadiumExpiringTickets {
    public static final SortedArraySet<Ticket<?>> NONE = SortedArraySet.m_14246_(0);

    private RadiumExpiringTickets() {
    }
}
