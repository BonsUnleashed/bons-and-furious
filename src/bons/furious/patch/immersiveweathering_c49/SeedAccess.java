package bons.furious.patch.immersiveweathering_c49;

import java.util.concurrent.atomic.AtomicLong;

/**
 * immersiveweathering_growth_roll_batch: the seed of a LegacyRandomSource, added to that vanilla class by our mixin (read
 * and compare-and-set exactly as LegacyRandomSource.next does).
 */
public interface SeedAccess {
    AtomicLong bons$iwC49Seed();
}
