package bons.portprobe;

import bons.furious.patch.terrablender.LazyNamespaceRules;
import bons.furious.patch.terrablender_layers.StackedLayers;
import bons.furious.patch.terrablender_stale.StaleLayerGuard;
import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import com.sun.management.ThreadMXBean;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.data.worldgen.SurfaceRuleData;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.placement.CaveSurface;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.potionstudios.biomeswevegone.world.level.levelgen.surfacerules.BandsRuleSource;
import net.potionstudios.biomeswevegone.world.level.levelgen.surfacerules.BetweenRepeatingNoiseRange;
import net.potionstudios.biomeswevegone.world.level.levelgen.surfacerules.WeightedRuleSource;
import terrablender.worldgen.surface.NamespacedSurfaceRuleSource;

/**
 * Bons and Furious 1.0.47, terrablender_stale group: native proof for terrablender_stale_layer_guard (Fix) on the
 * classes TerrablenderStaleApply.py transformed (the three TerraBlender groups of 1.0.47 + the server pack's mixins on the
 * surface-rule classes: C2ME x3, ModernFix 5.27.89 x7, BWG SurfaceSystemMixin), with TerraBlender 3.0.1.10 or 3.0.1.11.
 * Original = the switch's runtime flag off (StaleLayerGuard.enabled = false: TerraBlender builds every entry as shipped);
 * terrablender_lazy_namespace_rules and terrablender_stacked_layer_dedupe stay on, as in game.
 *
 * Stacks are built the way level.dat makes them (TerrablenderLayersProof: live top layer, inner layers fresh
 * DECODES of the codec encoding, decoded vanilla base), in two shapes: "direct" (each layer's base is the next layer) and
 * "sequenced" (each layer's base is sequence(VanillaBackport-like rules, sequence(other rules, next layer)), the shape
 * VanillaBackport 1.1.7+ makes by prepending its rules at every NoiseGeneratorSettings construction; panda_tw's crash shows
 * it). The "minecraft" entry carries an unverified source type (as VanillaBackport's own rule types make it in game), so
 * the lazy switch builds it up front; other namespaces are vanilla trees and deferred. The world's noise registry is
 * vanilla's: minecraft:small_patch (VanillaBackport 1.2) and terralith:gone_noise do not exist in it.
 *
 * Checks: (1) exactness: stacks without a broken entry (direct x3 / x10 + unknown type, sequenced x8, sequenced x8 with an
 * edited but valid inner entry): every block and both WG heightmaps of real chunks, switch off vs on; the lazy switch defers
 * the same number of entries either way (the guard wraps inside its consumer). (2) the fix: a stale inner entry that needs
 * a missing noise (eager "minecraft" in the innermost layer as in panda_tw's crash; the same in three layers; a deferred
 * vanilla "terralith" entry; a namespace no biome uses any more): off throws "Missing element ResourceKey[minecraft:
 * worldgen/noise / ...]" (the crash reproduced), on generates every chunk exactly like the same stack WITHOUT that entry
 * (switch off), and records the missing noise for that layer and namespace. (3) a broken entry in the outermost (current)
 * layer still throws with the switch on. (4) sensitivity: a valid entry in the same place changes blocks, so the equality in
 * (2) is not vacuous. Timing (mode "timing"): surface pass per chunk on the sequenced x8 stack, off vs on.
 *
 *   args: proof|timing <TerraBlender jar (3.0.1.10 or 3.0.1.11)> [chunks per variant]
 */
public final class TerrablenderStaleProof extends SurfaceRules {
    static long checks, fails;
    static final long SEED = 2026102247L;
    static final String NR = "terrablender/worldgen/surface/NamespacedSurfaceRuleSource$NamespacedRule";
    static final String SMALL_PATCH = "minecraft:small_patch", GONE = "terralith:gone_noise";

    static void ok(boolean c, String what) {
        checks++;
        if (!c && ++fails <= 40) System.out.println("FAIL " + what);
    }

    // ------------------------------------------------------------------ helpers (as TerrablenderLayersProof)
    static BlockState block(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState();
    }

    static SurfaceRules.RuleSource state(String id) {
        return SurfaceRules.state(block(id));
    }

    @SafeVarargs
    public static SurfaceRules.ConditionSource isBiome(ResourceKey<Biome>... keys) {
        return SurfaceRules.isBiome(keys);
    }

    public static SurfaceRules.RuleSource ifTrue(SurfaceRules.ConditionSource c, SurfaceRules.RuleSource r) {
        return SurfaceRules.ifTrue(c, r);
    }

    static SurfaceRules.RuleSource seq(SurfaceRules.RuleSource... r) {
        return SurfaceRules.sequence(r);
    }

    static ResourceKey<Biome> key(String id) {
        return ResourceKey.create(Registries.BIOME, ResourceLocation.parse(id));
    }

    static ResourceKey<Biome>[] keys(String... ids) {
        @SuppressWarnings("unchecked") ResourceKey<Biome>[] out = new ResourceKey[ids.length];
        for (int i = 0; i < ids.length; i++) out[i] = key(ids[i]);
        return out;
    }

    static ResourceKey<net.minecraft.world.level.levelgen.synth.NormalNoise.NoiseParameters> noise(String id) {
        return ResourceKey.create(Registries.NOISE, ResourceLocation.parse(id));
    }

    static void setStatic(Class<?> c, String name, Object v) throws Exception {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        f.set(null, v);
    }

    /** An unverified rule-source type (stands for VanillaBackport's own rule types): the lazy switch builds it up front. */
    record CountingSource(SurfaceRules.RuleSource inner) implements SurfaceRules.RuleSource {
        static final AtomicLong APPLIES = new AtomicLong();

        @Override
        public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() {
            return this.inner.codec();
        }

        @Override
        public SurfaceRules.SurfaceRule apply(SurfaceRules.Context context) {
            APPLIES.incrementAndGet();
            return this.inner.apply(context);
        }
    }

    static SimpleWeightedRandomList<BlockState> bandStates() {
        return SimpleWeightedRandomList.<BlockState>builder().add(block("minecraft:terracotta"), 4).add(block("minecraft:yellow_terracotta"), 2)
                .add(block("minecraft:brown_terracotta"), 2).add(block("minecraft:calcite"), 1).build();
    }

    static SurfaceRules.RuleSource bwgTree() {
        SimpleWeightedRandomList<SurfaceRules.RuleSource> floor = SimpleWeightedRandomList.<SurfaceRules.RuleSource>builder()
                .add(state("minecraft:grass_block"), 5).add(state("minecraft:podzol"), 3).add(state("minecraft:coarse_dirt"), 2).build();
        return seq(
                ifTrue(isBiome(keys("biomeswevegone:weighted_meadow")), ifTrue(SurfaceRules.ON_FLOOR, new WeightedRuleSource(floor))),
                ifTrue(isBiome(keys("biomeswevegone:noise_canyon")), ifTrue(SurfaceRules.stoneDepthCheck(0, true, CaveSurface.FLOOR),
                        new BetweenRepeatingNoiseRange(noise("minecraft:surface"), 0.15f, -1f, 1f, state("minecraft:terracotta"), state("minecraft:red_terracotta"),
                                state("minecraft:orange_terracotta")))),
                ifTrue(isBiome(keys("biomeswevegone:banded_peaks")), ifTrue(SurfaceRules.abovePreliminarySurface(),
                        new BandsRuleSource(bandStates(), UniformInt.of(1, 4), UniformInt.of(8, 16), 0.05f, 3))));
    }

    static final String[][] BIOME_COPIES = {
            {"biomeswevegone:weighted_meadow", "minecraft:plains"}, {"biomeswevegone:noise_canyon", "minecraft:badlands"},
            {"biomeswevegone:banded_peaks", "minecraft:jagged_peaks"},
            {"terralith:yellowstone", "minecraft:savanna"}, {"terralith:desert_canyon", "minecraft:desert"}, {"terralith:alpine_grove", "minecraft:grove"},
            {"regions_unexplored:redwoods", "minecraft:taiga"}, {"spawn:almond_grove", "minecraft:meadow"},
            {"vanillabackport:pale_garden", "minecraft:dark_forest"}, {"nyctophobia:no_rules", "minecraft:swamp"}};

    /** VanillaBackport-like rules of the current version (vanilla types; the unverified wrapper is added by minecraftEntry). */
    static SurfaceRules.RuleSource vbCurrent() {
        return seq(ifTrue(isBiome(keys("vanillabackport:pale_garden")), ifTrue(SurfaceRules.ON_FLOOR, state("minecraft:moss_block"))),
                ifTrue(isBiome(keys("spawn:almond_grove")), ifTrue(SurfaceRules.noiseCondition(noise("minecraft:surface"), -0.2, 0.2), state("minecraft:rooted_dirt"))));
    }

    /** VanillaBackport 1.2's extra rule: Dappled Forest patches from minecraft:small_patch (missing in this registry). */
    static SurfaceRules.RuleSource vbStaleExtra() {
        return ifTrue(isBiome(keys("vanillabackport:dappled_forest")), ifTrue(SurfaceRules.noiseCondition(noise(SMALL_PATCH), 0.0, Double.MAX_VALUE),
                state("minecraft:mossy_cobblestone")));
    }

    /** The "minecraft" namespace entry: TerraBlender's default overworld rules with VanillaBackport-like rules before them. */
    static SurfaceRules.RuleSource minecraftEntry(boolean stale) {
        return seq(new CountingSource(stale ? seq(vbStaleExtra(), vbCurrent()) : vbCurrent()), SurfaceRuleData.overworld());
    }

    /** The live "minecraft" entry: one object, as TerraBlender's live map holds; set in main after Bootstrap. */
    static SurfaceRules.RuleSource LIVE_MINECRAFT;

    /** The live namespace map (withCustom: plus a namespace with an unknown source type). */
    static Map<String, SurfaceRules.RuleSource> namespaces(boolean withCustom) {
        Map<String, SurfaceRules.RuleSource> m = new LinkedHashMap<>();
        m.put("minecraft", LIVE_MINECRAFT);
        m.put("regions_unexplored", seq(ifTrue(isBiome(keys("regions_unexplored:redwoods")), ifTrue(SurfaceRules.ON_FLOOR, state("minecraft:podzol"))),
                SurfaceRuleData.overworldLike(true, false, true)));
        m.put("spawn", ifTrue(isBiome(keys("spawn:almond_grove")), ifTrue(SurfaceRules.ON_FLOOR, state("minecraft:rooted_dirt"))));
        m.put("terralith", terralith(false));
        m.put("biomeswevegone", bwgTree());
        if (withCustom) m.put("custom", seq(new CountingSource(state("minecraft:clay")), SurfaceRuleData.overworldLike(true, false, false)));
        return m;
    }

    /** terralith's rules; stale: plus a rule that needs terralith:gone_noise (missing). Vanilla types only: deferred. */
    static SurfaceRules.RuleSource terralith(boolean stale) {
        SurfaceRules.RuleSource yellowstone = ifTrue(isBiome(keys("terralith:yellowstone")), seq(ifTrue(SurfaceRules.noiseCondition(noise("minecraft:surface"), -0.1, 0.1),
                state("minecraft:gravel")), ifTrue(SurfaceRules.ON_FLOOR, state("minecraft:coarse_dirt"))));
        SurfaceRules.RuleSource canyon = ifTrue(isBiome(keys("terralith:desert_canyon")), seq(ifTrue(SurfaceRules.yBlockCheck(VerticalAnchor.absolute(70), 2),
                SurfaceRules.bandlands()), state("minecraft:sandstone")));
        SurfaceRules.RuleSource grove = ifTrue(isBiome(keys("terralith:alpine_grove")), ifTrue(SurfaceRules.verticalGradient("terralith_grove", VerticalAnchor.absolute(100),
                VerticalAnchor.absolute(140)), state("minecraft:snow_block")));
        if (!stale) return seq(yellowstone, canyon, grove, SurfaceRuleData.overworldLike(true, false, true));
        SurfaceRules.RuleSource gone = ifTrue(isBiome(keys("terralith:yellowstone", "terralith:desert_canyon")),
                ifTrue(SurfaceRules.noiseCondition(noise(GONE), -0.5, 0.5), state("minecraft:red_sandstone")));
        return seq(gone, yellowstone, canyon, grove, SurfaceRuleData.overworldLike(true, false, true));
    }

    static Tag encode(SurfaceRules.RuleSource s) {
        return SurfaceRules.RuleSource.CODEC.encodeStart(NbtOps.INSTANCE, s).result().orElseThrow(() -> new IllegalStateException("cannot encode " + s));
    }

    static SurfaceRules.RuleSource decode(Tag t) {
        return SurfaceRules.RuleSource.CODEC.parse(NbtOps.INSTANCE, t).result().orElseThrow();
    }

    static boolean hasCounting(SurfaceRules.RuleSource s) {
        if(s instanceof CountingSource)return true;
        if(s.getClass().getName().equals("net.minecraft.world.level.levelgen.SurfaceRules$SequenceRuleSource")) {
            try {
                Method method=s.getClass().getDeclaredMethod("sequence");method.setAccessible(true);
                for(Object child:(List<?>)method.invoke(s))if(hasCounting((SurfaceRules.RuleSource)child))return true;
            } catch(ReflectiveOperationException e){throw new AssertionError(e);}
        }
        return false;
    }

    /** A layer's map the way level.dat hands it back: encodable sources decoded afresh; unknown types the same object. */
    static Map<String, SurfaceRules.RuleSource> decoded(Map<String, SurfaceRules.RuleSource> live) {
        Map<String, SurfaceRules.RuleSource> out = new LinkedHashMap<>();
        for (var e : live.entrySet()) out.put(e.getKey(), hasCounting(e.getValue()) ? e.getValue() : decode(encode(e.getValue())));
        return out;
    }

    /** One stack. Layer 1 = innermost wrapper, depth - 1 = the one below the live top. */
    record Spec(String name, int depth, boolean sequenced, boolean withCustom, Map<Integer, Map<String, SurfaceRules.RuleSource>> edits,
                Map<Integer, List<String>> removals, Map<String, SurfaceRules.RuleSource> topEdits) {
        Spec(String name, int depth, boolean sequenced, boolean withCustom) {
            this(name, depth, sequenced, withCustom, Map.of(), Map.of(), Map.of());
        }

        Spec edit(int layer, String ns, SurfaceRules.RuleSource s) {
            Map<Integer, Map<String, SurfaceRules.RuleSource>> e = new LinkedHashMap<>(this.edits);
            Map<String, SurfaceRules.RuleSource> m = new LinkedHashMap<>(e.getOrDefault(layer, Map.of()));
            m.put(ns, s);
            e.put(layer, m);
            return new Spec(this.name, this.depth, this.sequenced, this.withCustom, e, this.removals, this.topEdits);
        }

        Spec remove(int layer, String ns) {
            Map<Integer, List<String>> r = new LinkedHashMap<>(this.removals);
            List<String> l = new ArrayList<>(r.getOrDefault(layer, List.of()));
            l.add(ns);
            r.put(layer, l);
            return new Spec(this.name, this.depth, this.sequenced, this.withCustom, this.edits, r, this.topEdits);
        }

        Spec top(String ns, SurfaceRules.RuleSource s) {
            Map<String, SurfaceRules.RuleSource> t = new LinkedHashMap<>(this.topEdits);
            t.put(ns, s);
            return new Spec(this.name, this.depth, this.sequenced, this.withCustom, this.edits, this.removals, t);
        }
    }

    static final class Built {
        final NamespacedSurfaceRuleSource top;
        final Map<Integer, NamespacedSurfaceRuleSource> layers = new LinkedHashMap<>();

        Built(NamespacedSurfaceRuleSource top) {
            this.top = top;
        }
    }

    static Built build(Spec s) {
        Map<String, SurfaceRules.RuleSource> live = namespaces(s.withCustom());
        SurfaceRules.RuleSource chain = decode(encode(SurfaceRuleData.overworld()));
        Map<Integer, NamespacedSurfaceRuleSource> layers = new LinkedHashMap<>();
        for (int layer = 1; layer < s.depth(); layer++) {
            Map<String, SurfaceRules.RuleSource> m = decoded(live);
            for (String r : s.removals().getOrDefault(layer, List.of())) m.remove(r);
            m.putAll(s.edits().getOrDefault(layer, Map.of()));
            NamespacedSurfaceRuleSource ns = new NamespacedSurfaceRuleSource(chain, ImmutableMap.copyOf(m));
            layers.put(layer, ns);
            chain = s.sequenced()
                    ? seq(decode(encode(vbCurrent())), seq(decode(encode(ifTrue(isBiome(keys("nyctophobia:no_rules")), ifTrue(SurfaceRules.ON_FLOOR,
                            state("minecraft:mycelium"))))), ns))
                    : ns;
        }
        Map<String, SurfaceRules.RuleSource> top = new LinkedHashMap<>(live);
        top.putAll(s.topEdits());
        Built b = new Built(new NamespacedSurfaceRuleSource(chain, ImmutableMap.copyOf(top)));
        b.layers.putAll(layers);
        return b;
    }

    // ------------------------------------------------------------------ world (as TerrablenderLayersProof)
    static HolderLookup.Provider lookup;
    static MappedRegistry<Biome> biomes;
    static List<Holder<Biome>> pool = new ArrayList<>();
    static NoiseGeneratorSettings vanilla;
    static Method doFill;

    static final class Patches extends BiomeSource {
        @Override
        public com.mojang.serialization.MapCodec<? extends BiomeSource> codec() {
            return null;
        }

        @Override
        public Stream<Holder<Biome>> collectPossibleBiomes() {
            return pool.stream();
        }

        @Override
        public Holder<Biome> getNoiseBiome(int qx, int qy, int qz, Climate.Sampler s) {
            int px = Math.floorDiv(qx, 12), pz = Math.floorDiv(qz, 12), py = Math.floorDiv(qy, 24);
            int h = (px * 73856093) ^ (pz * 19349663) ^ (py * 83492791);
            return pool.get(Math.floorMod(h, pool.size()));
        }
    }

    static final class FixedStarts extends StructureManager {
        FixedStarts() {
            super(null, null, null);
        }

        @Override
        public List<StructureStart> startsForStructure(ChunkPos pos, Predicate<Structure> filter) {
            return List.of();
        }
    }

    record World(NoiseBasedChunkGenerator gen, RandomState random, BiomeSource source, BiomeManager manager) {}

    static Biome copy(Biome b) throws Exception {
        Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        sun.misc.Unsafe u = (sun.misc.Unsafe) uf.get(null);
        Biome c = (Biome) u.allocateInstance(Biome.class);
        for (Field f : Biome.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            f.setAccessible(true);
            f.set(c, f.get(b));
        }
        return c;
    }

    static void setup() throws Exception {
        lookup = VanillaRegistries.createLookup();
        biomes = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        Map<ResourceKey<Biome>, Biome> vanillaBiomes = new LinkedHashMap<>();
        lookup.<Biome>lookupOrThrow(Registries.BIOME).listElements().forEach(h -> vanillaBiomes.put(h.key(), h.value()));
        vanillaBiomes.forEach((k, v) -> biomes.register(k, v, net.minecraft.core.RegistrationInfo.BUILT_IN));
        for (String[] c : BIOME_COPIES) biomes.register(key(c[0]), copy(vanillaBiomes.get(key(c[1]))), net.minecraft.core.RegistrationInfo.BUILT_IN);
        biomes.freeze();
        for (String id : new String[] {"minecraft:plains", "minecraft:desert", "minecraft:eroded_badlands", "minecraft:snowy_plains", "minecraft:frozen_ocean",
                "minecraft:jagged_peaks", "minecraft:stony_shore", "minecraft:swamp", "minecraft:river", "minecraft:ocean", "minecraft:deep_dark"})
            pool.add(biomes.getHolderOrThrow(key(id)));
        for (String[] c : BIOME_COPIES) pool.add(biomes.getHolderOrThrow(key(c[0])));
        pool.add(Holder.direct(vanillaBiomes.get(key("minecraft:cherry_grove"))));
        vanilla = lookup.<NoiseGeneratorSettings>lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        doFill = NoiseBasedChunkGenerator.class.getDeclaredMethod("doFill", Blender.class, StructureManager.class, RandomState.class, ChunkAccess.class, int.class, int.class);
        doFill.setAccessible(true);
    }

    static World world(SurfaceRules.RuleSource surface) {
        NoiseGeneratorSettings s = new NoiseGeneratorSettings(vanilla.noiseSettings(), vanilla.defaultBlock(), vanilla.defaultFluid(), vanilla.noiseRouter(), surface,
                vanilla.spawnTarget(), vanilla.seaLevel(), vanilla.disableMobGeneration(), true, true, vanilla.useLegacyRandomSource());
        RandomState rs = RandomState.create(s, lookup.lookupOrThrow(Registries.NOISE), SEED);   // vanilla noises only
        BiomeSource src = new Patches();
        BiomeManager bm = new BiomeManager((x, y, z) -> src.getNoiseBiome(x, y, z, rs.sampler()), BiomeManager.obfuscateSeed(SEED));
        return new World(new NoiseBasedChunkGenerator(src, Holder.direct(s)), rs, src, bm);
    }

    static ProtoChunk generate(World w, int cx, int cz, boolean surface) throws Exception {
        ProtoChunk c = new ProtoChunk(new ChunkPos(cx, cz), UpgradeData.EMPTY, LevelHeightAccessor.create(-64, 384), biomes, null);
        c.fillBiomesFromNoise(w.source(), w.random().sampler());
        c.setPersistedStatus(net.minecraft.world.level.chunk.status.ChunkStatus.BIOMES);
        NoiseSettings ns = w.gen().generatorSettings().value().noiseSettings().clampToHeightAccessor(c.getHeightAccessorForGeneration());
        int cellHeight = ns.getCellHeight();
        doFill.invoke(w.gen(), Blender.empty(), new FixedStarts(), w.random(), c, Mth.floorDiv(ns.minY(), cellHeight), Mth.floorDiv(ns.height(), cellHeight));
        c.setPersistedStatus(net.minecraft.world.level.chunk.status.ChunkStatus.NOISE);
        if (surface) w.gen().buildSurface(c, new WorldGenerationContext(w.gen(), c), w.random(), null, w.manager(), biomes, Blender.empty());
        return c;
    }

    static long positions;

    static String compare(ProtoChunk a, ProtoChunk b) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int x0 = a.getPos().getMinBlockX(), z0 = a.getPos().getMinBlockZ();
        for (int y = -64; y < 320; y++)
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++) {
                    p.set(x0 + x, y, z0 + z);
                    positions++;
                    BlockState sa = a.getBlockState(p), sb = b.getBlockState(p);
                    if (sa != sb) return "block " + p + " " + sa + " vs " + sb;
                }
        for (Heightmap.Types t : new Heightmap.Types[] {Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG})
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++)
                    if (a.getHeight(t, x, z) != b.getHeight(t, x, z)) return "heightmap " + t + " " + x + "," + z;
        return null;
    }

    /** The chunk generated with the guard on or off; a thrown exception comes back as its message (prefixed "THROWS "). */
    static Object gen(World w, int cx, int cz, boolean guard) {
        StaleLayerGuard.enabled = guard;
        try {
            return generate(w, cx, cz, true);
        } catch (Throwable t) {
            Throwable c = t instanceof java.lang.reflect.InvocationTargetException ite ? ite.getCause() : t;
            String noise = StaleLayerGuard.missingNoise(c);
            return "THROWS " + (noise != null ? "missing noise " + noise : c.toString());
        } finally {
            StaleLayerGuard.enabled = true;
        }
    }

    static int[] coords(Random r) {
        return new int[] {r.nextInt(400) - 200, r.nextInt(400) - 200};
    }

    // ------------------------------------------------------------------ the proof
    /** (1) No broken entry: switch off vs on, every block; the lazy switch defers as many entries either way. */
    static void exact(Spec s, int chunks, long seed) {
        long c0 = checks, p0 = positions, g0 = StaleLayerGuard.GUARDED.get();
        World w = world(build(s).top);
        Random r = new Random(seed);
        long deferredOff = 0, deferredOn = 0;
        for (int i = 0; i < chunks; i++) {
            int[] c = coords(r);
            long d0 = LazyNamespaceRules.DEFERRED.get();
            Object off = gen(w, c[0], c[1], false);
            long d1 = LazyNamespaceRules.DEFERRED.get();
            Object on = gen(w, c[0], c[1], true);
            long d2 = LazyNamespaceRules.DEFERRED.get();
            deferredOff += d1 - d0;
            deferredOn += d2 - d1;
            ok(off instanceof ProtoChunk && on instanceof ProtoChunk, s.name() + " chunk " + c[0] + "," + c[1] + " generates: " + (off instanceof String ? off : on));
            if (off instanceof ProtoChunk a && on instanceof ProtoChunk b) {
                String d = compare(a, b);
                ok(d == null, s.name() + " chunk " + c[0] + "," + c[1] + " guard on vs off: " + d);
            }
        }
        long guarded = StaleLayerGuard.GUARDED.get() - g0;
        ok(deferredOn == deferredOff, s.name() + ": the lazy switch deferred " + deferredOff + " entries without the guard and " + deferredOn + " with it");
        // identical DIRECT stacks collapse under terrablender_stacked_layer_dedupe (no inner layer is applied at all); inner
        // layers survive in the sequenced shape and wherever an entry is not provably equal (unknown types)
        if (s.depth() > 1 && (s.sequenced() || s.withCustom()))
            ok(guarded > 0, s.name() + ": inner entries were guarded (" + guarded + ")");
        if (s.depth() <= 1) ok(guarded == 0, s.name() + ": nothing is guarded without a saved layer (" + guarded + ")");
        System.out.printf(Locale.ROOT, "EXACT  %-44s depth %2d %-9s %3d chunks, %,d positions, deferred off/on %d/%d, guarded %d, %d checks%n", s.name(),
                s.depth(), s.sequenced() ? "sequenced" : "direct", chunks, positions - p0, deferredOff, deferredOn, guarded, checks - c0);
    }

    /**
     * (2) A broken inner entry: off throws the missing-noise exception (in at least one chunk), on generates every chunk
     * like the reference stack without that entry; the guard recorded the noise for that layer and namespace.
     */
    static void fixed(Spec broken, Spec reference, int layer, String ns, String noiseId, int chunks, long seed) {
        long c0 = checks, p0 = positions, f0 = StaleLayerGuard.FAILED.get();
        Built b = build(broken);
        World w = world(b.top), ref = world(build(reference).top);
        Random r = new Random(seed);
        int threw = 0, otherThrows = 0;
        for (int i = 0; i < chunks; i++) {
            int[] c = coords(r);
            Object off = gen(w, c[0], c[1], false);
            if (off instanceof String m) {
                if (m.equals("THROWS missing noise " + noiseId)) threw++;
                else otherThrows++;
            }
            Object on = gen(w, c[0], c[1], true);
            Object want = gen(ref, c[0], c[1], false);
            ok(want instanceof ProtoChunk, broken.name() + " reference chunk " + c[0] + "," + c[1] + " generates: " + want);
            ok(on instanceof ProtoChunk, broken.name() + " chunk " + c[0] + "," + c[1] + " generates with the guard: " + on);
            if (on instanceof ProtoChunk a && want instanceof ProtoChunk e) {
                String d = compare(a, e);
                ok(d == null, broken.name() + " chunk " + c[0] + "," + c[1] + " guard on vs the stack without the entry: " + d);
            }
        }
        ok(threw > 0, broken.name() + ": without the guard at least one chunk throws for " + noiseId + " (" + threw + " of " + chunks + ")");
        ok(otherThrows == 0, broken.name() + ": no other exception without the guard (" + otherThrows + ")");
        ok(StaleLayerGuard.FAILED.get() > f0 || StaleLayerGuard.SKIPPED.get() > 0, broken.name() + ": the guard caught the failure");
        ok(StaleLayerGuard.reported(ns, noiseId), broken.name() + ": the guard reported " + ns + " / " + noiseId + " (layer " + layer + ")");
        System.out.printf(Locale.ROOT, "FIXED  %-44s depth %2d %-9s %3d chunks, %,d positions, off threw %d/%d (%s), failures caught %d, %d checks%n",
                broken.name(), broken.depth(), broken.sequenced() ? "sequenced" : "direct", chunks, positions - p0, threw, chunks, noiseId,
                StaleLayerGuard.FAILED.get() - f0, checks - c0);
    }

    /** (3) A broken entry in the outermost layer: the switch does not touch it, so it still throws. */
    static void outermost(int chunks, long seed) {
        Spec s = new Spec("broken outermost minecraft x4", 4, true, false).top("minecraft", minecraftEntry(true));
        World w = world(build(s).top);
        Random r = new Random(seed);
        int threwOn = 0;
        for (int i = 0; i < chunks; i++) {
            int[] c = coords(r);
            Object on = gen(w, c[0], c[1], true);
            if (("THROWS missing noise " + SMALL_PATCH).equals(on)) threwOn++;
        }
        ok(threwOn == chunks, "outermost broken entry still throws with the guard on: " + threwOn + " of " + chunks);
        System.out.printf(Locale.ROOT, "OUTER  %-44s %d of %d chunks throw with the guard on (unchanged)%n", s.name(), threwOn, chunks);
    }

    /** (4) The equality in (2) is not vacuous: a VALID entry in the place of the removed one changes blocks. */
    static void sensitivity(long seed) throws Exception {
        Spec without = new Spec("sensitivity", 8, true, false).remove(1, "minecraft");
        SurfaceRules.RuleSource valid = seq(new CountingSource(seq(ifTrue(isBiome(keys("minecraft:plains", "minecraft:desert", "minecraft:swamp")),
                ifTrue(SurfaceRules.ON_FLOOR, state("minecraft:red_wool"))), vbCurrent())), SurfaceRuleData.overworld());
        Spec with = new Spec("sensitivity", 8, true, false).edit(1, "minecraft", valid);
        World a = world(build(without).top), b = world(build(with).top);
        Random r = new Random(seed);
        int differ = 0;
        for (int i = 0; i < 40 && differ < 3; i++) {
            int[] c = coords(r);
            Object x = gen(a, c[0], c[1], false), y = gen(b, c[0], c[1], false);
            if (x instanceof ProtoChunk p && y instanceof ProtoChunk q && compare(p, q) != null) differ++;
        }
        ok(differ > 0, "sensitivity: a valid entry in layer 1 changes blocks (" + differ + " chunks)");
        System.out.printf(Locale.ROOT, "SENSITIVITY %d chunks differ between the stack without the innermost minecraft entry and one with a valid one%n", differ);
    }

    static void units() {
        ok(SMALL_PATCH.equals(StaleLayerGuard.missingNoise(new IllegalStateException("Missing element ResourceKey[minecraft:worldgen/noise / minecraft:small_patch]"))),
                "missingNoise reads the vanilla message");
        ok(SMALL_PATCH.equals(StaleLayerGuard.missingNoise(new RuntimeException("wrapped", new IllegalStateException(
                "Missing element ResourceKey[minecraft:worldgen/noise / minecraft:small_patch]")))), "missingNoise reads a wrapped cause");
        ok(StaleLayerGuard.missingNoise(new IllegalStateException("Missing element ResourceKey[minecraft:worldgen/density_function / minecraft:x]")) == null,
                "another registry's missing element is not a missing noise");
        ok(StaleLayerGuard.missingNoise(new IllegalArgumentException("Missing element ResourceKey[minecraft:worldgen/noise / minecraft:small_patch]")) == null,
                "another exception type is not the missing-noise exception");
        ok(StaleLayerGuard.missingNoise(new IllegalStateException((String) null)) == null, "a message-less exception is not the missing-noise exception");
    }

    static void timing(int rounds, int perRound) throws Exception {
        World w = world(build(new Spec("timing", 8, true, false)).top);
        double[][] us = new double[2][rounds], kb = new double[2][rounds];
        for (int round = -3; round < rounds; round++) {
            for (int k = 0; k < 2; k++) {
                int mode = (round + k) & 1;
                ProtoChunk[] cs = new ProtoChunk[perRound];
                for (int i = 0; i < perRound; i++) cs[i] = generate(w, i * 7 - 30, 21 + round * 5, false);
                StaleLayerGuard.enabled = mode == 1;
                long a0 = allocated(), t0 = System.nanoTime();
                for (ProtoChunk c : cs) w.gen().buildSurface(c, new WorldGenerationContext(w.gen(), c), w.random(), null, w.manager(), biomes, Blender.empty());
                long t1 = System.nanoTime(), a1 = allocated();
                StaleLayerGuard.enabled = true;
                if (round >= 0) {
                    us[mode][round] = (t1 - t0) / 1e3 / perRound;
                    kb[mode][round] = (a1 - a0) / 1024.0 / perRound;
                }
            }
        }
        System.out.printf(Locale.ROOT, "MEDIAN surface pass per chunk, sequenced x8 stack (%d rounds x %d chunks): guard off %.0f us %.0f KB -> on %.0f us %.0f KB%n",
                rounds, perRound, median(us[0]), median(kb[0]), median(us[1]), median(kb[1]));
    }

    static long allocated() {
        return ((ThreadMXBean) ManagementFactory.getThreadMXBean()).getCurrentThreadAllocatedBytes();
    }

    static double median(double[] v) {
        double[] c = v.clone();
        Arrays.sort(c);
        return c.length % 2 == 1 ? c[c.length / 2] : (c[c.length / 2 - 1] + c[c.length / 2]) / 2;
    }

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        LIVE_MINECRAFT = minecraftEntry(false);
        String build;
        try (ZipFile z = new ZipFile(args[1]); InputStream in = z.getInputStream(z.getEntry(NR + ".class"))) {
            build = LazyNamespaceRules.testedBuild(in.readAllBytes());
        }
        ok("4.1.0.8".equals(build), "TerraBlender NamespacedRule is a tested build: " + build);
        String problem = LazyNamespaceRules.mixinProblem();
        ok(problem == null, "foreign mixins on the vanilla rule classes verified: " + problem);
        // as in the preceding proofs: the game reads the jar's class file; this class path serves the transformed copy first
        System.out.println("TerraBlender " + build + ", lazy rules on " + LazyNamespaceRules.enabled + ", dedupe on " + StackedLayers.dedupeEnabled);
        units();
        setup();
        int n = args.length > 2 ? Integer.parseInt(args[2]) : 16;
        if (mode.equals("timing")) {
            timing(11, 6);
        } else {
            // (1) exactness
            exact(new Spec("direct identical x3", 3, false, false), n, 1);
            exact(new Spec("direct identical + unknown type x10", 10, false, true), n, 2);
            exact(new Spec("sequenced identical x8 (VanillaBackport shape)", 8, true, false), n, 3);
            exact(new Spec("sequenced x8, valid edited entries in layers 2+5", 8, true, true)
                    .edit(2, "terralith", decode(encode(ifTrue(isBiome(keys("terralith:yellowstone")), state("minecraft:red_wool")))))
                    .edit(5, "minecraft", minecraftEntry(false)), n, 4);
            exact(new Spec("no saved layer (depth 1)", 1, false, false), Math.max(4, n / 4), 5);
            // (2) the fix
            fixed(new Spec("stale minecraft, innermost of 8 (panda_tw)", 8, true, false).edit(1, "minecraft", minecraftEntry(true)),
                    new Spec("ref", 8, true, false).remove(1, "minecraft"), 1, "minecraft", SMALL_PATCH, n, 11);
            fixed(new Spec("stale minecraft, layers 1-3 of 8", 8, true, false).edit(1, "minecraft", minecraftEntry(true))
                            .edit(2, "minecraft", minecraftEntry(true)).edit(3, "minecraft", minecraftEntry(true)),
                    new Spec("ref", 8, true, false).remove(1, "minecraft").remove(2, "minecraft").remove(3, "minecraft"), 2, "minecraft", SMALL_PATCH, n, 12);
            fixed(new Spec("stale minecraft, direct layer 2 of 5", 5, false, true).edit(2, "minecraft", minecraftEntry(true)),
                    new Spec("ref", 5, false, true).remove(2, "minecraft"), 2, "minecraft", SMALL_PATCH, n, 13);
            fixed(new Spec("stale terralith (deferred), layer 2 of 5", 5, false, false).edit(2, "terralith", decode(encode(terralith(true)))),
                    new Spec("ref", 5, false, false).remove(2, "terralith"), 2, "terralith", GONE, n * 3, 14);
            fixed(new Spec("stale removed namespace, layer 3 of 8", 8, true, false).edit(3, "gone",
                            seq(new CountingSource(ifTrue(isBiome(keys("gone:biome")), state("minecraft:clay"))), ifTrue(SurfaceRules.noiseCondition(noise(GONE), 0.0, 1.0),
                                    state("minecraft:clay")))),
                    new Spec("ref", 8, true, false), 3, "gone", GONE, n, 15);
            // (3) the outermost layer is never touched
            outermost(Math.max(4, n / 4), 21);
            // (4) sensitivity
            sensitivity(31);
            System.out.printf(Locale.ROOT, "COUNTERS guarded %d, failures caught %d, skipped applies %d; lazy deferred %d, eager %d, built late %d%n",
                    StaleLayerGuard.GUARDED.get(), StaleLayerGuard.FAILED.get(), StaleLayerGuard.SKIPPED.get(), LazyNamespaceRules.DEFERRED.get(),
                    LazyNamespaceRules.EAGER.get(), LazyNamespaceRules.BUILT_LATE.get());
        }
        System.out.println("TOTAL terrablender stale layer guard (" + mode + ", TerraBlender " + build + "): checks=" + checks + " failures=" + fails);
        if (fails > 0) throw new AssertionError("TerraBlender stale proof: " + fails + " failures");
    }
}
