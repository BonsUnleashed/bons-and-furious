package bons.furious.patch.oculus_outline;

import bons.furious.guard.Fingerprint;
import bons.furious.mixin.oculus_outline.BufferSourceAccessor;
import bons.furious.mixin.oculus_outline.OutlineSourceAccessor;
import com.mojang.blaze3d.vertex.BufferBuilder;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.service.MixinService;

/**
 * Bons and Furious switch oculus_shadow_outline_discard (Oculus 1.8.0 on Minecraft 1.20.1 / Forge 47.4.16, client only;
 * with or without ImmediatelyFast 1.5.5). Helper of bons.furious.mixin.oculus_outline.ShadowOutlineDiscardMixin.
 *
 * Fix: Oculus's shadow pass renders entities with its own RenderBuffers, which it puts into LevelRenderer.renderBuffers for
 * the duration of the pass, and while a pass is open that RenderBuffers' outlineBufferSource() answers Oculus's own
 * OutlineBufferSource. Nothing ever ends that source's outline batch (the main pass's endOutlineBatch is called on the
 * player's buffers). Armor renderers that pick their buffer themselves write a glowing wearer's outline into
 * levelRenderer.renderBuffers.outlineBufferSource(): GeckoLib 4.8.4 and AzureLib 3.0.13 GeoArmorRenderer.renderToBuffer
 * (the armor of Ars Nouveau, Mowzie's Mobs, Fantasy Armor, Goblin's Tyranny and ten more mods in this pack). In the shadow
 * pass those vertices land in the never-ended outline batch:
 *   - with ImmediatelyFast, the outline source is IF's BatchableBufferSource, which keeps one pooled BufferBuilder per
 *     outline layer and never flushes on a layer change; the builder stays "building", so IF's pool never reclaims it, and
 *     it grows by the armor's outline every shadow pass while the wearer glows (native memory, re-copied on every 2 MB
 *     growth step, never released until the shader pipeline is rebuilt);
 *   - with vanilla's immediate BufferSource, the vertices pile up until a different outline layer is requested in a later
 *     pass, and then all of them (from many frames, at old positions) are drawn with the outline render type into the
 *     shadow buffers.
 * ImmediatelyFast 1.6.9 / 1.8.5 address this upstream ("clear the Iris shadow pass outline buffer"; idea text only). The
 * intended behaviour is that shadow-pass outline vertices are neither kept nor drawn: the main pass draws the glow
 * outline from its own buffers.
 *
 * discard() runs at the end of each shadow pass (just before Oculus closes the pass) and ends the pending outline batch
 * without drawing it, i.e. exactly the state changes of ending the batch minus the draw call:
 *   - vanilla's immediate BufferSource (no fixed buffers): what endBatch(lastState) does: if the shared builder was
 *     started, remove it from the started set, end() it and release() the rendered buffer (RenderType.end ends the
 *     builder, and the upload inside its draw releases the buffer), then forget the last render type;
 *   - ImmediatelyFast 1.5.5's BatchableBufferSource: its own close(), which ends and releases every active layer's
 *     builders and clears its layer bookkeeping, as its endBatch() does after drawing; used only while IF's close() and
 *     the methods it pairs with have the recorded 1.5.5 code (checked once from the class bytes, IF_FINGERPRINTS);
 *   - any other source class or IF code: left alone (WARN once), as shipped.
 * Nothing else is touched: Oculus draws the shadow pass's entity batch before this point, and the player's buffers (main
 * pass) are not the shadow RenderBuffers.
 */
public final class ShadowOutlineDiscard {
    /** Runtime switch; -Dbons_and_furious.shadowOutlineDiscard=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.shadowOutlineDiscard", "true"));
    /**
     * Rig probe: -Dbons_and_furious.shadowOutlineDiscard.shadow=true discards nothing; it counts the shadow passes that end
     * with a pending outline batch (SHADOW_MISMATCHES, = the bug firing) out of all passes (SHADOW_CHECKS) and keeps the
     * outline builders' buffer bytes in BYTES_HELD, so the growth can be watched.
     */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.shadowOutlineDiscard.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Batches discarded, sources left alone (unknown class or IF code), outline buffer bytes seen in SHADOW mode. */
    public static final AtomicLong DISCARDED = new AtomicLong(), LEFT_ALONE = new AtomicLong(), BYTES_HELD = new AtomicLong();

    static final String IF_SOURCE = "net/raphimc/immediatelyfast/feature/core/BatchableBufferSource";
    /**
     * ImmediatelyFast 1.5.5+1.20.4's BatchableBufferSource methods whose behaviour discard() relies on (Fingerprint.of of
     * the class bytes): close() is endBatch() minus the draws only while these are as recorded.
     */
    static final String[][] IF_FINGERPRINTS = {
            {"close", "()V", "cea0f02a224b7e77cca2f0fdc01be6a966e7ce67143f7a1e087bfcfa21ae7b8d"},
            {"hasActiveLayers", "()Z", "2b858822074f292aaa3ac97eb81183dba07ee0e491326fda2b98038dd398cbd2"},
            {"m_109911_", "()V", "7f857425d40bb939540366a98fe9989bbebcdc3625bb3fe03ac53a45e410efdb"},
            {"m_109912_", "(Lnet/minecraft/client/renderer/RenderType;)V", "376d8a2b9642a66cec0edefaddafbf4b8dfc006ed28eea4f1dc4b05823b0118e"},
            {"m_173043_", "()V", "db6fa5ba84143f807390d114315fd3ce346422010a50b0a8dec5a04347cf67ae"},
            {"m_6299_", "(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;", "7016a8229d1dfdeb59d1725c2ed77683738f83405dd800ef3a03c560f870cf57"},
            {"getBufferBuilder", "(Lnet/minecraft/client/renderer/RenderType;)Ljava/util/Set;", "c16d37e954b3aeed1fc3162e10c84eb169e9acefabcca0268264266f2f2b89e8"},
    };

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced, warned;
    private static volatile Boolean ifVerified;
    private static Method ifHasActiveLayers;
    private static Field ifFallbackBuffers, builderBuffer;

    private ShadowOutlineDiscard() {
    }

    /** End of an Oculus shadow pass, while it is still open: drop the pending outline batch of its buffers. */
    public static void discard(RenderBuffers shadowBuffers) {
        OutlineBufferSource outline = shadowBuffers.m_110109_();
        MultiBufferSource.BufferSource source = ((OutlineSourceAccessor) outline).bons$outlineSource();
        if (SHADOW) {
            probe(source);
            return;
        }
        Class<?> c = source.getClass();
        if (c == MultiBufferSource.BufferSource.class) {
            if (discardVanilla(source)) counted();
        } else if (c.getName().replace('.', '/').equals(IF_SOURCE) && ifVerified(c)) {
            if (hasActiveLayers(source)) {
                try {
                    ((AutoCloseable) source).close();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                counted();
            }
        } else {
            leftAlone(c.getName());
        }
    }

    /**
     * vanilla immediate BufferSource: the state changes of endBatch(lastState) minus RenderType.end's draw. Returns whether a
     * batch was pending.
     */
    static boolean discardVanilla(MultiBufferSource.BufferSource source) {
        BufferSourceAccessor a = (BufferSourceAccessor) source;
        Optional<RenderType> last = a.bons$lastState();
        if (last.isEmpty() || !a.bons$fixedBuffers().isEmpty()) return false;
        BufferBuilder builder = a.bons$builder();
        if (a.bons$startedBuffers().remove(builder)) {
            if (builder.m_85732_()) builder.m_231175_().m_231200_();
            a.bons$setLastState(Optional.empty());
            return true;
        }
        return false;
    }

    static boolean hasActiveLayers(MultiBufferSource.BufferSource source) {
        try {
            Method m = ifHasActiveLayers;
            if (m == null || m.getDeclaringClass() != source.getClass()) ifHasActiveLayers = m = source.getClass().getMethod("hasActiveLayers");
            return (boolean) m.invoke(source);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Whether ImmediatelyFast's source class has the recorded 1.5.5 code for every method in IF_FINGERPRINTS. */
    static boolean ifVerified(Class<?> c) {
        Boolean v = ifVerified;
        if (v == null) {
            v = Boolean.FALSE;
            try {
                ClassNode node = rawNode(IF_SOURCE, c.getClassLoader());
                int ok = 0;
                for (String[] f : IF_FINGERPRINTS) {
                    for (MethodNode m : node.methods)
                        if (m.name.equals(f[0]) && m.desc.equals(f[1]) && Fingerprint.of(m).equals(f[2])) { ok++; break; }
                }
                v = ok == IF_FINGERPRINTS.length;
                if (!v) LOGGER.warn("Bons and Furious: oculus_shadow_outline_discard leaves ImmediatelyFast's outline source alone: its code is not "
                        + "the supported 1.5.5 ({} of {} methods match)", ok, IF_FINGERPRINTS.length);
            } catch (Throwable t) {
                LOGGER.warn("Bons and Furious: oculus_shadow_outline_discard leaves ImmediatelyFast's outline source alone: {}", t.toString());
            }
            ifVerified = v;
        }
        return v;
    }

    /** The class as it is in its jar (before any mixin), like the guard check reads it. */
    static ClassNode rawNode(String internalName, ClassLoader loader) throws Exception {
        try {
            return MixinService.getService().getBytecodeProvider().getClassNode(internalName.replace('/', '.'));
        } catch (Throwable noService) {   // a plain JVM (harness): the bytes on the class path
            try (InputStream in = loader.getResourceAsStream(internalName + ".class")) {
                ClassNode n = new ClassNode();
                new ClassReader(in).accept(n, 0);
                return n;
            }
        }
    }

    private static void leftAlone(String cls) {
        LEFT_ALONE.incrementAndGet();
        if (!warned) {
            warned = true;
            LOGGER.warn("Bons and Furious: oculus_shadow_outline_discard leaves the shadow outline source alone ({})", cls);
        }
    }

    private static void counted() {
        DISCARDED.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: oculus_shadow_outline_discard: an outline batch written during the shader shadow pass was discarded "
                    + "(armor renderers that pick the level renderer's outline buffer themselves)");
        }
    }

    /** SHADOW mode: count passes ending with a pending outline batch and the outline builders' buffer bytes. */
    static void probe(MultiBufferSource.BufferSource source) {
        SHADOW_CHECKS.incrementAndGet();
        boolean pending;
        long bytes = 0;
        try {
            if (source.getClass().getName().replace('.', '/').equals(IF_SOURCE)) {
                pending = hasActiveLayers(source);
                if (ifFallbackBuffers == null) {
                    Field f = source.getClass().getDeclaredField("fallbackBuffers");
                    f.setAccessible(true);
                    ifFallbackBuffers = f;
                }
                for (Object set : ((Map<?, ?>) ifFallbackBuffers.get(source)).values())
                    for (Object b : (Collection<?>) set) bytes += capacity((BufferBuilder) b);
            } else {
                BufferSourceAccessor a = (BufferSourceAccessor) source;
                pending = a.bons$lastState().isPresent() && a.bons$startedBuffers().contains(a.bons$builder());
                bytes = capacity(a.bons$builder());
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        BYTES_HELD.set(bytes);
        if (pending && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
            LOGGER.warn("Bons and Furious: oculus_shadow_outline_discard SHADOW: shadow pass ended with a pending outline batch "
                    + "({} bytes in the outline builders)", bytes);
        }
    }

    private static long capacity(BufferBuilder b) throws ReflectiveOperationException {
        if (builderBuffer == null) {
            Field f = BufferBuilder.class.getDeclaredField("f_85648_");
            f.setAccessible(true);
            builderBuffer = f;
        }
        Object buf = builderBuffer.get(b);
        return buf instanceof ByteBuffer bb ? bb.capacity() : 0;
    }
}
