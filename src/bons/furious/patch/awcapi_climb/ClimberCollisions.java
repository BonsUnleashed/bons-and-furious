package bons.furious.patch.awcapi_climb;

import bons.furious.mixin.awcapi_climb.ClimbableViewAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch awcapi_collision_direct (Advanced Wall Climber API 1.0.3, all rights reserved: no awcapi code is
 * carried; nested in Nyf's Spiders 3.0.2; both sides; since 1.0.46). Our own walk; the rules it follows are Minecraft
 * 1.20.1's BlockCollisions, which awcapi uses.
 *
 * <p>awcapi's ClimberComponent.forEachCollisonBox(box, action) runs twice per climber per tick (the walking-side probe and
 * the attachment solve): it builds one filtered view per chunk column of the box (blocks the climber cannot climb read as
 * air), wraps them in a CollisionGetter, takes that getter's getBlockCollisions(mob, box) (Minecraft's BlockCollisions
 * iterator: every cell of the box grown by one block, x fastest, then y, then z; corner cells skipped, edge cells only for
 * blocks with a large collision shape, two-edge cells only for moving pistons; each solid shape moved to its cell and kept
 * when it meets the box) and hands every box of every kept shape to {@code action}. For each solid cell that costs a moved
 * VoxelShape (an ArrayVoxelShape with three OffsetDoubleLists), a BitSet copy in forAllBoxes and a lambda call, and the
 * iterator reads every cell of the box even where the chunk section holds only air.
 *
 * <p>This walk visits the same cells in the same order with the same rules and the same objects (awcapi's own filtered
 * getter and views, the same CollisionContext.of(mob), the same getBlockState / getCollisionShape / hasLargeCollisionShape /
 * MOVING_PISTON calls in the same order, so canClimbOnBlock and every block's shape run exactly as before) and hands the
 * same boxes to {@code action} in the same order. Two differences, neither observable:
 * <ul>
 * <li>A cell whose column chunk is exactly a LevelChunk of a non-debug level and whose section index is out of range or
 * holds only air is skipped: LevelChunk.getBlockState would answer air without reading anything else, the view returns air
 * without asking canClimbOnBlock, and air yields nothing in every rule (its collision shape is empty, also through the
 * pack's three getCollisionShape hooks: Galosphere needs blocksMotion, Kiwi has no settings for vanilla air, fowlplay only
 * changes leaves).</li>
 * <li>A full cube (the shape is Shapes.block() itself) is emitted as {@code (x, y, z, x + 1, y + 1, z + 1)} when it meets
 * the box: bit-identical to Shapes.block().move(x, y, z).forAllBoxes (OffsetDoubleList answers 0.0 + x and 1.0 + x, exact
 * for every integer cell) and to BlockCollisions' own box test. Any other shape takes Minecraft's path: move, isEmpty,
 * Shapes.joinIsNotEmpty with the box's shape, forAllBoxes.</li>
 * </ul>
 * The walk applies only to a plain AABB (CerbonsAPI's Shapes.create hook acts only on its own box subclass) with finite
 * coordinates within +-30,000,000 and every extent at most 32 blocks, so Valkyrien Skies 2.4.11's too-big-box guard in
 * BlockCollisions (volume > 1e8) can never apply; larger boxes run awcapi's code. Our hull-climb mod's hooks are on other
 * ClimberComponent methods and keep running.
 *
 * <p>-Dbons_and_furious.awcapiCollisionDirect=false turns the walk off at runtime. SHADOW MODE for rigs:
 * -Dbons_and_furious.awcapiCollisionDirect.shadow=true runs awcapi's own gathering (whose boxes reach the climber) and this
 * walk into a recorder, compares the two box sequences bit for bit and counts SHADOW_CHECKS / SHADOW_MISMATCHES (WARN for
 * the first 20). In shadow mode canClimbOnBlock and the shape lookups run twice per cell (both are pure reads in this pack).
 */
public final class ClimberCollisions {
    /** Runtime switch. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.awcapiCollisionDirect", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.awcapiCollisionDirect.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Statistics for probes and the rig (plain fields; the climber's own thread writes them). */
    public static long walks, airCellsSkipped, fullCubes;
    private static final double EXTENT_LIMIT = 32.0, COORD_LIMIT = 3.0E7;
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private ClimberCollisions() {
    }

    /** True when this walk may replace awcapi's gathering for this box. */
    public static boolean applies(Mob mob, CollisionGetter reader, AABB box) {
        if (!enabled || mob == null || reader == null || box == null || box.getClass() != AABB.class) return false;
        double x0 = box.f_82288_, y0 = box.f_82289_, z0 = box.f_82290_, x1 = box.f_82291_, y1 = box.f_82292_, z1 = box.f_82293_;
        // !(a <= b) also rejects NaN
        if (!(Math.abs(x0) <= COORD_LIMIT && Math.abs(x1) <= COORD_LIMIT && Math.abs(y0) <= COORD_LIMIT && Math.abs(y1) <= COORD_LIMIT
                && Math.abs(z0) <= COORD_LIMIT && Math.abs(z1) <= COORD_LIMIT)) return false;
        return x1 - x0 <= EXTENT_LIMIT && y1 - y0 <= EXTENT_LIMIT && z1 - z0 <= EXTENT_LIMIT;
    }

    /** One-time INFO line; the caller has checked applies(). */
    public static void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: awcapi_collision_direct: wall climbers gather their collision boxes without moved shapes and skip all-air sections{}",
                SHADOW ? " (SHADOW MODE: awcapi's own gathering runs every time and is compared)" : "");
    }

    /** awcapi's gathering, done directly (see the class comment). {@code reader} is awcapi's own filtered CollisionGetter. */
    public static void walk(Mob mob, CollisionGetter reader, AABB box, Shapes.DoubleLineConsumer action) {
        walks++;
        int x0 = Mth.m_14107_(box.f_82288_ - 1.0E-7) - 1, x1 = Mth.m_14107_(box.f_82291_ + 1.0E-7) + 1;
        int y0 = Mth.m_14107_(box.f_82289_ - 1.0E-7) - 1, y1 = Mth.m_14107_(box.f_82292_ + 1.0E-7) + 1;
        int z0 = Mth.m_14107_(box.f_82290_ - 1.0E-7) - 1, z1 = Mth.m_14107_(box.f_82293_ + 1.0E-7) + 1;
        CollisionContext context = CollisionContext.m_82750_(mob);
        VoxelShape boxShape = null;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        VoxelShape block = Shapes.m_83144_();
        long columnKey = 0;
        boolean haveColumn = false;
        BlockGetter view = null;
        LevelChunk plain = null;
        LevelChunkSection[] sections = null;
        for (int z = z0; z <= z1; z++) {
            int zEdge = z == z0 || z == z1 ? 1 : 0;
            for (int y = y0; y <= y1; y++) {
                int yzEdge = zEdge + (y == y0 || y == y1 ? 1 : 0);
                for (int x = x0; x <= x1; x++) {
                    int type = yzEdge + (x == x0 || x == x1 ? 1 : 0);
                    if (type == 3) continue;
                    int cx = x >> 4, cz = z >> 4;
                    long key = ChunkPos.m_45589_(cx, cz);
                    if (!haveColumn || key != columnKey) {
                        view = reader.m_7925_(cx, cz);
                        columnKey = key;
                        haveColumn = view != null;
                        plain = null;
                        sections = null;
                        if (view instanceof ClimbableViewAccessor a && a.bons$climbChunk() instanceof LevelChunk lc && lc.getClass() == LevelChunk.class
                                && !lc.m_62953_().m_46659_()) {
                            plain = lc;
                            sections = lc.m_7103_();
                        }
                    }
                    if (view == null) continue;
                    if (plain != null) {
                        int index = plain.m_151564_(y);
                        if (index < 0 || index >= sections.length || sections[index].m_188008_()) {
                            airCellsSkipped++;
                            continue;
                        }
                    }
                    pos.m_122178_(x, y, z);
                    BlockState state = view.m_8055_(pos);
                    if (type == 1 && !state.m_60779_()) continue;
                    if (type == 2 && !state.m_60713_(Blocks.f_50110_)) continue;
                    VoxelShape shape = state.m_60742_(reader, pos, context);
                    if (shape == block) {
                        if (box.m_82314_(x, y, z, x + 1.0, y + 1.0, z + 1.0)) {
                            fullCubes++;
                            action.m_83161_(x, y, z, x + 1.0, y + 1.0, z + 1.0);
                        }
                    } else {
                        VoxelShape moved = shape.m_83216_(x, y, z);
                        if (!moved.m_83281_()) {
                            if (boxShape == null) boxShape = Shapes.m_83064_(box);
                            if (Shapes.m_83157_(moved, boxShape, BooleanOp.f_82689_)) moved.m_83286_(action);
                        }
                    }
                }
            }
        }
    }

    /** Shadow mode: this walk's boxes, bit for bit. */
    public static List<double[]> record(Mob mob, CollisionGetter reader, AABB box) {
        List<double[]> out = new ArrayList<>();
        walk(mob, reader, box, (a, b, c, d, e, f) -> out.add(new double[]{a, b, c, d, e, f}));
        return out;
    }

    /** Shadow mode: compares the boxes awcapi produced with this walk's. */
    public static void compare(List<double[]> theirs, List<double[]> ours, AABB box) {
        SHADOW_CHECKS.incrementAndGet();
        boolean same = theirs.size() == ours.size();
        for (int i = 0; same && i < theirs.size(); i++) {
            for (int k = 0; k < 6; k++) {
                if (Double.doubleToRawLongBits(theirs.get(i)[k]) != Double.doubleToRawLongBits(ours.get(i)[k])) {
                    same = false;
                    break;
                }
            }
        }
        if (!same && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: awcapi_collision_direct SHADOW MISMATCH {}: box {}: awcapi gave {} boxes, the direct walk {}",
                    SHADOW_MISMATCHES.get(), box, theirs.size(), ours.size());
    }
}
