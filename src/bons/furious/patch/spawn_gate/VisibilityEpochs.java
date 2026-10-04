package bons.furious.patch.spawn_gate;

/**
 * vanilla_spawn_gate_visibility_memo: implemented by PersistentEntitySectionManager through
 * bons.furious.mixin.spawn_gate.EntityManagerEpochsMixin. One long epoch per group of 8x8-chunk regions
 * (SpawnGate.group), bumped whenever chunkVisibility may change there.
 */
public interface VisibilityEpochs {
    long[] bons$visEpochs();
}
