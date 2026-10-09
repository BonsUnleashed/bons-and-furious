package bons.furious.patch.vanilla_chunk_codecs;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_state_codec_dispatch_memo (Minecraft 1.20.1 on Forge 47.4.16; both sides, mostly the
 * server's chunk saving and loading). SRG member names. Idea: C2ME / Gale / Leaves / Bellows texts on faster chunk
 * serialization, idea text only; the design is ours.
 *
 * What vanilla does. BlockState.CODEC is StateHolder.codec(Block.CODEC, Block::defaultBlockState) (m_61127_): a
 * Codec.dispatch on the block whose per-block codec function (the lambda m_187545_) BUILDS A NEW CODEC ON EVERY CALL:
 * `defaultState.propertiesCodec.codec().optionalFieldOf("Properties").xmap(o -> o.orElse(defaultState), Optional::of)
 * .codec()`, or `Codec.unit(defaultState)` for a block without properties. DFU 6.0.8's KeyDispatchCodec calls that
 * function once per encoded or decoded block state, so every palette entry of every chunk section that is saved or
 * loaded allocates a fresh OptionalFieldCodec, the three objects xmap makes, a MapCodecCodec and the lambdas, and every
 * one of those MapCodecs is a CompressorHolder whose field initializer allocates an Object2ObjectArrayMap.
 *
 * What the switch does. The function BlockState.<clinit> hands to Codec.dispatch is wrapped (Dispatch): for each block
 * the codec the original function built is kept in a map of that wrapper, together with the default state it was built
 * from, and handed back while Block.defaultBlockState() (m_49966_, a public final getter) still returns that same state
 * object. Anything else (the switch off, another default state, the other caller FluidState) runs the original function.
 *
 * Why the result is identical. The codec the original builds is a pure function of the default-state object: its only
 * inputs are that state, its final propertiesCodec field and its immutable property map, DFU's optionalFieldOf / xmap /
 * codec / unit, and the constant key "Properties". DFU codecs keep no per-call state; the one mutable piece, each
 * CompressorHolder's lazily filled key-compressor map, is touched only by DynamicOps that compress maps (NbtOps and
 * JsonOps.INSTANCE do not) and fills deterministically. Encoding and decoding with the kept codec therefore produce the
 * same values, the same errors (messages included) and the same NBT bytes as with a fresh one. The getter is called once
 * per call as before (the original function calls it inside; a hit calls it instead), and twice on a miss (pure: it is
 * final and only returns the field). The table is keyed by block identity. Only BlockState.<clinit> and FluidState.<clinit>
 * call m_61127_ (census of every jar of both instances, 2026-10-09).
 *
 * -Dbons_and_furious.stateCodecDispatchMemo=false switches it off at run time.
 * -Dbons_and_furious.stateCodecDispatchMemo.shadow=true (verification runs only): on every 64th hit the original
 * function also builds a fresh codec, and every possible state of that block (up to 256) is encoded with both through
 * NbtOps and the results decoded again with both; SHADOW_CHECKS counts compared states, SHADOW_MISMATCHES any difference
 * in the encoded tag or the decoded state (WARN for the first 20).
 */
public final class StateCodecMemo {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.stateCodecDispatchMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.stateCodecDispatchMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static final AtomicLong HITS = new AtomicLong();
    /** Codecs built and kept (one per block, again if a block's default state object changes); test and log support. */
    public static final AtomicLong KEPT = new AtomicLong();
    private static volatile boolean announced;
    static final String STATE_HOLDER = "net.minecraft.world.level.block.state.StateHolder";
    static final String BLOCK_STATE = "net.minecraft.world.level.block.state.BlockState";

    /** The kept codec and the default state of its block when it was built. Immutable. */
    record Memo(Object defaultState, Object codec) {
    }

    private StateCodecMemo() {
    }

    /**
     * StateHolder.codec (m_61127_): the codec function handed to Codec.dispatch, wrapped when m_61127_ was called by
     * BlockState's static initializer (BlockState.CODEC, whose default-state function is Block::defaultBlockState; the guard
     * fingerprints that initializer). Any other caller gets its function back unchanged. Runs twice per JVM.
     */
    public static <O, C> Function<O, C> wrap(Function<O, C> codecFunction) {
        String caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames -> frames
                .map(f -> f.getDeclaringClass().getName())
                .filter(n -> !n.equals(STATE_HOLDER) && !n.equals(StateCodecMemo.class.getName()))
                .findFirst().orElse(""));
        return BLOCK_STATE.equals(caller) ? new Dispatch<>(codecFunction) : codecFunction;
    }

    /**
     * The function BlockState.CODEC's dispatch calls, with the kept codecs in an identity table (IdentityTable: lock-free
     * reads, locked writes), so a block class that overrides equals or hashCode is handled like any other.
     */
    static final class Dispatch<O, C> implements Function<O, C> {
        private final Function<O, C> original;
        private final IdentityTable kept = new IdentityTable();

        Dispatch(Function<O, C> original) {
            this.original = original;
        }

        @Override
        @SuppressWarnings("unchecked")
        public C apply(O owner) {
            if (!enabled || !(owner instanceof Block block)) {
                return this.original.apply(owner);
            }
            // Block.defaultBlockState(): what the original function asks first (Block::defaultBlockState); public final
            Object state = block.m_49966_();
            Memo m = (Memo) this.kept.get(owner);
            if (m != null && m.defaultState() == state) {
                if (SHADOW && (HITS.incrementAndGet() & 63) == 0) {
                    shadow(block, (Codec<Object>) m.codec(), (Codec<Object>) this.original.apply(owner));
                }
                return (C) m.codec();
            }
            C codec = this.original.apply(owner);
            this.kept.put(owner, new Memo(state, codec));
            KEPT.incrementAndGet();
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: vanilla_state_codec_dispatch_memo applies (block state codecs are built once per block, not once per palette entry){}",
                        SHADOW ? " - shadow verification on" : "");
            }
            return codec;
        }

    }

    /** Shadow: every possible state of the block, encoded and decoded with the kept codec and a fresh one. */
    static void shadow(Block block, Codec<Object> kept, Codec<Object> fresh) {
        try {
            List<?> states = block.m_49965_().m_61056_();
            int n = Math.min(states.size(), 256);
            for (int i = 0; i < n; i++) {
                Object state = states.get(i);
                DataResult<Tag> a = kept.encodeStart(NbtOps.f_128958_, state);
                DataResult<Tag> b = fresh.encodeStart(NbtOps.f_128958_, state);
                boolean same = String.valueOf(a.result().orElse(null)).equals(String.valueOf(b.result().orElse(null)))
                        && String.valueOf(a.error().map(e -> e.message()).orElse(null)).equals(String.valueOf(b.error().map(e -> e.message()).orElse(null)));
                if (same && a.result().isPresent()) {
                    Object da = kept.parse(NbtOps.f_128958_, a.result().get()).result().orElse(null);
                    Object db = fresh.parse(NbtOps.f_128958_, a.result().get()).result().orElse(null);
                    same = da == db;
                }
                SHADOW_CHECKS.incrementAndGet();
                if (!same) {
                    long k = SHADOW_MISMATCHES.incrementAndGet();
                    if (k <= 20) LOGGER.warn("Bons and Furious: vanilla_state_codec_dispatch_memo shadow mismatch #{} for {}", k, state);
                }
            }
        } catch (Throwable t) {
            long k = SHADOW_MISMATCHES.incrementAndGet();
            if (k <= 20) LOGGER.warn("Bons and Furious: vanilla_state_codec_dispatch_memo shadow check failed for {}", block, t);
        }
    }
}
