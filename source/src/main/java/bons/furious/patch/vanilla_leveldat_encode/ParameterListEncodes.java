package bons.furious.patch.vanilla_leveldat_encode;

import com.google.common.collect.MapMaker;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagTypes;
import net.minecraft.resources.DelegatingOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.biome.Climate;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_leveldat_encode_reuse (Minecraft 1.20.1, server side; SRG names for Minecraft members).
 *
 * What it costs. Every level.dat save (each autosave and the stop) runs PrimaryLevelData.createTag, which encodes the
 * whole WorldGenSettings through the codecs again. Dimensions whose multi_noise biome source carries its parameter list
 * inline encode that list entry by entry each time: in this pack graveyard:past (7,593 entries, 1.52 MB of NBT) and the
 * overworld (Terralith's inline list, 0.36 MB) take about 20 of createTag's 25 ms on the server thread.
 *
 * What the switch does. Climate.ParameterList.codec(entry) returns its codec wrapped in Cached. When that codec encodes a
 * parameter list of at least MIN_ENTRIES entries, each a bound registry holder, through RegistryOps over NbtOps into an
 * empty prefix, and the result is a full success, the result's NBT bytes are kept for that ParameterList object (weak key),
 * together with the list object, an array of its elements, the biome registry's holder owner of the ops, and the result's
 * lifecycle. The next encode of the same ParameterList returns a NEW tag read from those bytes when the list is still the
 * same object holding the same element objects and the owner is the same; anything else encodes as before.
 *
 * Why the result is identical, down to the level.dat bytes. A ParameterList is immutable here: its list field is final,
 * each element is a DFU Pair (final fields) of a ParameterPoint record (final longs) and a Holder.Reference whose key never
 * changes once bound. The codec writes each entry from those values only (the point's floats, the holder's key through
 * RegistryFileCodec, which needs the same holder owner to allow it), in list order, so the same elements and the same owner
 * give the same tag. The tag is handed out as a fresh object, so nothing done to one save's tag reaches the next. It is
 * READ from the bytes rather than copied: CompoundTag.copy() sizes its HashMap from the entry count, which can change the
 * order its keys are written in; reading builds every compound the way the codec does (a default HashMap, entries in the
 * order they were written), so the saved bytes are the same. NBT reading and writing are exact for every tag type.
 *
 * -Dbons_and_furious.levelDatEncodeReuse=false encodes every time (checked per encode).
 * -Dbons_and_furious.levelDatEncodeReuse.shadow=true (verification runs only): every reuse also encodes the list afresh and
 * compares the bytes (SHADOW_CHECKS / SHADOW_MISMATCHES); the reused tag is returned.
 */
public final class ParameterListEncodes {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.levelDatEncodeReuse", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.levelDatEncodeReuse.shadow");
    public static final AtomicLong HITS = new AtomicLong(), STORED = new AtomicLong(), SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Smaller lists encode quickly; keeping their bytes is not worth it. */
    public static final int MIN_ENTRIES = 256;
    private static final Field DELEGATE = delegateField();
    private static volatile boolean announced;

    private ParameterListEncodes() {
    }

    /** The codec Climate.ParameterList.codec returns, wrapped (raw types: the mixin handler sees Codec only). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Codec wrapAny(Codec codec) {
        return codec == null ? null : new Cached(codec);
    }

    private static Field delegateField() {
        try {
            Field f = DelegatingOps.class.getDeclaredField("delegate");      // DelegatingOps.delegate
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Bons and Furious: vanilla_leveldat_encode_reuse cannot read DelegatingOps.delegate ({}); level.dat encodes as before", e.toString());
            return null;
        }
    }

    /** NbtOps when the ops are RegistryOps over it (level.dat's), else null. */
    static Object nbtDelegate(RegistryOps<?> ops) {
        try {
            return DELEGATE == null ? null : DELEGATE.get(ops);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    static boolean boundReferences(List<?> values) {
        for (Object o : values) {
            if (!(o instanceof Pair<?, ?> p) || !(p.getSecond() instanceof Holder.Reference<?> r) || !r.isBound() || r.unwrapKey().isEmpty()) return false;
        }
        return true;
    }

    /** The tag as NBT bytes (type id, then the payload), or null. Public for the proof. */
    public static byte[] bytes(Tag tag) {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream(1 << 16);
            DataOutputStream out = new DataOutputStream(raw);
            out.writeByte(tag.getId());
            tag.write(out);
            out.flush();
            return raw.toByteArray();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** A new tag read from bytes(tag), or null. Public for the proof. */
    public static Tag read(byte[] bytes) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            byte id = in.readByte();
            return TagTypes.getType(id).load(in, NbtAccounter.unlimitedHeap());
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** One kept encoding: valid while the list is the same object with the same elements and the owner is the same. */
    record Entry(Object owner, List<?> values, Object[] elements, byte[] bytes, Lifecycle lifecycle) {
        boolean same(Object owner, List<?> values) {
            if (owner != this.owner || values != this.values || values.size() != this.elements.length) return false;
            for (int i = 0; i < this.elements.length; i++) if (values.get(i) != this.elements[i]) return false;
            return true;
        }
    }

    static final class Cached<T> implements Codec<Climate.ParameterList<T>> {
        private final Codec<Climate.ParameterList<T>> delegate;
        private final ConcurrentMap<Object, Entry> kept = new MapMaker().weakKeys().makeMap();

        Cached(Codec<Climate.ParameterList<T>> delegate) {
            this.delegate = delegate;
        }

        @Override
        public <O> DataResult<Pair<Climate.ParameterList<T>, O>> decode(DynamicOps<O> ops, O input) {
            return this.delegate.decode(ops, input);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <O> DataResult<O> encode(Climate.ParameterList<T> input, DynamicOps<O> ops, O prefix) {
            if (!enabled || input == null || !(ops instanceof RegistryOps<O> registryOps) || prefix != ops.empty() || nbtDelegate(registryOps) != NbtOps.INSTANCE)
                return this.delegate.encode(input, ops, prefix);
            Object owner = registryOps.owner(Registries.BIOME).orElse(null);
            List<?> values = input.values();
            Entry e = owner == null ? null : this.kept.get(input);
            if (e != null && e.same(owner, values)) {
                Tag tag = read(e.bytes());
                if (tag != null) {
                    HITS.incrementAndGet();
                    if (SHADOW) shadow(this.delegate.encode(input, ops, prefix), e.bytes());
                    return DataResult.success((O) tag, e.lifecycle());
                }
            }
            DataResult<O> result = this.delegate.encode(input, ops, prefix);
            if (owner != null && values.size() >= MIN_ENTRIES && result.error().isEmpty() && result.result().orElse(null) instanceof Tag t
                    && boundReferences(values)) {
                byte[] b = bytes(t);
                if (b != null) {
                    this.kept.put(input, new Entry(owner, values, values.toArray(), b, result.lifecycle()));
                    STORED.incrementAndGet();
                    if (!announced) {
                        announced = true;
                        LOGGER.info("Bons and Furious: vanilla_leveldat_encode_reuse applies (an inline biome parameter list of {} entries is written into level.dat from its kept encoding while it is unchanged){}",
                                values.size(), SHADOW ? " - shadow verification on" : "");
                    }
                }
            }
            return result;
        }

        private static <O> void shadow(DataResult<O> fresh, byte[] kept) {
            SHADOW_CHECKS.incrementAndGet();
            byte[] now = fresh.error().isEmpty() && fresh.result().orElse(null) instanceof Tag t ? bytes(t) : null;
            if (!Arrays.equals(now, kept) && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                LOGGER.warn("Bons and Furious: vanilla_leveldat_encode_reuse shadow mismatch: fresh encoding {} bytes, kept {} bytes", now == null ? -1 : now.length, kept.length);
        }
    }
}
