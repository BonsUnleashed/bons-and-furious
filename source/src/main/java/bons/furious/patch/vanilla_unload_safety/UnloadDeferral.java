package bons.furious.patch.vanilla_unload_safety;

/**
 * vanilla_entity_unload_snapshot: implemented by PersistentEntitySectionManager (EntityUnloadDeferralMixin). While an
 * entity-unload walk over chunksToUnload runs (begin ... end, nestable), the set changes updateChunkStatus would make are
 * queued and applied, in their order, when the outermost walk ends. Code that walks chunksToUnload in a loop of its own
 * opens the same scope through this interface. No Mojang code.
 */
public interface UnloadDeferral {
    void bons$beginUnloadWalk();

    void bons$endUnloadWalk();
}
