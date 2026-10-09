package bons.furious.patch.oculus_entity_vertex;

import com.mojang.blaze3d.vertex.BufferVertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch oculus_entity_vertex_direct (Oculus 1.8.0 + Embeddium 0.3.31 on Minecraft 1.20.1 / Forge
 * 47.4.16, client only). Helper of bons.furious.mixin.oculus_entity_vertex.EntityVertexDirectMixin: the runtime flag, the
 * SHADOW probe for the qualification rig, and the once-only log lines.
 *
 * The mixin writes the 14-value vertex call of a plain BufferBuilder in Oculus's ENTITY format element by element itself
 * (same values, same offsets, same nextElement()/endVertex() calls) instead of through VertexConsumer's default method.
 */
public final class EntityVertexDirect {
    /** Runtime switch; -Dbons_and_furious.oculusEntityVertexDirect=false also turns it off (the original call then runs). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.oculusEntityVertexDirect", "true"));
    /**
     * Rig probe: -Dbons_and_furious.oculusEntityVertexDirect.shadow=true runs the ORIGINAL path for every vertex the switch
     * would write itself and compares the bytes it wrote (position, colour, UV0, overlay, light; the normal too unless the
     * mode is QUADS, where Oculus's fillExtendedData replaces it at the quad's fourth vertex) with the bytes the direct path
     * writes. SHADOW_CHECKS counts compared vertices, SHADOW_MISMATCHES differing ones (WARN for the first 20).
     */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.oculusEntityVertexDirect.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Vertices whose cursor left the entity layout before the last element (they continued on the original calls). */
    public static final AtomicLong OFF_LAYOUT = new AtomicLong();
    /** Plain static: read on every call; set once by announce(). */
    public static boolean announced;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean warned;
    private static final ThreadLocal<byte[]> EXPECTED = ThreadLocal.withInitial(() -> new byte[35]);

    private EntityVertexDirect() {
    }

    public static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: oculus_entity_vertex_direct: Oculus entity-format vertices are written directly "
                + "(same bytes, same endVertex hooks)");
    }

    /**
     * A vertex whose cursor was not at step STEP's expected element (an earlier vertex was abandoned and Embeddium's element
     * table cursor is stale): continue with exactly the calls VertexConsumer's default vertex(14 values) makes from that step
     * on, as interface calls on the builder (what the original does), so everything from here is the original path.
     */
    public static void continueFrom(com.mojang.blaze3d.vertex.VertexConsumer c, int step, float r, float g, float b, float a, float u, float v,
                                    int overlay, int light, float nx, float ny, float nz) {
        offLayout(step);
        if (step <= 1) c.m_85950_(r, g, b, a);
        if (step <= 2) c.m_7421_(u, v);
        if (step <= 3) c.m_86008_(overlay);
        if (step <= 4) c.m_85969_(light);
        c.m_5601_(nx, ny, nz);
        c.m_5752_();
    }

    static void offLayout(int step) {
        OFF_LAYOUT.incrementAndGet();
        if (!warned) {
            warned = true;
            LOGGER.warn("Bons and Furious: oculus_entity_vertex_direct: a vertex's element cursor left the entity layout at step {} "
                    + "(an earlier vertex was abandoned); it continued with the original element calls", step);
        }
    }

    /**
     * The 35 bytes the direct path writes for one vertex (offsets 0-34 from the vertex start; byte 35, the padding, is not
     * written by either path), computed exactly as the mixin computes them.
     */
    public static void expected(byte[] out, java.nio.ByteOrder order, float x, float y, float z, float r, float g, float b, float a,
                                float u, float v, int overlay, int light, float nx, float ny, float nz) {
        ByteBuffer bb = ByteBuffer.wrap(out).order(order);
        bb.putFloat(0, (float) (double) x);
        bb.putFloat(4, (float) (double) y);
        bb.putFloat(8, (float) (double) z);
        bb.put(12, (byte) (int) (r * 255.0F));
        bb.put(13, (byte) (int) (g * 255.0F));
        bb.put(14, (byte) (int) (b * 255.0F));
        bb.put(15, (byte) (int) (a * 255.0F));
        bb.putFloat(16, u);
        bb.putFloat(20, v);
        bb.putShort(24, (short) (overlay & 65535));
        bb.putShort(26, (short) (overlay >> 16 & 65535));
        bb.putShort(28, (short) (light & 65535));
        bb.putShort(30, (short) (light >> 16 & 65535));
        bb.put(32, BufferVertexConsumer.m_85774_(nx));
        bb.put(33, BufferVertexConsumer.m_85774_(ny));
        bb.put(34, BufferVertexConsumer.m_85774_(nz));
    }

    /** SHADOW mode, after the original call: compare what it wrote at BASE with what the direct path writes. */
    public static void shadowCompare(ByteBuffer buffer, int base, VertexFormat.Mode mode, float x, float y, float z, float r, float g,
                                     float b, float a, float u, float v, int overlay, int light, float nx, float ny, float nz) {
        byte[] exp = EXPECTED.get();
        expected(exp, buffer.order(), x, y, z, r, g, b, a, u, v, overlay, light, nx, ny, nz);
        int n = mode == VertexFormat.Mode.QUADS ? 32 : 35;
        SHADOW_CHECKS.incrementAndGet();
        for (int i = 0; i < n; i++) {
            if (buffer.get(base + i) != exp[i]) {
                if (SHADOW_MISMATCHES.incrementAndGet() <= 20) {
                    LOGGER.warn("Bons and Furious: oculus_entity_vertex_direct SHADOW: byte {} of a vertex differs (original {}, direct {}); "
                            + "mode {}, values {} {} {} | {} {} {} {} | {} {} | {} {} | {} {} {}", i, buffer.get(base + i), exp[i], mode,
                            x, y, z, r, g, b, a, u, v, overlay, light, nx, ny, nz);
                }
                return;
            }
        }
    }
}
