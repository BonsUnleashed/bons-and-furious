package bons.portprobe;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.embeddedt.embeddium.impl.gl.util.VertexRange;
import org.embeddedt.embeddium.impl.render.EmbeddiumWorldRenderer;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.GlobalChunkBuildContext;
import org.embeddedt.embeddium.impl.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltSectionMeshParts;
import org.embeddedt.embeddium.impl.render.chunk.terrain.DefaultTerrainRenderPasses;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.embeddedt.embeddium.impl.util.task.CancellationToken;
import org.embeddedt.embeddium.impl.world.cloned.ChunkRenderContext;
import org.embeddedt.embeddium.impl.world.cloned.ClonedChunkSectionCache;

/**
 * Development-only (never shipped), 1.0.50 port: the in-game section A/B of the Forge 1.0.50 rig
 * (projects/furious_1050_majoropts_20261113/fixtures46/probe46/SectionCheck.java, directive @sections), ported to
 * Embeddium 1.0.15 on NeoForge 1.21.1 (its classes moved to org.embeddedt.embeddium.impl; Mojang names), run by the client
 * probe when -Dbons_and_furious.sections50 is set. The property holds the directive's arguments separated by ';':
 *
 *   <label>;<class>;<field[,field]>[;horizontal][;vertical][;rounds][;fixture|nofixture][;count=<F1,F2,..>]
 *
 * e.g. "embeddium_hidden_block_skip;bons.furious.patch.embeddium_hidden_blocks.HiddenBlocks;enabled;4;3;6;fixture;count=SKIPPED,VISIBLE".
 * The fields are public static booleans of the class: false = the original path, true = the switch's path.
 *
 * It runs on the render thread (the client tick that writes the probe result), with the player in the loaded world:
 *  1. clones every loaded, non-empty section in a box around the player with Embeddium's own WorldSlice.prepare and
 *     builds one ChunkBuilderMeshingTask per section (as RenderSectionManager.createRebuildTask makes it), all sharing one
 *     fixed camera position;
 *  2. executes every task synchronously with a private ChunkBuildContext (Embeddium's own buffers and BlockRenderCache)
 *     and hashes, per section and per terrain render pass, the vertex buffer, the vertex ranges (per facing) and the
 *     index buffer, plus the section's BuiltSectionInfo (flags, visibility data, block entity counts, animated sprites);
 *  3. does that with the flag off and on, alternating, several rounds, in the same JVM and on the same cloned section
 *     data (each task keeps its ChunkRenderContext, so world changes between the arms cannot leak in);
 *  4. compares every digest of every round against the first flag-off round and reports the meshing time per arm.
 * PASS = zero differing digests. Every output's native buffers are freed; the flags are restored afterwards.
 *
 * "fixture" (the default) first places a small cube of blocks next to the player, CLIENT-SIDE ONLY (ClientLevel.setBlock,
 * never sent to the integrated server, so the save is not touched), with gaps so hidden and visible faces both occur, and
 * puts the original client block states back afterwards. "count=..." names public static LongAdder fields of the same
 * class (a helper's counters, when its metrics flag is on): their increase over one more flag-on pass of the same
 * sections is reported, twice (equal numbers = nothing else counted). Each execute posts Embeddium's ChunkDataBuiltEvent
 * for the built section info, as a normal rebuild does.
 */
final class SectionCheck50 {
    private SectionCheck50() {
    }

    private static final CancellationToken NEVER = new CancellationToken() {
        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void setCancelled() {
        }
    };

    static Map<String, Object> run(String spec) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("spec", spec);
        try {
            run(spec.split(";"), out);
        } catch (Throwable t) {
            out.put("passed", false);
            out.put("error", t.toString());
            t.printStackTrace();
        }
        System.out.println("BONS_PORT_SECTIONS50 " + out);
        return out;
    }

    private static void run(String[] a, Map<String, Object> out) throws Exception {
        if (a.length < 3) throw new IllegalArgumentException("usage: <label>;<class>;<field[,field]>[;h][;v][;rounds][;fixture|nofixture][;count=F1,F2]");
        String label = a[0];
        Class<?> owner = Class.forName(a[1]);
        String[] names = a[2].split(",");
        int horizontal = 4, vertical = 3, rounds = 6, n = 0;
        boolean fixture = true;
        String[] counters = new String[0];
        for (int i = 3; i < a.length; i++) {
            if (a[i].equals("fixture")) fixture = true;
            else if (a[i].equals("nofixture")) fixture = false;
            else if (a[i].startsWith("count=")) counters = a[i].substring(6).split(",");
            else {
                int v = Integer.parseInt(a[i]);
                if (n == 0) horizontal = v;
                else if (n == 1) vertical = v;
                else rounds = v;
                n++;
            }
        }
        out.put("label", label);
        Field[] flags = new Field[names.length];
        for (int i = 0; i < names.length; i++) flags[i] = owner.getField(names[i]);
        LongAdder[] adders = new LongAdder[counters.length];
        for (int i = 0; i < counters.length; i++) adders[i] = (LongAdder) owner.getField(counters[i]).get(null);
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) throw new IllegalStateException("no world loaded");
        Map<BlockPos, BlockState> placed = fixture ? placeFixture(level, mc.player.blockPosition()) : Map.of();
        boolean[] saved = new boolean[flags.length];
        for (int i = 0; i < flags.length; i++) saved[i] = flags[i].getBoolean(null);
        out.put("flags_before", Arrays.toString(saved));
        try {
            check(mc, level, label, flags, horizontal, vertical, rounds, placed.size(), counters, adders, out);
        } finally {
            for (int i = 0; i < flags.length; i++) flags[i].setBoolean(null, saved[i]);
            for (Map.Entry<BlockPos, BlockState> e : placed.entrySet()) level.setBlock(e.getKey(), e.getValue(), 2 | 16);
        }
    }

    private static void set(Field[] flags, boolean on) throws IllegalAccessException {
        for (Field f : flags) f.setBoolean(null, on);
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        for (Class<?> c = owner.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(owner);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(owner.getClass().getName() + "." + name);
    }

    /** Block ids of the fixture; ids not registered in this pack are skipped. */
    private static final String[] FIXTURE = {
            "minecraft:glass", "minecraft:white_stained_glass", "minecraft:glass_pane", "minecraft:tinted_glass",
            "minecraft:stone", "minecraft:diorite", "minecraft:granite", "minecraft:andesite", "minecraft:dirt",
            "minecraft:cobblestone", "minecraft:sand", "minecraft:gravel", "minecraft:deepslate", "minecraft:netherrack",
            "minecraft:coarse_dirt", "minecraft:tuff", "minecraft:calcite",
            "mekanism:steel_casing", "mekanism:structural_glass", "mekanism:dynamic_tank", "mekanism:induction_casing",
            "mekanism:boiler_casing", "mekanism:thermal_evaporation_block"};

    /** Places the fixture client-side; returns the original client states (to restore), in placement order. */
    private static Map<BlockPos, BlockState> placeFixture(ClientLevel level, BlockPos at) {
        List<BlockState> states = new ArrayList<>();
        for (String id : FIXTURE) {
            ResourceLocation key = ResourceLocation.parse(id);
            if (!BuiltInRegistries.BLOCK.containsKey(key)) continue;
            BlockState s = BuiltInRegistries.BLOCK.get(key).defaultBlockState();
            if (!s.hasBlockEntity()) states.add(s);   // no block entities: nothing client-side may tick or need server data
        }
        Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        if (states.isEmpty()) return original;
        for (int i = 0; i < 7; i++)
            for (int j = 0; j < 4; j++)
                for (int k = 0; k < 7; k++) {
                    if ((i + j + k) % 3 == 0) continue;                    // gaps: visible faces next to hidden ones
                    BlockPos p = at.offset(3 + i, j, 3 + k);
                    if (!level.isLoaded(p)) continue;                      // only loaded chunks
                    original.put(p, level.getBlockState(p));
                    level.setBlock(p, states.get((i * 7 + j * 3 + k * 5) % states.size()), 2 | 16);
                }
        return original;
    }

    /** Over every block of the box: non-air blocks and the blocks whose top-level model is one of Fusion's. */
    private static String coverage(ClientLevel level, int cx, int cy, int cz, int horizontal, int vertical) {
        var blocks = Minecraft.getInstance().getBlockRenderer();
        long solid = 0, modifier = 0, fusionOther = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = (cy - vertical) * 16; y < (cy + vertical + 1) * 16; y++) {
            if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) continue;
            for (int x = (cx - horizontal) * 16; x < (cx + horizontal + 1) * 16; x++)
                for (int z = (cz - horizontal) * 16; z < (cz + horizontal + 1) * 16; z++) {
                    BlockState state = level.getBlockState(p.set(x, y, z));
                    if (state.isAir()) continue;
                    solid++;
                    String name = blocks.getBlockModel(state).getClass().getName();
                    if (name.equals("com.supermartijn642.fusion.model.modifiers.block.BlockModelModifierBakedModel")) modifier++;
                    else if (name.startsWith("com.supermartijn642.fusion.")) fusionOther++;
                }
        }
        return "blocks=" + solid + " fusionModifierModels=" + modifier + " otherFusionModels=" + fusionOther;
    }

    @SuppressWarnings("unchecked")
    private static void check(Minecraft mc, ClientLevel level, String label, Field[] flags, int horizontal, int vertical, int rounds,
                              int fixtureBlocks, String[] counters, LongAdder[] adders, Map<String, Object> out) throws Exception {
        RenderSectionManager rsm = (RenderSectionManager) field(EmbeddiumWorldRenderer.instance(), "renderSectionManager");
        ClonedChunkSectionCache sectionCache = (ClonedChunkSectionCache) field(rsm, "sectionCache");
        ChunkVertexType vertexType = (ChunkVertexType) field(rsm, "vertexType");
        Long2ReferenceMap<RenderSection> byPos = (Long2ReferenceMap<RenderSection>) field(rsm, "sectionByPosition");
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        BlockPos at = mc.player.blockPosition();
        int cx = SectionPos.blockToSectionCoord(at.getX()), cy = SectionPos.blockToSectionCoord(at.getY()), cz = SectionPos.blockToSectionCoord(at.getZ());
        // WorldSlice.prepare by reflection, as the Forge rig does (WorldSlice implements Fabric block-view interfaces)
        java.lang.reflect.Method prepare = Class.forName("org.embeddedt.embeddium.impl.world.WorldSlice")
                .getMethod("prepare", Level.class, SectionPos.class, ClonedChunkSectionCache.class);
        List<ChunkBuilderMeshingTask> tasks = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (int y = cy - vertical; y <= cy + vertical; y++) {
            if (y < level.getMinSection() || y >= level.getMaxSection()) continue;
            for (int x = cx - horizontal; x <= cx + horizontal; x++) {
                for (int z = cz - horizontal; z <= cz + horizontal; z++) {
                    RenderSection section = byPos.get(SectionPos.asLong(x, y, z));
                    if (section == null) continue;
                    ChunkRenderContext context = (ChunkRenderContext) prepare.invoke(null, (Level) level, section.getPosition(), sectionCache);
                    if (context == null) continue;                       // empty section: Embeddium builds nothing either
                    tasks.add(new ChunkBuilderMeshingTask(section, context, 0).withCameraPosition(camera));
                    names.add(x + "," + y + "," + z);
                }
            }
        }
        out.put("at", at.getX() + " " + at.getY() + " " + at.getZ());
        out.put("box", (2 * horizontal + 1) + "x" + (2 * vertical + 1) + "x" + (2 * horizontal + 1));
        out.put("fixture_blocks", fixtureBlocks);
        out.put("coverage", coverage(level, cx, cy, cz, horizontal, vertical));
        out.put("sections", tasks.size());
        if (tasks.isEmpty()) throw new IllegalStateException("no loaded non-empty section in the box");
        ChunkBuildContext build = new ChunkBuildContext(level, vertexType);
        // Embeddium 1.0.15's fluid renderer asks GlobalChunkBuildContext for the build context of the thread it runs on (its builder
        // threads carry their own; the render thread's is a static Embeddium binds only while it builds there itself). These builds
        // run on the render thread with their own context, so it is bound for them and the previous binding is put back afterwards
        // (first run, c2: the fluid of the first watery section met a null context and the check stopped).
        ChunkBuildContext previousMain = GlobalChunkBuildContext.get();
        GlobalChunkBuildContext.bindMainThread(build);
        Map<String, String> reference = null;
        int differences = 0, meshedPassesOff = 0;
        List<String> details = new ArrayList<>();
        double[] offNs = new double[rounds], onNs = new double[rounds];
        String digestOff = null, digestOn = null;
        List<String> counts = new ArrayList<>();
        try {
            // warm-up: one pass per arm (JIT, model caches, the render-layer memo)
            for (boolean arm : new boolean[] {false, true}) {
                set(flags, arm);
                buildAll(tasks, build, names, null);
            }
            for (int r = 0; r < rounds; r++) {
                for (int k = 0; k < 2; k++) {
                    boolean arm = (r % 2 == 0) == (k == 1);                // off,on / on,off alternating
                    set(flags, arm);
                    Map<String, String> digests = new LinkedHashMap<>();
                    long t0 = System.nanoTime();
                    buildAll(tasks, build, names, digests);
                    long ns = System.nanoTime() - t0;                      // includes hashing, the same work in both arms
                    if (arm) onNs[r] = ns;
                    else offNs[r] = ns;
                    String whole = combined(digests);
                    if (reference == null) {
                        reference = digests;                               // first round is always flag off
                        meshedPassesOff = (int) digests.values().stream().filter(v -> !v.equals("none")).count();
                    }
                    for (Map.Entry<String, String> e : digests.entrySet()) {
                        if (!e.getValue().equals(reference.get(e.getKey()))) {
                            differences++;
                            if (details.size() < 20) details.add("round " + r + (arm ? " on " : " off ") + e.getKey());
                        }
                    }
                    if (arm) digestOn = whole;
                    else digestOff = whole;
                }
            }
            if (adders.length > 0) {                                       // a helper's counters over one flag-on pass, twice
                set(flags, true);
                for (int pass = 1; pass <= 2; pass++) {
                    long[] before = new long[adders.length];
                    for (int i = 0; i < adders.length; i++) before[i] = adders[i].sum();
                    buildAll(tasks, build, names, null);
                    StringBuilder sb = new StringBuilder("pass " + pass + ":");
                    for (int i = 0; i < adders.length; i++) sb.append(' ').append(counters[i]).append('=').append(adders[i].sum() - before[i]);
                    counts.add(sb.toString());
                }
            }
        } finally {
            GlobalChunkBuildContext.bindMainThread(previousMain);
            build.cleanup();
        }
        double offMs = median(offNs) / 1e6, onMs = median(onNs) / 1e6;
        boolean pass = differences == 0 && digestOff != null && digestOff.equals(digestOn);
        out.put("passed", pass);
        out.put("meshed_pass_digests", meshedPassesOff);
        out.put("differences", differences);
        out.put("rounds", rounds);
        if (!details.isEmpty()) out.put("details", details);
        out.put("digest_off", digestOff);
        out.put("digest_on", digestOn);
        out.put("timing", String.format("off %.2f ms on %.2f ms per full rebuild of %d sections (median of %d), on/off = %.3f",
                offMs, onMs, tasks.size(), rounds, onMs / offMs));
        out.put("off_ns", Arrays.toString(offNs));
        out.put("on_ns", Arrays.toString(onNs));
        out.put("counts", counts);
    }

    private static void buildAll(List<ChunkBuilderMeshingTask> tasks, ChunkBuildContext build, List<String> names, Map<String, String> digests) throws Exception {
        for (int i = 0; i < tasks.size(); i++) {
            ChunkBuildOutput out = tasks.get(i).execute(build, NEVER);
            if (out == null) throw new IllegalStateException("meshing task returned null for section " + names.get(i));
            try {
                if (digests != null) {
                    for (TerrainRenderPass pass : DefaultTerrainRenderPasses.ALL) digests.put(names.get(i) + "/" + pass, digest(out.getMesh(pass)));
                    // Embeddium leaves the block-entity arrays null for a section without block entities
                    digests.put(names.get(i) + "/info", "flags=" + out.info.flags + " visibility=" + out.info.visibilityData
                            + " global=" + length(out.info.globalBlockEntities) + " culled=" + length(out.info.culledBlockEntities)
                            + " sprites=" + Arrays.toString(out.info.animatedSprites));
                }
            } finally {
                out.delete();
            }
        }
    }

    private static String digest(BuiltSectionMeshParts mesh) throws Exception {
        if (mesh == null) return "none";
        MessageDigest h = MessageDigest.getInstance("SHA-256");
        ByteBuffer v = mesh.getVertexData().getDirectBuffer().duplicate();
        v.position(0);
        h.update(v);
        for (VertexRange r : mesh.getVertexRanges()) {
            String s = r == null ? "-" : r.vertexStart() + ":" + r.vertexCount();
            h.update(s.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            h.update((byte) ';');
        }
        if (mesh.getIndexData() != null) {
            ByteBuffer ix = mesh.getIndexData().getDirectBuffer().duplicate();
            ix.position(0);
            h.update(ix);
        }
        return HexFormat.of().formatHex(h.digest());
    }

    private static String combined(Map<String, String> digests) throws Exception {
        MessageDigest h = MessageDigest.getInstance("SHA-256");
        for (Map.Entry<String, String> e : digests.entrySet()) {
            h.update(e.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            h.update((byte) '=');
            h.update(e.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            h.update((byte) '\n');
        }
        return HexFormat.of().formatHex(h.digest());
    }

    private static int length(Object[] a) {
        return a == null ? -1 : a.length;
    }

    private static double median(double[] v) {
        double[] c = v.clone();
        Arrays.sort(c);
        return c.length % 2 == 1 ? c[c.length / 2] : (c[c.length / 2 - 1] + c[c.length / 2]) / 2;
    }
}
