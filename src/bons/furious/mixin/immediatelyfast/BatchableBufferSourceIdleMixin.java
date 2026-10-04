package bons.furious.mixin.immediatelyfast;

import bons.furious.patch.immediatelyfast.IdleLayers;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.raphimc.immediatelyfast.compat.IrisCompat;
import net.raphimc.immediatelyfast.feature.core.BatchableBufferSource;
import net.raphimc.immediatelyfast.feature.core.BufferBuilderPool;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * immediatelyfast_idle_layer_skip (ImmediatelyFast 1.5.5+1.20.4, Forge; client).
 *
 * endBatch(), endBatch(layer), close(), getOrCreateBufferBuilder and addNewFallbackBuffer are overwritten (ImmediatelyFast
 * is LGPL-3.0; each body keeps ImmediatelyFast's own code as the runtime-off path), and the two linked collections the
 * constructor builds start with room for 4 layers. IdleLayers documents, per change, why the result is ImmediatelyFast's
 * own: idle fixed layers are left out of a flush only when their endBatch(layer) could not draw or change any state but
 * the Iris flag (which ends where ImmediatelyFast leaves it); fixed builders are ended without a one-element set; empty
 * sets are not iterated; lookups are single; drawn fallback sets are reused. Subclasses of BatchableBufferSource keep
 * ImmediatelyFast's code in full. Our immediatelyfast_offset_layer_prefixes (getLayerOrder) is untouched.
 */
@Mixin(value = BatchableBufferSource.class, remap = false)
public abstract class BatchableBufferSourceIdleMixin extends MultiBufferSource.BufferSource {
    @Shadow
    @Final
    protected Map<RenderType, ReferenceSet<BufferBuilder>> fallbackBuffers;

    @Shadow
    @Final
    protected Set<RenderType> activeLayers;

    @Shadow
    protected boolean drawFallbackLayersFirst;

    @Unique
    private IdleLayers.SetPool bons$setPool;

    protected BatchableBufferSourceIdleMixin(BufferBuilder fallback, Map<RenderType, BufferBuilder> fixed) {
        super(fallback, fixed);
    }

    @Shadow
    protected abstract Set<BufferBuilder> getBufferBuilder(RenderType layer);

    // ------------------------------------------------------------------ construction: smaller initial tables

    @Redirect(method = "<init>(Lcom/mojang/blaze3d/vertex/BufferBuilder;Ljava/util/Map;)V",
            at = @At(value = "NEW", target = "()Lit/unimi/dsi/fastutil/objects/Object2ObjectLinkedOpenHashMap;"))
    private Object2ObjectLinkedOpenHashMap<?, ?> bons$fallbackMapIris() {
        return IdleLayers.enabled ? new Object2ObjectLinkedOpenHashMap<>(IdleLayers.INITIAL_LAYERS) : new Object2ObjectLinkedOpenHashMap<>();
    }

    @Redirect(method = "<init>(Lcom/mojang/blaze3d/vertex/BufferBuilder;Ljava/util/Map;)V",
            at = @At(value = "NEW", target = "()Lit/unimi/dsi/fastutil/objects/Reference2ObjectLinkedOpenHashMap;"))
    private Reference2ObjectLinkedOpenHashMap<?, ?> bons$fallbackMap() {
        return IdleLayers.enabled ? new Reference2ObjectLinkedOpenHashMap<>(IdleLayers.INITIAL_LAYERS) : new Reference2ObjectLinkedOpenHashMap<>();
    }

    @Redirect(method = "<init>(Lcom/mojang/blaze3d/vertex/BufferBuilder;Ljava/util/Map;)V",
            at = @At(value = "NEW", target = "()Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;"))
    private ObjectLinkedOpenHashSet<?> bons$activeSetIris() {
        return IdleLayers.enabled ? new ObjectLinkedOpenHashSet<>(IdleLayers.INITIAL_LAYERS) : new ObjectLinkedOpenHashSet<>();
    }

    @Redirect(method = "<init>(Lcom/mojang/blaze3d/vertex/BufferBuilder;Ljava/util/Map;)V",
            at = @At(value = "NEW", target = "()Lit/unimi/dsi/fastutil/objects/ReferenceLinkedOpenHashSet;"))
    private ReferenceLinkedOpenHashSet<?> bons$activeSet() {
        return IdleLayers.enabled ? new ReferenceLinkedOpenHashSet<>(IdleLayers.INITIAL_LAYERS) : new ReferenceLinkedOpenHashSet<>();
    }

    // ------------------------------------------------------------------ flushes

    /**
     * @author BonsUnleashed
     * @reason Leave out the fixed layers whose endBatch(layer) could not draw anything (see IdleLayers).
     */
    @Overwrite
    public void m_109911_() {
        if (!IdleLayers.plain(this)) {
            if (this.activeLayers.isEmpty()) {
                this.close();
                return;
            }
            this.m_173043_();
            for (RenderType layer : this.f_109905_.keySet()) {
                this.m_109912_(layer);
            }
            return;
        }
        if (this.activeLayers.isEmpty()) {
            this.close();
            return;
        }
        this.m_173043_();
        boolean skipped = false;
        for (RenderType layer : this.f_109905_.keySet()) {
            if (!this.drawFallbackLayersFirst) {
                BufferBuilder fixed = this.f_109905_.get(layer);
                if (fixed != null && !fixed.m_85732_() && !this.activeLayers.contains(layer) && !this.fallbackBuffers.containsKey(layer)
                        && IdleLayers.plainEnd(layer)) {
                    if (!skipped) {
                        skipped = true;
                        RenderSystem.getVertexSorting();   // ImmediatelyFast reads it for this layer: keep its render-thread assertion
                    }
                    continue;
                }
            }
            this.m_109912_(layer);
        }
        if (skipped && IrisCompat.IRIS_LOADED && !IrisCompat.isRenderingLevel.getAsBoolean()) {
            IrisCompat.renderWithExtendedVertexFormat.accept(true);
        }
    }

    /**
     * @author BonsUnleashed
     * @reason End a fixed layer's builder directly instead of through a one-element set; reuse the drawn fallback set.
     */
    @Overwrite
    public void m_109912_(RenderType layer) {
        if (this.drawFallbackLayersFirst) {
            this.m_173043_();
        }
        if (IrisCompat.IRIS_LOADED && !IrisCompat.isRenderingLevel.getAsBoolean()) {
            IrisCompat.renderWithExtendedVertexFormat.accept(false);
        }
        this.activeLayers.remove(layer);
        if (IdleLayers.plain(this)) {
            ReferenceSet<BufferBuilder> fallback = this.fallbackBuffers.get(layer);
            if (fallback != null) {
                for (BufferBuilder bufferBuilder : fallback) {
                    if (bufferBuilder == null) continue;
                    layer.m_276775_(bufferBuilder, RenderSystem.getVertexSorting());
                }
            } else {
                BufferBuilder fixed = this.f_109905_.get(layer);
                if (fixed != null) {
                    layer.m_276775_(fixed, RenderSystem.getVertexSorting());
                }
            }
            ReferenceSet<BufferBuilder> removed = this.fallbackBuffers.remove(layer);
            if (removed != null) {
                this.bons$pool().give(removed);
            }
        } else {
            for (BufferBuilder bufferBuilder : this.getBufferBuilder(layer)) {
                if (bufferBuilder == null) continue;
                layer.m_276775_(bufferBuilder, RenderSystem.getVertexSorting());
            }
            this.fallbackBuffers.remove(layer);
        }
        if (IrisCompat.IRIS_LOADED && !IrisCompat.isRenderingLevel.getAsBoolean()) {
            IrisCompat.renderWithExtendedVertexFormat.accept(true);
        }
    }

    /**
     * @author BonsUnleashed
     * @reason Do not iterate an empty active set; keep the dropped fallback sets for reuse.
     */
    @Overwrite
    public void close() {
        this.f_109906_ = Optional.empty();
        this.drawFallbackLayersFirst = false;
        if (!IdleLayers.plain(this)) {
            for (RenderType layer : this.activeLayers) {
                for (BufferBuilder bufferBuilder : this.getBufferBuilder(layer)) {
                    bufferBuilder.m_231175_().m_231200_();
                }
            }
            this.activeLayers.clear();
            this.fallbackBuffers.clear();
            return;
        }
        if (!this.activeLayers.isEmpty()) {
            for (RenderType layer : this.activeLayers) {
                ReferenceSet<BufferBuilder> fallback = this.fallbackBuffers.get(layer);
                if (fallback != null) {
                    for (BufferBuilder bufferBuilder : fallback) {
                        bufferBuilder.m_231175_().m_231200_();
                    }
                } else if (this.f_109905_.containsKey(layer)) {
                    this.f_109905_.get(layer).m_231175_().m_231200_();
                }
            }
        }
        this.activeLayers.clear();
        if (!this.fallbackBuffers.isEmpty()) {
            IdleLayers.SetPool pool = this.bons$pool();
            for (ReferenceSet<BufferBuilder> set : this.fallbackBuffers.values()) {
                pool.give(set);
            }
            this.fallbackBuffers.clear();
        }
    }

    /**
     * @author BonsUnleashed
     * @reason One lookup per map instead of containsKey followed by get.
     */
    @Overwrite
    protected BufferBuilder getOrCreateBufferBuilder(RenderType layer) {
        if (!layer.m_234326_()) {
            return this.addNewFallbackBuffer(layer);
        }
        if (IdleLayers.plain(this)) {
            BufferBuilder fixed = this.f_109905_.get(layer);
            if (fixed != null) {
                return fixed;
            }
            ReferenceSet<BufferBuilder> fallback = this.fallbackBuffers.get(layer);
            if (fallback != null) {
                return fallback.iterator().next();
            }
            return this.addNewFallbackBuffer(layer);
        }
        if (this.f_109905_.containsKey(layer)) {
            return this.f_109905_.get(layer);
        }
        if (this.fallbackBuffers.containsKey(layer)) {
            return this.fallbackBuffers.get(layer).iterator().next();
        }
        return this.addNewFallbackBuffer(layer);
    }

    /**
     * @author BonsUnleashed
     * @reason Take the layer's fallback set from the source's pool of emptied sets.
     */
    @Overwrite
    protected BufferBuilder addNewFallbackBuffer(RenderType layer) {
        BufferBuilder bufferBuilder = BufferBuilderPool.get();
        if (IdleLayers.plain(this)) {
            ReferenceSet<BufferBuilder> set = this.fallbackBuffers.get(layer);
            if (set == null) {
                set = this.bons$pool().take();
                this.fallbackBuffers.put(layer, set);
            }
            set.add(bufferBuilder);
            return bufferBuilder;
        }
        this.fallbackBuffers.computeIfAbsent(layer, k -> new ReferenceLinkedOpenHashSet<>()).add(bufferBuilder);
        return bufferBuilder;
    }

    @Unique
    private IdleLayers.SetPool bons$pool() {
        IdleLayers.SetPool pool = this.bons$setPool;
        if (pool == null) {
            pool = this.bons$setPool = new IdleLayers.SetPool();
        }
        return pool;
    }
}
