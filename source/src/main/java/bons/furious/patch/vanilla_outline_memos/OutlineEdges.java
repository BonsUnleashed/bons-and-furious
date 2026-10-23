package bons.furious.patch.vanilla_outline_memos;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

/**
 * Bons and Furious switch vanilla_outline_edge_memo (Minecraft 1.20.1, client). Helper of LevelRendererOutlineEdgeMixin;
 * no Minecraft code is carried.
 *
 * Every frame the block outline of the block the player looks at is drawn by LevelRenderer.renderShape, which asks the
 * block's shape for its edges (VoxelShape.forAllEdges). That walk visits every grid point of the shape's voxel grid, three
 * times, testing up to four cells at each: 1.3 us for a full cube, about 100 us for a fence, several hundred microseconds
 * for a block made of a few dozen boxes (measured on a Core Ultra 9 285K), every frame, for the same shape object.
 *
 * What changes: the edges of the last shape object are kept (six doubles per edge, in the order the walk produced them) and
 * replayed into renderShape's consumer when the same shape object is drawn again; for a new shape the walk runs once into a
 * recorder and is then replayed. Only shapes of the known immutable classes are kept (vanilla ArrayVoxelShape,
 * CubeVoxelShape, SliceShape; Radium's VoxelShapeSimpleCube, VoxelShapeAlignedCuboid, VoxelShapeAlignedCuboidOffset,
 * VoxelShapeEmpty, by exact class: all their fields are final and set when they are built, so the walk is a fixed function of
 * the object); any other shape class walks every frame. Why identical: the consumer receives the same calls with the same
 * numbers in the same order as the walk would make; the walk itself has no other effect. It stays off when one of the shape
 * classes carries a mixin member this switch does not know (another mod could make edges depend on something else).
 * Runtime flag: -Dbons_and_furious.outlineEdgeMemo=false. SHADOW MODE for rigs: -Dbons_and_furious.outlineEdgeMemo.shadow=true
 * walks the shape on every frame as well, compares the edges with the kept ones (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for
 * the first 20) and draws from the walk.
 */
public final class OutlineEdges {
    /** Runtime switch (the config switch acts when LevelRenderer is transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.outlineEdgeMemo", "true"));
    /** Shadow mode (see the class comment). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.outlineEdgeMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Walks recorded and frames replayed (statistics; the render thread writes them). */
    public static long recorded, replayed;
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");

    static final Set<String> SHAPES = Set.of(
            "net.minecraft.world.phys.shapes.ArrayVoxelShape",
            "net.minecraft.world.phys.shapes.CubeVoxelShape",
            "net.minecraft.world.phys.shapes.SliceShape",
            "me.jellysquid.mods.lithium.common.shapes.VoxelShapeSimpleCube",
            "me.jellysquid.mods.lithium.common.shapes.VoxelShapeAlignedCuboid",
            "me.jellysquid.mods.lithium.common.shapes.VoxelShapeAlignedCuboidOffset",
            "me.jellysquid.mods.lithium.common.shapes.VoxelShapeEmpty");
    /** Classes whose merged members are checked once: the shape classes above, their supertypes and the voxel grids. */
    static final String[] CHECKED = {
            "net.minecraft.world.phys.shapes.VoxelShape", "net.minecraft.world.phys.shapes.ArrayVoxelShape",
            "net.minecraft.world.phys.shapes.CubeVoxelShape", "net.minecraft.world.phys.shapes.SliceShape",
            "net.minecraft.world.phys.shapes.DiscreteVoxelShape", "net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape",
            "net.minecraft.world.phys.shapes.SubShape"};
    /**
     * Mixins whose merged members those classes may carry, checked for this pack on 2026-10-19: accessors and invokers
     * (FerriteCore, CerbonsAPI, CreativeCore, Bons' create_contraptions), CerbonsAPI's collision hook, Radium's precomputed
     * coordinate arrays and specialised collision methods. None touches the edge walk.
     */
    static final Set<String> KNOWN_MIXINS = Set.of(
            "malte0811.ferritecore.mixin.blockstatecache.ArrayVSAccess", "malte0811.ferritecore.mixin.blockstatecache.BitSetDVSAccess",
            "malte0811.ferritecore.mixin.blockstatecache.DiscreteVSAccess", "malte0811.ferritecore.mixin.blockstatecache.SliceShapeAccess",
            "malte0811.ferritecore.mixin.blockstatecache.SubShapeAccess", "malte0811.ferritecore.mixin.blockstatecache.VoxelShapeAccess",
            "com.cerbon.cerbons_api.mixin.multipart_entities.InvokerArrayVoxelShape", "com.cerbon.cerbons_api.mixin.multipart_entities.VoxelShapeMixin",
            "team.creative.creativecore.mixin.VoxelShapeAccessor", "bons.furious.mixin.create_contraptions.VoxelShapeAccessor",
            "me.jellysquid.mods.lithium.mixin.block.moving_block_shapes.VoxelShapeMixin",
            "me.jellysquid.mods.lithium.mixin.shapes.precompute_shape_arrays.SimpleVoxelShapeMixin",
            "me.jellysquid.mods.lithium.mixin.shapes.specialized_shapes.VoxelShapeMixin");

    private record Kept(VoxelShape shape, double[] edges) {
    }

    private static volatile Kept kept;
    /** 0 = not checked yet, 1 = only known mixins, 2 = another mixin is merged into a shape class (the memo stays off). */
    private static volatile int neighbours;
    private static final AtomicBoolean ANNOUNCED = new AtomicBoolean();
    private static final ClassValue<Boolean> KNOWN_SHAPE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return SHAPES.contains(type.getName());
        }
    };

    private OutlineEdges() {
    }

    /** Records the walk: six doubles per edge, in order. */
    static final class Recorder implements Shapes.DoubleLineConsumer {
        double[] edges = new double[96];
        int size;

        @Override
        public void consume(double x1, double y1, double z1, double x2, double y2, double z2) {
            if (size + 6 > edges.length) edges = Arrays.copyOf(edges, edges.length * 2);
            double[] e = edges;
            e[size] = x1;
            e[size + 1] = y1;
            e[size + 2] = z1;
            e[size + 3] = x2;
            e[size + 4] = y2;
            e[size + 5] = z2;
            size += 6;
        }

        double[] toArray() {
            return Arrays.copyOf(edges, size);
        }
    }

    /** In place of shape.forAllEdges(consumer) in LevelRenderer.renderShape. */
    public static void forAllEdges(VoxelShape shape, Shapes.DoubleLineConsumer consumer, Operation<Void> original) {
        if (!enabled || shape == null || !KNOWN_SHAPE.get(shape.getClass()) || !neighboursKnown()) {
            original.call(shape, consumer);
            return;
        }
        Kept k = kept;
        double[] e;
        if (k != null && k.shape == shape) {
            e = k.edges;
            replayed++;
            if (SHADOW) {
                shadow(shape, e, consumer, original);
                return;
            }
        } else {
            Recorder r = walk(shape, consumer, original);   // the shape's own walk, once
            e = r.toArray();
            kept = new Kept(shape, e);
            recorded++;
        }
        for (int i = 0; i < e.length; i += 6) consumer.consume(e[i], e[i + 1], e[i + 2], e[i + 3], e[i + 4], e[i + 5]);
    }

    /**
     * The shape's walk into a recorder. If the walk fails (vanilla's walk of some face-slice shapes indexes a voxel at -1),
     * the consumer first receives the edges the walk produced before it failed, exactly as the original call hands them out
     * one by one, and then the same exception is thrown; nothing is kept.
     */
    private static Recorder walk(VoxelShape shape, Shapes.DoubleLineConsumer consumer, Operation<Void> original) {
        Recorder r = new Recorder();
        try {
            original.call(shape, r);
        } catch (RuntimeException | Error t) {
            double[] e = r.edges;
            for (int i = 0; i < r.size; i += 6) consumer.consume(e[i], e[i + 1], e[i + 2], e[i + 3], e[i + 4], e[i + 5]);
            throw t;
        }
        return r;
    }

    private static void shadow(VoxelShape shape, double[] e, Shapes.DoubleLineConsumer consumer, Operation<Void> original) {
        Recorder r = walk(shape, consumer, original);
        SHADOW_CHECKS.incrementAndGet();
        double[] fresh = r.toArray();
        if (!Arrays.equals(fresh, e) && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
            LOGGER.warn("Bons and Furious: vanilla_outline_edge_memo SHADOW MISMATCH {}: {} walked {} edges, {} were kept", SHADOW_MISMATCHES.get(),
                    shape, fresh.length / 6, e.length / 6);
        }
        for (int i = 0; i < fresh.length; i += 6) consumer.consume(fresh[i], fresh[i + 1], fresh[i + 2], fresh[i + 3], fresh[i + 4], fresh[i + 5]);
    }

    static boolean neighboursKnown() {
        int n = neighbours;
        if (n != 0) return n == 1;
        String other = unknownMixin();
        n = other == null ? 1 : 2;
        neighbours = n;   // every thread computes the same answer: no lock (see vanilla_rl_path_char_fast)
        if (ANNOUNCED.compareAndSet(false, true)) {
            if (other != null) LOGGER.info("Bons and Furious: vanilla_outline_edge_memo stays off because a shape class also carries {}", other);
            else LOGGER.info("Bons and Furious: vanilla_outline_edge_memo: the block outline replays the edges of the shape it drew last{}",
                    SHADOW ? " (SHADOW MODE: the shape is walked every frame and compared)" : "");
        }
        return n == 1;
    }

    /** The first member some other mixin merged into one of the checked classes (or the known Radium shape classes), or null. */
    static String unknownMixin() {
        try {
            for (String name : CHECKED) {
                String u = unknownMixin(Class.forName(name, false, OutlineEdges.class.getClassLoader()));
                if (u != null) return u;
            }
            for (String name : SHAPES) {
                if (!name.startsWith("me.jellysquid.")) continue;
                Class<?> c;
                try {
                    c = Class.forName(name, false, OutlineEdges.class.getClassLoader());
                } catch (ClassNotFoundException e) {
                    continue;   // Radium not installed
                }
                String u = unknownMixin(c);
                if (u != null) return u;
            }
            return null;
        } catch (Throwable t) {
            return "(check failed: " + t + ")";
        }
    }

    static String unknownMixin(Class<?> c) {
        for (Method m : c.getDeclaredMethods()) {
            MixinMerged merged = m.getAnnotation(MixinMerged.class);
            if (merged != null && !KNOWN_MIXINS.contains(merged.mixin())) return merged.mixin() + " -> " + c.getName() + "." + m.getName();
        }
        return null;
    }
}
