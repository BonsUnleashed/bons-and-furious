package bons.furious.patch.vanilla_chunk_codecs;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.core.Registry;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_chunk_biome_codec_memo (Minecraft 1.20.1 on Forge 47.4.16; both sides, the server's
 * chunk saving and loading). SRG member names. Idea: C2ME / Gale / Leaves texts on faster chunk serialization, idea text
 * only; the design is ours.
 *
 * What vanilla does. ChunkSerializer.write (m_63454_) and read (m_188230_) each start with makeBiomeCodec(biomeRegistry)
 * (m_188260_), which builds the whole biome PalettedContainer codec (RecordCodecBuilder, list/field/optional codecs,
 * comapFlatMap, orElsePartial; dozens of objects, several of them MapCodecs that allocate their compressor maps) for every
 * chunk written and every chunk read.
 *
 * What the switch does. The last codec built is kept together with the registry it was built for and the plains holder
 * it captured, and handed back while the same registry object is asked again and registry.getHolderOrThrow(PLAINS)
 * (m_246971_) still returns that same holder object.
 *
 * Why the result is identical. makeBiomeCodec captures exactly three things from the registry: asHolderIdMap() (m_206115_,
 * a new Registry$1 view that delegates every call to the registry), holderByNameCodec() (m_206110_, lambdas that call the
 * registry) and the plains holder. The first two are live views, so a kept one answers exactly as a new one does; the
 * plains holder is compared on every call. Everything else in the codec is constant (strategy, DFU combinators), and DFU
 * codecs keep no per-call state (their compressor maps are filled only by map-compressing ops, which NbtOps is not). It is
 * used only for registries whose class keeps Registry's own asHolderIdMap / holderByNameCodec / getHolderOrThrow (checked
 * once per class): a registry class that overrides one of them always runs the original. The registry calls vanilla
 * makes are pure lookups and allocations; a hit makes one getHolderOrThrow call, as vanilla does; a miss makes one more
 * (Aquamirae's ModifyVariable on MappedRegistry.getHolder maps only its own item/block ids from a table fixed at class
 * initialisation, so it answers the same each time).
 *
 * -Dbons_and_furious.chunkBiomeCodecMemo=false switches it off at run time.
 * -Dbons_and_furious.chunkBiomeCodecMemo.shadow=true (verification runs only): on every 256th hit the original also
 * builds a fresh codec, and a section-sized container holding the registry's first 64 biomes is encoded with both and
 * decoded with both; SHADOW_CHECKS / SHADOW_MISMATCHES (WARN for the first 20).
 */
public final class BiomeCodecMemo {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.chunkBiomeCodecMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.chunkBiomeCodecMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static final AtomicLong HITS = new AtomicLong();
    private static volatile boolean announced;
    private static volatile Memo last;

    /** True when the registry class keeps Registry's own view methods (nothing overrides them). */
    private static final ClassValue<Boolean> PLAIN_VIEWS = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("m_206115_").getDeclaringClass() == Registry.class
                        && type.getMethod("m_206110_").getDeclaringClass() == Registry.class
                        && type.getMethod("m_246971_", net.minecraft.resources.ResourceKey.class).getDeclaringClass() == Registry.class;
            } catch (Throwable t) {
                return false;
            }
        }
    };

    record Memo(Object registry, Object plains, Object codec) {
    }

    private BiomeCodecMemo() {
    }

    /** ChunkSerializer.makeBiomeCodec (m_188260_), wrapped. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Codec codec(Registry<Biome> registry, Operation<Codec> original) {
        if (!enabled || registry == null || !PLAIN_VIEWS.get(registry.getClass())) {
            return original.call(registry);
        }
        Object plains = registry.m_246971_(Biomes.f_48202_);
        Memo m = last;
        if (m != null && m.registry() == registry && m.plains() == plains) {
            if (SHADOW && (HITS.incrementAndGet() & 255) == 0) shadow(registry, (Codec<Object>) m.codec(), original.call(registry));
            return (Codec) m.codec();
        }
        Codec codec = original.call(registry);
        last = new Memo(registry, plains, codec);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_chunk_biome_codec_memo applies (the biome section codec is built once per registry, not once per chunk){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return codec;
    }

    /** The registry the given codec was built for, when it is the codec this memo keeps; null otherwise (vanilla_chunk_palette_direct_nbt). */
    public static Registry<?> registryOf(Object codec) {
        Memo m = last;
        return m != null && m.codec() == codec ? (Registry<?>) m.registry() : null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void shadow(Registry<Biome> registry, Codec<Object> kept, Codec<Object> fresh) {
        try {
            IdMap<Holder<Biome>> ids = registry.m_206115_();
            PalettedContainer<Holder<Biome>> c = new PalettedContainer<>(ids, registry.m_246971_(Biomes.f_48202_), PalettedContainer.Strategy.f_188138_);
            List<Holder.Reference<Biome>> all = registry.m_203611_().limit(64).toList();
            for (int i = 0; i < 64 && !all.isEmpty(); i++) c.m_63127_(i & 3, (i >> 2) & 3, (i >> 4) & 3, all.get(i % all.size()));
            DataResult<Tag> a = kept.encodeStart(NbtOps.f_128958_, c);
            DataResult<Tag> b = fresh.encodeStart(NbtOps.f_128958_, c);
            boolean same = String.valueOf(a.result().orElse(null)).equals(String.valueOf(b.result().orElse(null)));
            if (same && a.result().isPresent()) {
                PalettedContainerRO<Holder<Biome>> da = (PalettedContainerRO<Holder<Biome>>) kept.parse(NbtOps.f_128958_, a.result().get()).result().orElse(null);
                PalettedContainerRO<Holder<Biome>> db = (PalettedContainerRO<Holder<Biome>>) fresh.parse(NbtOps.f_128958_, a.result().get()).result().orElse(null);
                for (int i = 0; i < 64 && same; i++) same = da != null && db != null && da.m_63087_(i & 3, (i >> 2) & 3, (i >> 4) & 3) == db.m_63087_(i & 3, (i >> 2) & 3, (i >> 4) & 3);
            }
            SHADOW_CHECKS.incrementAndGet();
            if (!same) {
                long k = SHADOW_MISMATCHES.incrementAndGet();
                if (k <= 20) LOGGER.warn("Bons and Furious: vanilla_chunk_biome_codec_memo shadow mismatch #{}", k);
            }
        } catch (Throwable t) {
            long k = SHADOW_MISMATCHES.incrementAndGet();
            if (k <= 20) LOGGER.warn("Bons and Furious: vanilla_chunk_biome_codec_memo shadow check failed", t);
        }
    }
}
