package bons.furious.patch.iceandfire_dens_c49;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * iceandfire_structure_dens (since 1.0.49): the copy-on-write view a cave is generated against. Reads come from the cave's
 * own earlier writes, else they are plain stone: a cave's generator reads the world only to leave block entities and
 * unbreakable blocks alone (its shell and hollow) and to find the floor its treasure lies on, and while the first chunk of
 * a cave is decorated most of the cave's chunks have no blocks yet. With every unwritten block read as stone, the cave is a
 * function of its seed and centre only (the same wherever and whenever it is generated); the chunk that writes a part keeps
 * the block entities and unbreakable blocks it actually holds (DenResult.commit). Writes, chests and entities stay here.
 * Ice and Fire calls a WorldGenLevel, so the view is a dynamic proxy of that interface: the block, fluid, block entity and
 * entity calls are answered here, the interface's own default helpers (isEmptyBlock) run on top of them, everything else
 * goes to the world.
 */
final class DenLevel implements InvocationHandler {
    static final BlockState UNWRITTEN = Blocks.f_50069_.m_49966_();
    final WorldGenLevel world;
    final WorldGenLevel view;
    final Long2ObjectOpenHashMap<BlockState> states = new Long2ObjectOpenHashMap<>();
    final Long2ByteOpenHashMap flags = new Long2ByteOpenHashMap();
    final Long2ObjectOpenHashMap<Chest> chests = new Long2ObjectOpenHashMap<>();
    final List<Entity> entities = new ArrayList<>();

    private DenLevel(WorldGenLevel world) {
        this.world = world;
        this.view = (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(), new Class<?>[] {WorldGenLevel.class}, this);
    }

    static DenLevel over(WorldGenLevel world) {
        return new DenLevel(world);
    }

    /** A chest the cave placed: Ice and Fire sets its loot table here, the chunk that holds it copies it to the real chest. */
    static final class Chest extends ChestBlockEntity {
        Chest(BlockPos pos, BlockState state) {
            super(pos, state);
        }

        ResourceLocation table() {
            return this.f_59605_;
        }

        long seed() {
            return this.f_59606_;
        }
    }

    BlockState state(BlockPos pos) {
        BlockState s = states.get(pos.m_121878_());
        return s != null ? s : UNWRITTEN;
    }

    boolean set(BlockPos pos, BlockState state, int flag) {
        long key = pos.m_121878_();
        states.put(key, state);
        flags.put(key, (byte) flag);
        if (!(state.m_60734_() instanceof ChestBlock)) chests.remove(key);
        return true;
    }

    Object blockEntity(BlockPos pos) {
        long key = pos.m_121878_();
        BlockState s = states.get(key);
        if (s == null || !(s.m_60734_() instanceof ChestBlock)) return null;
        Chest c = chests.get(key);
        if (c == null) chests.put(key, c = new Chest(pos.m_7949_(), s));
        return c;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        int n = args == null ? 0 : args.length;
        switch (method.getName()) {
            case "m_8055_":   // getBlockState
                if (n == 1) return state((BlockPos) args[0]);
                break;
            case "m_6425_":   // getFluidState
                if (n == 1) return state((BlockPos) args[0]).m_60819_();
                break;
            case "m_7731_":   // setBlock(pos, state, flags)
                if (n == 3) return set((BlockPos) args[0], (BlockState) args[1], (Integer) args[2]);
                break;
            case "m_6933_":   // setBlock(pos, state, flags, recursionLeft)
                if (n == 4) return set((BlockPos) args[0], (BlockState) args[1], (Integer) args[2]);
                break;
            case "m_7471_":   // removeBlock(pos, isMoving)
                if (n == 2) return set((BlockPos) args[0], state((BlockPos) args[0]).m_60819_().m_76188_(), 3);
                break;
            case "m_7702_":   // getBlockEntity
                if (n == 1) return blockEntity((BlockPos) args[0]);
                break;
            case "m_7967_":   // addFreshEntity
                if (n == 1) {
                    entities.add((Entity) args[0]);
                    return true;
                }
                break;
            case "m_46859_":  // isEmptyBlock: the interface's own helper, on this view's getBlockState
                if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
                break;
            case "equals":
                if (n == 1) return proxy == args[0];
                break;
            case "hashCode":
                if (n == 0) return System.identityHashCode(proxy);
                break;
            case "toString":
                if (n == 0) return "cave view of " + world;
                break;
            default:
                break;
        }
        try {
            return method.invoke(world, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
