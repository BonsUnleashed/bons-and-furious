package bons.furious.patch.elysium_sound_memo;

import bons.furious.mixin.elysium_sound_memo.HolderReferenceTagsAccessor;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.jadenxgamer.elysium_api.impl.core.datadriven.block.BlockSoundTransformer;
import net.jadenxgamer.elysium_api.impl.registry.ElysiumRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bons and Furious switch elysium_sound_transformer_memo (ElysiumAPI; 1.21.1 tested build: ElysiumAPI 1.21.1-2.0.1 on
 * Minecraft 1.21.1 / NeoForge 21.1; both sides: the server and the client ask blocks for their sound). Idea: Collections Of
 * Optimizations (elysiumapi.cacheSoundTransformers), idea text only.
 *
 * What ElysiumAPI does. Its BlockBehaviorMixin injects at the HEAD of BlockBehaviour.getSoundType(BlockState) (called for
 * every step sound of every walking entity, every block break, place, hit and fall, and by sound mods per reflection ray):
 * RegistryAccessHelper.getServer() (ServerLifecycleHooks.getCurrentServer(): empty without a running server, e.g. on a
 * client connected to another machine) .flatMap(access -> access.registryOrThrow(BLOCK_SOUND_TRANSFORMERS).stream()
 * .filter(s -> s.blocks().contains(state.getBlockHolder())).findFirst()) .ifPresent(t -> setReturnValue(t.toSoundType()))
 * (a new SoundType). A fresh Optional, stream pipeline and three lambdas, and a HolderSet probe per transformer, on every
 * call.
 *
 * What the switch does. The first matching transformer (or none) is remembered on the block (ElysiumMemoHolder, an immutable
 * Memo behind a volatile field), keyed on everything that answer depends on: the registry object and its size, the block's
 * holder and the holder's tag set object. A call with the same key gets the same BlockSoundTransformer object and the same
 * toSoundType() call (a new SoundType each time, as before); any other call runs Elysium's search (the same stream
 * expression, so the same order and the same exceptions) and remembers it. Without a running server it returns at once and
 * Elysium's handler runs (and finds no registry access).
 *
 * Why the answer is the same. Elysium's registry is a datapack registry (exactly MappedRegistry) of the server's registry
 * access, frozen after loading: a frozen MappedRegistry refuses every write (validateWrite throws), and before freezing an
 * entry is only ever appended (register uses the next id, so the size changes); datapack registries are never unfrozen or
 * cleared (NeoForge does that only for synced built-in registries). So with the same object and the same size its entries
 * and their id order (the stream's order) are the same; BlockSoundTransformer is a record. The memo is used only when every
 * transformer's block set is a vanilla HolderSet.Named (contains = holder.is(tag) = the holder's own tag set contains the
 * tag) or HolderSet.Direct (a fixed list), so the answer is a function of the holder and its tags; NeoForge's own holder sets
 * (and, or, not, any) keep Elysium's search. Holder.Reference.bindTags replaces the tag set with Set.copyOf(...) on every
 * tag reload: a different object (or the same immutable empty set when the block has no tags before and after, when the
 * answer is the same too). The tag set is read before and after a search and the result is kept only when it did not change
 * in between. Any other HolderSet class, another Registry class or no running server: Elysium's own code runs, unchanged.
 * ElysiumAPI is GPL-2.0 (Modrinth) / LGPL-2.1 (GitHub): the HEAD injection carries a modified version of its handler logic
 * (the same decisions, the search remembered).
 *
 * -Dbons_and_furious.elysiumSoundTransformerMemo=false switches it off at run time.
 * -Dbons_and_furious.elysiumSoundTransformerMemo.shadow=true (verification runs only): Elysium's code runs as shipped on
 * every call; at each memo hit the search is also run here and SHADOW_CHECKS / SHADOW_MISMATCHES count the hits whose
 * remembered transformer differs from the searched one (WARN for the first 20).
 */
public final class ElysiumSoundMemo {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.elysiumSoundTransformerMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.elysiumSoundTransformerMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Hits and searches since start (statistics for the rigs; plain longs, racy by design). */
    public static long hits, searches;
    private static final Object NONE = new Object();
    private static volatile boolean announced;
    private static volatile Checked checked;

    /**
     * Whether a registry (object + size) may be remembered: exactly a MappedRegistry (no subclass, which could override
     * its stream or size) whose every block set is a vanilla HolderSet.Named or HolderSet.Direct.
     */
    private record Checked(Registry<BlockSoundTransformer> registry, int size, boolean memoable) {}

    /** One block's remembered answer: the first matching transformer, or NONE. */
    private record Memo(Registry<BlockSoundTransformer> registry, int size, Holder<Block> holder, Set<?> tags, Object found) {}

    private ElysiumSoundMemo() {
    }

    /**
     * HEAD of Elysium's handler: true when the call was answered here (the caller cancels the handler body), false to let
     * Elysium's own code run.
     */
    public static boolean answer(ElysiumMemoHolder slot, BlockState state, CallbackInfoReturnable<SoundType> theirs) {
        if (!enabled) return false;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return false;                 // RegistryAccessHelper.getServer() is empty: Elysium's body does nothing
        Registry<BlockSoundTransformer> registry = server.registryAccess().registryOrThrow(ElysiumRegistries.Keys.BLOCK_SOUND_TRANSFORMERS);   // throws as Elysium's call would
        Holder<Block> holder = state.getBlockHolder();
        if (!(holder instanceof Holder.Reference<Block> ref)) return false;
        int size = registry.size();
        Set<?> tags = ((HolderReferenceTagsAccessor) (Object) ref).bons$elyTags();
        if (slot.bons$elyMemo() instanceof Memo m && m.registry == registry && m.size == size && m.holder == holder && m.tags == tags) {
            hits++;
            if (SHADOW) {
                shadow(registry, holder, m.found);
                return false;
            }
            if (!announced) announce();
            if (m.found != NONE) theirs.setReturnValue(((BlockSoundTransformer) m.found).toSoundType());
            return true;
        }
        Checked c = checked;
        if (c == null || c.registry != registry || c.size != size) checked = c = check(registry, size);
        if (!c.memoable) return false;
        searches++;
        Optional<BlockSoundTransformer> first = registry.stream().filter(s -> s.blocks().contains(holder)).findFirst();
        if (((HolderReferenceTagsAccessor) (Object) ref).bons$elyTags() == tags)
            slot.bons$setElyMemo(new Memo(registry, size, holder, tags, first.isPresent() ? first.get() : NONE));
        if (SHADOW) return false;                         // Elysium's own code runs: the same search, the same result
        if (first.isPresent()) theirs.setReturnValue(first.get().toSoundType());
        return true;
    }

    private static Checked check(Registry<BlockSoundTransformer> registry, int size) {
        boolean ok = registry.getClass() == MappedRegistry.class;
        if (ok) {
            for (BlockSoundTransformer s : registry) {
                HolderSet<Block> blocks = s == null ? null : s.blocks();
                Class<?> k = blocks == null ? null : blocks.getClass();
                if (k != HolderSet.Named.class && k != HolderSet.Direct.class) {
                    ok = false;
                    break;
                }
            }
        }
        if (!ok) LOGGER.info("Bons and Furious: elysium_sound_transformer_memo: Elysium's sound transformer registry ({}, {} entries) uses a block set this switch does not know; its search runs as shipped",
                registry.getClass().getSimpleName(), size);
        return new Checked(registry, size, ok);
    }

    private static void shadow(Registry<BlockSoundTransformer> registry, Holder<Block> holder, Object remembered) {
        Optional<BlockSoundTransformer> first = registry.stream().filter(s -> s.blocks().contains(holder)).findFirst();
        Object searched = first.isPresent() ? first.get() : NONE;
        SHADOW_CHECKS.incrementAndGet();
        if (searched != remembered) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) LOGGER.warn("Bons and Furious: elysium_sound_transformer_memo shadow mismatch #{} for {}: remembered {}, searched {}", n, holder, remembered, searched);
        }
    }

    private static void announce() {
        announced = true;
        LOGGER.info("Bons and Furious: elysium_sound_transformer_memo applies (each block remembers its Elysium sound transformer until the registry or its tags change){}",
                SHADOW ? " - shadow verification on" : "");
    }
}
