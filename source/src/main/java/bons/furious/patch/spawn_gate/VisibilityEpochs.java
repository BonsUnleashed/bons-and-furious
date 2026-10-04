package bons.furious.patch.spawn_gate;

/**
 * vanilla_spawn_gate_visibility_memo: implemented by PersistentEntitySectionManager through
 * bons.furious.mixin.spawn_gate.EntityManagerEpochsMixin. One long epoch per group of 8x8-chunk regions
 * (SpawnGate.group), bumped whenever chunkVisibility may change there.
 *
 * Ported to 1.21.1: unchanged (Minecraft 1.21.1 with NeoForge 21.1.252; same single writer of chunkVisibility).
 */
public interface VisibilityEpochs {
    long[] bons$visEpochs();
}
