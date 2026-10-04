package bons.furious.mixin.jei_search;

import bons.furious.patch.jei_search.BackgroundGrams;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import java.util.Set;
import mezz.jei.modshade.net.mezzdev.bakedsubstring.BakedSubstringIndex;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * jei_baked_index_background_grams (JEI 19.51.0.418 for Minecraft 1.21.1 / NeoForge, tested build
 * jei-1.21.1-neoforge-19.51.0.418.jar; client only).
 *
 * BakedSubstringIndex.Builder (JEI's shaded net.mezzdev.bakedsubstring) collects key/value pairs in put() and does all
 * the gram work in build(). Here put() still appends exactly as before and then, once the builder holds
 * BackgroundGrams.START entries, also hands the key String to a background thread that computes the gram postings.
 * build() still runs its own code (the key and value arrays, the repeated-value scan, the constructor call), with three
 * changes while the background postings are ready: the per-key addGrams call does nothing, the repeated-value scan stops
 * adding to its local set once it has seen a repeat (the flag is already set and can only stay set), and the constructor
 * receives the background gram map, which has the same postings and the same layout as the map addGrams would have built
 * (BackgroundGrams class comment). Wherever BackgroundGrams declines, and with the runtime switch off, build() runs
 * unchanged. JEI is MIT; no JEI code is carried.
 *
 * Ported to 1.21.1: no change. BakedSubstringIndex and BakedSubstringIndex$Builder decompile identically in JEI
 * 15.59.0.212 and 19.51.0.418 (put, build, addGrams, the private constructors, encodeGram, the four final fields), build()
 * still makes one addGrams call, one Set.add call and one NEW BakedSubstringIndex; ElementSearch still puts and builds
 * each prefix's storage on one thread.
 */
@Mixin(value = BakedSubstringIndex.Builder.class, remap = false)
public abstract class BakedIndexBuilderMixin implements BackgroundGrams.Holder {
    @Shadow
    @Final
    private List<String> keys;

    @Shadow
    @Final
    private List<Object> values;

    @Unique
    private Object bons$grams;

    @Unique
    private Long2ObjectOpenHashMap<int[]> bons$ready;

    @Unique
    private boolean bons$repeatSeen;

    @Override
    public List<String> bons$keys() {
        return this.keys;
    }

    @Override
    public Object bons$grams() {
        return this.bons$grams;
    }

    @Override
    public void bons$grams(Object state) {
        this.bons$grams = state;
    }

    @WrapMethod(method = "put(Ljava/lang/String;Ljava/lang/Object;)Lmezz/jei/modshade/net/mezzdev/bakedsubstring/BakedSubstringIndex$Builder;")
    private BakedSubstringIndex.Builder<?> bons$handKey(String key, Object value, Operation<BakedSubstringIndex.Builder<?>> original) {
        BakedSubstringIndex.Builder<?> self = original.call(key, value);
        if (BackgroundGrams.enabled) BackgroundGrams.afterPut(this, key);
        return self;
    }

    @WrapMethod(method = "build()Lmezz/jei/modshade/net/mezzdev/bakedsubstring/BakedSubstringIndex;")
    private BakedSubstringIndex<?> bons$assemble(Operation<BakedSubstringIndex<?>> original) {
        if (this.bons$grams == null) return original.call();
        Long2ObjectOpenHashMap<int[]> ready = BackgroundGrams.prepare(this);
        if (ready == null) return original.call();
        BakedSubstringIndex<?> ours;
        this.bons$ready = ready;
        this.bons$repeatSeen = false;
        try {
            ours = original.call();
        } finally {
            this.bons$ready = null;
        }
        BackgroundGrams.built(this.keys.size());
        if (BackgroundGrams.SHADOW) {
            BakedSubstringIndex<?> orig = original.call();
            BackgroundGrams.shadow(orig, ours);
            return orig;
        }
        return ours;
    }

    @WrapOperation(method = "build()Lmezz/jei/modshade/net/mezzdev/bakedsubstring/BakedSubstringIndex;",
            at = @At(value = "INVOKE", target = "Lmezz/jei/modshade/net/mezzdev/bakedsubstring/BakedSubstringIndex$Builder;addGrams(Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;Lit/unimi/dsi/fastutil/longs/Long2IntOpenHashMap;Ljava/lang/String;I)V"))
    private void bons$backgroundGrams(Long2ObjectOpenHashMap<?> entriesByGram, Long2IntOpenHashMap lastEntryByGram, String key, int entryIndex, Operation<Void> original) {
        if (this.bons$ready == null) original.call(entriesByGram, lastEntryByGram, key, entryIndex);
    }

    @WrapOperation(method = "build()Lmezz/jei/modshade/net/mezzdev/bakedsubstring/BakedSubstringIndex;",
            at = @At(value = "INVOKE", target = "Ljava/util/Set;add(Ljava/lang/Object;)Z"))
    private boolean bons$firstRepeat(Set<Object> seen, Object value, Operation<Boolean> original) {
        if (this.bons$ready != null && this.bons$repeatSeen) return true;
        boolean added = original.call(seen, value);
        if (!added && this.bons$ready != null) this.bons$repeatSeen = true;
        return added;
    }

    @WrapOperation(method = "build()Lmezz/jei/modshade/net/mezzdev/bakedsubstring/BakedSubstringIndex;",
            at = @At(value = "NEW", target = "mezz/jei/modshade/net/mezzdev/bakedsubstring/BakedSubstringIndex"))
    private BakedSubstringIndex<?> bons$withPostings(String[] keyArray, Object[] valueArray, Long2ObjectOpenHashMap<int[]> entriesByGram, boolean deduplicate,
                                                     Operation<BakedSubstringIndex<?>> original) {
        Long2ObjectOpenHashMap<int[]> ready = this.bons$ready;
        return original.call(keyArray, valueArray, ready != null ? ready : entriesByGram, deduplicate);
    }
}
