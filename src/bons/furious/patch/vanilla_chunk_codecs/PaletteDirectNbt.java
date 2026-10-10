package bons.furious.patch.vanilla_chunk_codecs;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_chunk_palette_direct_nbt (Minecraft 1.20.1 on Forge 47.4.16; both sides, the server's
 * chunk saving). SRG member names. Idea: C2ME's "fast reduced allocation chunk serializer", Gale/Leaves "Faster chunk
 * serialization" (titles), Bellows' codec-versus-direct note; idea text only. The design is ours.
 *
 * What vanilla does. ChunkSerializer.write (m_63454_) encodes each section's block states and biomes through DFU:
 * f_188227_.encodeStart(NbtOps, states) and makeBiomeCodec(registry).encodeStart(NbtOps, biomes). For every palette entry
 * that walks KeyDispatchCodec, the per-block codec, StateDefinition's nested mapPair/xmap/fieldOf codecs and NbtOps'
 * RecordBuilder, with DataResults, Pairs and a StringTag for every key on the way; the result is a CompoundTag
 * {palette: [entry...], data: long[]} that the next instruction unwraps with getOrThrow.
 *
 * What the switch does. The section's PackedData is taken exactly as the codec takes it (pack(IdMap, strategy) with the
 * codec's own arguments: Block.BLOCK_STATE_REGISTRY and SECTION_STATES; the biome registry's holder id map and
 * SECTION_BIOMES), and the same tags are built directly: a new CompoundTag per palette entry with the keys put in the
 * order the codec puts them (block states: "Properties" first, holding the properties in reverse name order as
 * StateDefinition's nested mapPair codec adds them, then "Name"; biomes: one StringTag), the entries in a ListTag in
 * palette order, then a new CompoundTag with "palette" and, when the codec would write it, "data" as the same
 * LongArrayTag. Property value names are asked from the property every time (Property.getName(value), what the codec
 * calls); the block or biome name is the registry key's text, kept per state or holder.
 *
 * Why the result is identical (and when the codec still runs). A recipe (block state) or name (biome holder) is used only
 * after the real codec's output for it was seen: a section whose palette holds an entry without a verified recipe, or
 * whose outer shape (with or without "data") was not seen yet, is encoded by the original call, and its result is then
 * compared with the direct build of the same PackedData: same tag classes, the same key ITERATION ORDER in every
 * CompoundTag (what NbtIo writes) and equal values. Only on a full match are that section's entries and shape recorded
 * as verified; on any difference nothing is recorded and those entries keep going through the codec (logged once).
 * Errors (an unregistered block or biome, which the codec reports through DataResult and orElsePartial) never verify,
 * so they always take the original path with its exact error and partial result. Map iteration order of a CompoundTag
 * is a deterministic function of its map class and the insertion sequence; the direct build creates its compounds with
 * new CompoundTag() like NbtOps' builder and inserts in the verified order, so equal recipes give equal bytes every time.
 * The DataResult is consumed at once by getOrThrow (guarded fingerprint of m_63454_), which reads only its value.
 *
 * Only Minecraft's own container class takes this path (1.0.39). A section held in another mod's container type keeps the
 * original call, with one log line per type: Bye Pregen 1.1.2.4 keeps world-generation sections in its
 * ArenaBlockStatePalettedContainer, whose pack throws UnsupportedOperationException, and writes them with its own codec,
 * which it puts in ChunkSerializer's block-state codec field (f_188227_). 1.0.36-1.0.38 called pack on it, and every such
 * chunk failed to save (Bye Pregen's own raw saver is off next to FastChunkGen or any ChunkDataEvent.Save listener).
 *
 * -Dbons_and_furious.chunkPaletteDirectNbt=false switches it off at run time.
 * -Dbons_and_furious.chunkPaletteDirectNbt.shadow=true (verification runs only): every direct build is compared with the
 * original call's result (same comparison plus NbtIo bytes); the original's result is returned. SHADOW_CHECKS /
 * SHADOW_MISMATCHES (WARN for the first 20).
 */
public final class PaletteDirectNbt {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.chunkPaletteDirectNbt", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.chunkPaletteDirectNbt.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Test and log support: sections built directly, sections through the codec, verification refusals. */
    public static final AtomicLong DIRECT = new AtomicLong(), CODEC = new AtomicLong(), REFUSED = new AtomicLong(), VERIFIED = new AtomicLong();
    /** Test and log support: sections in another mod's container type, always left to the codec. */
    public static final AtomicLong FOREIGN = new AtomicLong();
    private static final Set<Class<?>> FOREIGN_LOGGED = ConcurrentHashMap.newKeySet();
    private static volatile boolean announced, refusalLogged;

    /** Block state -> StateRecipe; biome holder -> its name (String). Verified entries only. */
    private static final IdentityTable STATES = new IdentityTable(), BIOMES = new IdentityTable();
    /** Outer shapes seen and matched: block states / biomes, without / with "data". */
    private static volatile boolean blockShapeNoData, blockShapeData, biomeShapeNoData, biomeShapeData;
    private static volatile Codec<?> blockCodec;

    /** A verified block state: its name text and its properties in the order the codec inserts them. Immutable. */
    record StateRecipe(String name, Property<?>[] properties, String[] propertyNames) {
    }

    private PaletteDirectNbt() {
    }

    @SuppressWarnings("unchecked")
    private static Codec<?> blockCodec() {
        Codec<?> c = blockCodec;
        if (c == null) {
            try {
                Field f = ChunkSerializer.class.getDeclaredField("f_188227_");
                f.setAccessible(true);
                c = (Codec<?>) f.get(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            blockCodec = c;
        }
        return c;
    }

    /** ChunkSerializer.write: every Codec.encodeStart call (block states, biomes; blending data and below-zero retrogen pass through). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static DataResult encodeStart(Codec codec, DynamicOps ops, Object input, Operation<DataResult> original) {
        if (!enabled || ops != NbtOps.f_128958_) return original.call(codec, ops, input);
        // another mod's container type (a subclass, or its own PalettedContainerRO) is written by the codec it was made for
        if (input instanceof PalettedContainerRO<?> && input.getClass() != PalettedContainer.class) {
            foreign(input.getClass());
            return original.call(codec, ops, input);
        }
        if (codec == blockCodec() && input instanceof PalettedContainer<?> states) {
            return blockStates(codec, ops, (PalettedContainer<BlockState>) states, original);
        }
        Registry<?> registry = BiomeCodecMemo.registryOf(codec);
        if (registry != null && input instanceof PalettedContainerRO<?> biomes) {
            return biomes(codec, ops, registry, (PalettedContainerRO<Holder<?>>) biomes, original);
        }
        return original.call(codec, ops, input);
    }

    // ------------------------------------------------------------------------------------------------ block states

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static DataResult blockStates(Codec codec, DynamicOps ops, PalettedContainer<BlockState> container, Operation<DataResult> original) {
        // a global palette (more than 256 states) answers "maybe" to every question: such a section always takes the codec
        if (container.m_63109_(s -> false)) return original.call(codec, ops, container);
        // every state the container's palette may hold has a verified recipe
        if (!container.m_63109_(s -> !(STATES.get(s) instanceof StateRecipe))) {
            PalettedContainerRO.PackedData<BlockState> packed = container.m_188064_(Block.f_49791_, PalettedContainer.Strategy.f_188137_);
            List<BlockState> palette = packed.f_238184_();
            boolean withData = packed.f_238179_().isPresent();
            if (withData ? blockShapeData : blockShapeNoData) {
                StateRecipe[] recipes = new StateRecipe[palette.size()];
                for (int i = 0; i < recipes.length; i++) recipes[i] = (StateRecipe) STATES.get(palette.get(i));
                if (!SHADOW) {
                    DIRECT.incrementAndGet();
                    return DataResult.success(buildBlocks(palette, recipes, packed.f_238179_()));
                }
                DataResult result = original.call(codec, ops, container);
                CODEC.incrementAndGet();
                Optional<Tag> made = result.result();
                if (made.isPresent()) shadowCompare("block states", made.get(), buildBlocks(palette, recipes, packed.f_238179_()));
                return result;
            }
        }
        DataResult result = original.call(codec, ops, container);
        CODEC.incrementAndGet();
        // learn while a shape is unseen or the palette still holds a state that was neither verified nor refused
        if (result.result().isPresent() && (!blockShapeData || !blockShapeNoData || container.m_63109_(s -> STATES.get(s) == null))) {
            PalettedContainerRO.PackedData<BlockState> packed = container.m_188064_(Block.f_49791_, PalettedContainer.Strategy.f_188137_);
            learnBlocks(packed.f_238184_(), packed.f_238179_().isPresent(), (Tag) result.result().get());
        }
        return result;
    }
    /** The direct build of one block-states section. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static CompoundTag buildBlocks(List<BlockState> palette, StateRecipe[] recipes, Optional<LongStream> data) {
        ListTag list = new ListTag();
        for (int i = 0; i < recipes.length; i++) {
            BlockState state = palette.get(i);
            StateRecipe r = recipes[i];
            CompoundTag entry = new CompoundTag();
            if (r.properties().length > 0) {
                CompoundTag props = new CompoundTag();
                for (int k = 0; k < r.properties().length; k++) {
                    Property p = r.properties()[k];
                    props.m_128359_(r.propertyNames()[k], p.m_6940_(state.m_61143_(p)));
                }
                entry.m_128365_("Properties", props);
            }
            entry.m_128359_("Name", r.name());
            list.add(entry);
        }
        CompoundTag out = new CompoundTag();
        out.m_128365_("palette", list);
        if (data.isPresent()) out.m_128365_("data", new LongArrayTag(data.get().toArray()));
        return out;
    }

    /**
     * After an original encode: compare its tag with the direct build of the same palette (the codec's palette list is in
     * pack order, the order this second pack gives too). Entries that match are recorded as verified, entries that differ
     * as refused (always the codec); the outer shape (keys and their order, list type, "data") is recorded when it matches.
     * A palette whose size differs from the codec's list (the container changed in between) records nothing.
     */
    private static void learnBlocks(List<BlockState> palette, boolean withData, Tag made) {
        if (!(made instanceof CompoundTag c) || !(c.m_128423_("palette") instanceof ListTag list) || list.size() != palette.size()) return;
        StateRecipe[] candidates = new StateRecipe[palette.size()];
        for (int i = 0; i < candidates.length; i++) {
            Object known = STATES.get(palette.get(i));
            candidates[i] = known instanceof StateRecipe r ? r : known == null ? recipeFor(palette.get(i)) : null;
        }
        Optional<LongStream> data = Optional.empty();
        if (withData) {
            if (!(c.m_128423_("data") instanceof LongArrayTag lat)) return;
            data = Optional.of(LongStream.of(lat.m_128851_()));
        }
        boolean allCandidates = true;
        for (int i = 0; i < candidates.length; i++) {
            if (candidates[i] == null) {
                if (STATES.get(palette.get(i)) == null) STATES.put(palette.get(i), REFUSED_STATE);
                allCandidates = false;
                continue;
            }
            CompoundTag one = buildBlocks(List.of(palette.get(i)), new StateRecipe[] {candidates[i]}, Optional.empty());
            Tag directEntry = ((ListTag) one.m_128423_("palette")).get(0);
            if (sameTag(list.get(i), directEntry)) {
                if (!(STATES.get(palette.get(i)) instanceof StateRecipe)) VERIFIED.incrementAndGet();
                STATES.put(palette.get(i), candidates[i]);
            } else {
                STATES.put(palette.get(i), REFUSED_STATE);
                refused("block state", list.get(i), directEntry);
                allCandidates = false;
            }
        }
        if (allCandidates && (withData ? !blockShapeData : !blockShapeNoData)) {
            if (sameTag(made, buildBlocks(palette, candidates, data))) {
                if (withData) blockShapeData = true;
                else blockShapeNoData = true;
                announce();
            } else {
                refused("block states section", made, buildBlocks(palette, candidates, withData ? Optional.of(LongStream.of(((LongArrayTag) c.m_128423_("data")).m_128851_())) : Optional.empty()));
            }
        }
    }

    /** Marks a state whose codec output the direct build does not reproduce (or that has no registry key): codec forever. */
    private static final Object REFUSED_STATE = new Object();
    /** A candidate recipe for a registered block state: its block's key text and its properties in codec insertion order. */
    private static StateRecipe recipeFor(BlockState state) {
        Block block = state.m_60734_();
        ResourceLocation key = BuiltInRegistries.f_256975_.m_7981_(block);
        if (key == null) return null;
        List<Property<?>> props = new ArrayList<>(block.m_49965_().m_61092_());
        Collections.reverse(props);   // StateDefinition's mapPair codec adds the last property (by name) first
        String[] names = new String[props.size()];
        for (int i = 0; i < names.length; i++) names[i] = props.get(i).m_61708_();
        return new StateRecipe(key.toString(), props.toArray(new Property<?>[0]), names);
    }

    // ------------------------------------------------------------------------------------------------ biomes

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static DataResult biomes(Codec codec, DynamicOps ops, Registry<?> registry, PalettedContainerRO<Holder<?>> container, Operation<DataResult> original) {
        if (container.m_63109_(h -> false)) return original.call(codec, ops, container);   // global palette (more than 8 biomes)
        if (!container.m_63109_(h -> !(BIOMES.get(h) instanceof String))) {
            PalettedContainerRO.PackedData<Holder<?>> packed = container.m_188064_((net.minecraft.core.IdMap) registry.m_206115_(), PalettedContainer.Strategy.f_188138_);
            List<Holder<?>> palette = packed.f_238184_();
            boolean withData = packed.f_238179_().isPresent();
            if (withData ? biomeShapeData : biomeShapeNoData) {
                String[] names = new String[palette.size()];
                for (int i = 0; i < names.length; i++) names[i] = (String) BIOMES.get(palette.get(i));
                if (!SHADOW) {
                    DIRECT.incrementAndGet();
                    return DataResult.success(buildBiomes(names, packed.f_238179_()));
                }
                DataResult result = original.call(codec, ops, container);
                CODEC.incrementAndGet();
                Optional<Tag> made = result.result();
                if (made.isPresent()) shadowCompare("biomes", made.get(), buildBiomes(names, packed.f_238179_()));
                return result;
            }
        }
        DataResult result = original.call(codec, ops, container);
        CODEC.incrementAndGet();
        if (result.result().isPresent() && (!biomeShapeData || !biomeShapeNoData || container.m_63109_(h -> BIOMES.get(h) == null))) {
            PalettedContainerRO.PackedData<Holder<?>> packed = container.m_188064_((net.minecraft.core.IdMap) registry.m_206115_(), PalettedContainer.Strategy.f_188138_);
            learnBiomes(packed.f_238184_(), packed.f_238179_().isPresent(), (Tag) result.result().get());
        }
        return result;
    }

    /** As learnBlocks, for a biome section: entries are the holders' key texts. */
    private static void learnBiomes(List<Holder<?>> palette, boolean withData, Tag made) {
        if (!(made instanceof CompoundTag c) || !(c.m_128423_("palette") instanceof ListTag list) || list.size() != palette.size()) return;
        String[] candidates = new String[palette.size()];
        boolean all = true;
        for (int i = 0; i < candidates.length; i++) {
            Object known = BIOMES.get(palette.get(i));
            if (known instanceof String s) {
                candidates[i] = s;
                continue;
            }
            if (known != null) {
                all = false;
                continue;
            }
            Optional<? extends ResourceKey<?>> key = palette.get(i).m_203543_();
            String name = key.isPresent() ? key.get().m_135782_().toString() : null;
            if (name != null && list.get(i) instanceof StringTag st && sameTag(st, StringTag.m_129297_(name))) {
                BIOMES.put(palette.get(i), name);
                VERIFIED.incrementAndGet();
                candidates[i] = name;
            } else {
                BIOMES.put(palette.get(i), REFUSED_STATE);
                if (name != null) refused("biome", list.get(i), StringTag.m_129297_(name));
                all = false;
            }
        }
        if (all && (withData ? !biomeShapeData : !biomeShapeNoData)) {
            Optional<LongStream> data = Optional.empty();
            if (withData) {
                if (!(c.m_128423_("data") instanceof LongArrayTag lat)) return;
                data = Optional.of(LongStream.of(lat.m_128851_()));
            }
            CompoundTag direct = buildBiomes(candidates, data);
            if (sameTag(made, direct)) {
                if (withData) biomeShapeData = true;
                else biomeShapeNoData = true;
                announce();
            } else {
                refused("biomes section", made, direct);
            }
        }
    }
    static CompoundTag buildBiomes(String[] names, Optional<LongStream> data) {
        ListTag list = new ListTag();
        for (String n : names) list.add(StringTag.m_129297_(n));
        CompoundTag out = new CompoundTag();
        out.m_128365_("palette", list);
        if (data.isPresent()) out.m_128365_("data", new LongArrayTag(data.get().toArray()));
        return out;
    }

    // ------------------------------------------------------------------------------------------------ comparison

    /** Same tag classes, same key iteration order in every compound, same list element types, equal leaf values. */
    public static boolean sameTag(Tag a, Tag b) {
        if (a == null || b == null || a.getClass() != b.getClass()) return false;
        if (a instanceof CompoundTag ca) {
            CompoundTag cb = (CompoundTag) b;
            Iterator<String> ia = ca.m_128431_().iterator(), ib = cb.m_128431_().iterator();
            while (ia.hasNext()) {
                if (!ib.hasNext()) return false;
                String ka = ia.next(), kb = ib.next();
                if (!ka.equals(kb) || !sameTag(ca.m_128423_(ka), cb.m_128423_(kb))) return false;
            }
            return !ib.hasNext();
        }
        if (a instanceof ListTag la) {
            ListTag lb = (ListTag) b;
            if (la.size() != lb.size() || la.m_7264_() != lb.m_7264_()) return false;
            for (int i = 0; i < la.size(); i++) if (!sameTag(la.get(i), lb.get(i))) return false;
            return true;
        }
        return a.equals(b);
    }

    static byte[] bytes(Tag t) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            NbtIo.m_128950_(t, new DataOutputStream(bo));
            return bo.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void shadowCompare(String what, Tag made, Tag direct) {
        SHADOW_CHECKS.incrementAndGet();
        if (!sameTag(made, direct) || !Arrays.equals(bytes(made), bytes(direct))) {
            long k = SHADOW_MISMATCHES.incrementAndGet();
            if (k <= 20) LOGGER.warn("Bons and Furious: vanilla_chunk_palette_direct_nbt shadow mismatch #{} ({}): codec {} / direct {}", k, what, made, direct);
        }
    }

    private static void refused(String what, Tag made, Tag direct) {
        REFUSED.incrementAndGet();
        if (!refusalLogged) {
            refusalLogged = true;
            LOGGER.warn("Bons and Furious: vanilla_chunk_palette_direct_nbt: a {} section's direct build differs from the codec's ({} / {}); those entries keep using the codec",
                    what, made, direct);
        }
    }

    private static void foreign(Class<?> type) {
        FOREIGN.incrementAndGet();
        if (FOREIGN_LOGGED.add(type)) {
            LOGGER.info("Bons and Furious: vanilla_chunk_palette_direct_nbt steps aside for sections held in {} (another mod's container type); those sections are left to the codec",
                    type.getName());
        }
    }

    private static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_chunk_palette_direct_nbt applies (chunk palettes are written directly, each entry checked against the codec once){}",
                    SHADOW ? " - shadow verification on" : "");
        }
    }

    /** Test support: entries known (verified or refused) for block states and biome holders. */
    public static int knownStates() {
        return STATES.size();
    }

    public static int knownBiomes() {
        return BIOMES.size();
    }
}
