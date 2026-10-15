package bons.furious.patch.benssharks;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * benssharks_tick_listeners: per-level bookkeeping, one instance per Level (field added by
 * bons.furious.mixin.benssharks.LevelStateMixin). Every set is identity-based and keeps insertion order. Each Level is only
 * ever touched by its own thread (server levels by the server thread, the client level by the render thread).
 */
public final class LevelState {
    /** Server: loaded entities of the SmoothTurns classes, in the order ServerLevel.getAllEntities() yields them. */
    public final ReferenceLinkedOpenHashSet<Entity> sharks = new ReferenceLinkedOpenHashSet<>();
    /** Server: loaded ThrownTrident entities (SeekingHandler), same order rule. */
    public final ReferenceLinkedOpenHashSet<Entity> tridents = new ReferenceLinkedOpenHashSet<>();
    /** Both sides: entities that had Bleeding during the last tick (or just got it). */
    public ReferenceLinkedOpenHashSet<LivingEntity> bleedNow = new ReferenceLinkedOpenHashSet<>();
    /** Both sides: entity ids already given the IfWearingArmor check this tick. */
    public final IntOpenHashSet armorDone = new IntOpenHashSet();
    public long armorTick = Long.MIN_VALUE;
}
