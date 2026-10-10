package bons.furious.patch.beardifier;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

/**
 * Bons and Furious switch worldgen_empty_beardifier_marker (Minecraft 1.21.1 world generation, server side; tested
 * build NeoForge 21.1.252; other mods' Beardifier code read for this port: the IStructureWeightSampler accessor of
 * C2ME 0.4.0-alpha.0.122+1.21.1 and, since 1.0.36, the Beardifier mixins of Integrated API 1.8.2 for NeoForge 1.21.1,
 * also next to Integrated Patches 1.2.0, which patches none of these classes). Mojang member names.
 *
 * What it costs. NoiseChunk caches cacheAllInCell(add(finalDensity, beardifier)) per cell. Beardifier inherits
 * SimpleFunction's fillArray, so every cell is filled by NoiseChunk.fillAllDirectly calling Beardifier.compute once per
 * block (196,608 calls in a 768-high chunk), and every mod that adds structure terrain adaptation adds its own work to
 * that call. For a chunk with no structure piece near it, every one of those calls returns +0.0.
 *
 * What the switch does. Right after NoiseBasedChunkGenerator.createNoiseChunk built the chunk's Beardifier (mark), and
 * only when that Beardifier provably adds nothing anywhere, it is flagged. A flagged Beardifier's fillArray (added by
 * BeardifierEmptyFillMixin) answers a NoiseChunk fill with Arrays.fill(+0.0) and leaves the NoiseChunk's in-cell position
 * and array index exactly where fillAllDirectly's loop leaves them. The object, its bounds (-inf..+inf), its compute and
 * every other mod's code stay as they are; any other caller of compute still gets the full original computation.
 *
 * "Provably adds nothing" (all must hold, else the chunk runs the original code):
 *  1. Armed once per run: every mixin that merged code into Beardifier is ours or one of VERIFIED, byte for byte (SHA-256
 *     of its class file), and so are the enhanced-adaptation helpers their compute handlers call (HELPERS); every
 *     instance field of Beardifier is vanilla's, ours, or an ObjectListIterator slot; and no other mod's NoiseChunk mixin
 *     mentions the fill state (fillAllDirectly, inCellX/Y/Z, arrayIndex, beardifier). Unknown code anywhere: the switch
 *     stands down (one INFO line) and nothing is flagged.
 *  2. Per chunk: the object is exactly a Beardifier (no subclass), vanilla's piece and junction lists are empty, and every
 *     ObjectListIterator slot other mods added is null or empty. With the verified code that means vanilla's loops add
 *     nothing (the rigid loop, including the 1.21 ENCAPSULATE case, and the junction loop never run), so compute returns
 *     +0.0 at every position, which is what the fill writes.
 *
 * -Dbons_and_furious.emptyBeardifierMarker=false runs the original fill (checked per fill).
 * -Dbons_and_furious.emptyBeardifierMarker.shadow=true (verification runs only) also runs the original loop for every
 * flagged fill and counts disagreements in the values or the NoiseChunk state (SHADOW_CHECKS / SHADOW_MISMATCHES).
 *
 * Ported to 1.21.1: Beardifier.compute gained TerrainAdjustment.ENCAPSULATE (inside the rigid loop, so an empty piece
 * list still adds +0.0); createNoiseChunk, SimpleFunction.fillArray, NoiseChunk.fillAllDirectly and the fill-state
 * fields are unchanged. VERIFIED / HELPERS list only builds read for 1.21.1: the 1.20.1 Forge builds the original
 * verified (Integrated API, YUNG's API, Moog's, fdbosses, Lithostitched, Valhelsia, Cataclysm) cannot load on NeoForge
 * 1.21.1, so any 1.21.1 build of them stands the switch down until it is read. Since 1.0.36 Integrated API 1.8.2's
 * NeoForge 1.21.1 build is read and verified (see VERIFIED), with the 1.0.36 list form (a mixin may have several verified
 * builds; HELPERS keyed by the mixin build). C2ME 0.4's own Beardifier rewrite
 * (MixinStructureWeightSampler) and its NoiseChunk accessor (IChunkNoiseSampler names inCellX/Y/Z and beardifier) are
 * not verified, so with C2ME installed the census stands down and every chunk keeps the original fill.
 */
public final class EmptyBeardifiers {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.emptyBeardifierMarker", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.emptyBeardifierMarker.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Chunks whose beardifier was flagged / checked (diagnostics; the proof reads them). */
    public static final AtomicLong MARKED = new AtomicLong(), CHECKED = new AtomicLong();

    /**
     * Other mods' Beardifier mixins whose code was read for 1.21.1, by SHA-256 of the class file; since 1.0.36 a mixin may
     * have several verified builds, listed in release order. C2ME's base accessor is three @Accessor getters (pieceIterator
     * twice, the static BEARD_KERNEL) and nothing else: it changes no behaviour. Integrated API 1.8.2 for NeoForge 1.21.1
     * (integrated_api-neoforge-1.21.1-1.8.2.jar, Modrinth sha1 e0a0e442..., read 2026-10-09; the shape the Forge line
     * verified on its Forge 1.20.1 build): BeardifierMixin (priority 100) adds two ObjectListIterator slots initialised to
     * empty iterators and two RETURN handlers - forStructuresInChunk returns a new Beardifier (exactly that class) built
     * from the original's two iterators plus its own two lists; compute adds
     * EnhancedBeardifierHelper.computeDensity(ctx, density, this), which reads only ctx and its own two slots and returns
     * density unchanged when both are empty. BeardifierAccessor is two @Accessor getters (pieceIterator, junctionIterator).
     * No NoiseChunk mixin.
     */
    static final Map<String, List<String>> VERIFIED = Map.ofEntries(
            Map.entry("com.craisinlord.integrated_api.mixins.structures.BeardifierMixin", List.of(
                    "5c258c353f5b13a240728e903454dc1f7a35007bbb8eadb3dbae9991548e539e")),     // Integrated API 1.8.2, NeoForge 1.21.1
            Map.entry("com.craisinlord.integrated_api.mixins.structures.BeardifierAccessor", List.of(
                    "49f8f3e1dce84dd8aa9a30a380f7a57cd27e79d0627cb2226fba03ca1b2786a3")),     // Integrated API 1.8.2, NeoForge 1.21.1
            Map.entry("com.ishland.c2me.base.mixin.access.IStructureWeightSampler", List.of(
                    "b0b081204c2c461914ba6c9baff75ebca32621742a261a0f1c42e680305ea622")));   // C2ME 0.4.0-alpha.0.122+1.21.1

    /**
     * The enhanced-adaptation helper each compute-handling mixin build calls: that mixin build's SHA-256 -> the helper class
     * and the SHA-256 of the helper's class file from the same release (since 1.0.36 keyed by build, so a mixin build is
     * only accepted with its own release's helper).
     */
    static final Map<String, String[]> HELPERS = Map.of(
            "5c258c353f5b13a240728e903454dc1f7a35007bbb8eadb3dbae9991548e539e", new String[] {        // Integrated API 1.8.2, NeoForge 1.21.1
                    "com.craisinlord.integrated_api.world.terrainadaptation.beardifier.EnhancedBeardifierHelper",
                    "212ae482ea9767d610ae1e9713a5e61e9d488177cf0feb55f4907568a749c300"});

    /** Words that would mean another NoiseChunk mixin touches the fill state this switch reproduces (Mojang names). */
    private static final String[] FILL_STATE = {"fillAllDirectly", "inCellX", "inCellY", "inCellZ", "arrayIndex", "beardifier"};

    /** The census result: armed or not, why, and the getters of the iterator slots other mods added to Beardifier. */
    public record Census(boolean armed, String detail, MethodHandle[] slots) {}

    private static volatile Census census;
    private static volatile boolean announcedMark;

    private EmptyBeardifiers() {
    }

    /** NoiseBasedChunkGenerator.createNoiseChunk: called with the Beardifier it just built; returns it unchanged. */
    public static Beardifier mark(Beardifier beardifier) {
        if (!enabled || beardifier == null || beardifier.getClass() != Beardifier.class) return beardifier;
        Census c = census();
        if (!c.armed()) return beardifier;
        CHECKED.incrementAndGet();
        if (addsNothing(beardifier, c)) {
            ((EmptyFillBeardifier) (Object) beardifier).bons$markEmptyFill();
            MARKED.incrementAndGet();
            if (!announcedMark) {
                announcedMark = true;
                LOGGER.info("Bons and Furious: worldgen_empty_beardifier_marker flagged its first chunk without nearby structure pieces (the empty beardifier is filled without a per-block loop){}",
                        SHADOW ? " - shadow verification on" : "");
            }
        }
        return beardifier;
    }

    /** True when vanilla's lists are empty and every slot another mod added is null or empty. Public for the proof. */
    public static boolean addsNothing(Beardifier beardifier, Census c) {
        EmptyFillBeardifier e = (EmptyFillBeardifier) (Object) beardifier;
        if (!emptyList(e.bons$pieceIterator()) || !emptyList(e.bons$junctionIterator())) return false;
        try {
            for (MethodHandle slot : c.slots()) {
                Object it = slot.invokeExact((Object) beardifier);
                if (it != null && !emptyList((ObjectListIterator<?>) it)) return false;
            }
        } catch (Throwable t) {
            return false;
        }
        return true;
    }

    /** An iterator over an empty list: no element in either direction (vanilla's are fresh, so index 0). */
    private static boolean emptyList(ObjectListIterator<?> it) {
        return it != null && !it.hasNext() && !it.hasPrevious();
    }

    /**
     * fillArray of a flagged Beardifier. Answers the fill when the provider is exactly a NoiseChunk filling one of its
     * own cells (the array has the cell's size): every element +0.0, then the in-cell position and array index
     * fillAllDirectly's loop ends with (inCellY 0, inCellX and inCellZ cellWidth-1, arrayIndex = cell size). False =
     * not answered, the caller runs the original fill.
     */
    public static boolean fill(double[] values, DensityFunction.ContextProvider provider, DensityFunction self) {
        if (!enabled || provider == null || provider.getClass() != NoiseChunk.class) return false;
        NoiseChunkFillState chunk = (NoiseChunkFillState) provider;
        int w = chunk.bons$cellWidth(), h = chunk.bons$cellHeight();
        long size = (long) w * w * h;
        if (w <= 0 || h <= 0 || size != values.length) return false;
        int n = (int) size;
        if (SHADOW) {
            double[] reference = new double[n];
            provider.fillAllDirectly(reference, self);
            boolean same = chunk.bons$inCellY() == 0 && chunk.bons$inCellX() == w - 1 && chunk.bons$inCellZ() == w - 1 && chunk.bons$arrayIndex() == n;
            for (int i = 0; i < n && same; i++) same = Double.doubleToRawLongBits(reference[i]) == 0L;
            SHADOW_CHECKS.incrementAndGet();
            if (!same && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                LOGGER.warn("Bons and Furious: worldgen_empty_beardifier_marker shadow mismatch (a flagged beardifier's original fill was not all +0.0 or left another state)");
        }
        Arrays.fill(values, 0.0);
        chunk.bons$setInCellY(0);
        chunk.bons$setInCellX(w - 1);
        chunk.bons$setInCellZ(w - 1);
        chunk.bons$setArrayIndex(n);
        return true;
    }

    /** The census for this run (computed once). */
    public static Census census() {
        Census c = census;
        if (c == null) {
            synchronized (EmptyBeardifiers.class) {
                c = census;
                if (c == null) {
                    c = take(Beardifier.class, NoiseChunk.class, Beardifier.class.getClassLoader());
                    census = c;
                    if (c.armed()) LOGGER.info("Bons and Furious: worldgen_empty_beardifier_marker applies ({})", c.detail());
                    else LOGGER.info("Bons and Furious: worldgen_empty_beardifier_marker stands down: {}; every chunk keeps the original beardifier fill", c.detail());
                }
            }
        }
        return c;
    }

    /** Reads which mixins merged code into the given classes and checks them; public for the offline proof. */
    public static Census take(Class<?> beardifier, Class<?> noiseChunk, ClassLoader loader) {
        try {
            TreeSet<String> verified = new TreeSet<>();
            for (String mixin : mergedMixins(beardifier)) {
                if (mixin.startsWith("bons.furious.mixin.")) continue;
                List<String> want = VERIFIED.get(mixin);
                if (want == null) return new Census(false, "Beardifier carries code from " + mixin + ", which this switch has not verified", new MethodHandle[0]);
                String got = sha256(loader, mixin);
                if (!want.contains(got)) return new Census(false, mixin + " is not the verified build", new MethodHandle[0]);
                String[] helper = HELPERS.get(got);
                if (helper != null && !helper[1].equals(sha256(loader, helper[0])))
                    return new Census(false, helper[0] + " is not the verified build", new MethodHandle[0]);
                verified.add(mixin);
            }
            List<MethodHandle> slots = new ArrayList<>();
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            for (Field f : beardifier.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                String name = f.getName();
                if (name.equals("pieceIterator") || name.equals("junctionIterator") || name.startsWith("bons$")) continue;
                if (!ObjectListIterator.class.isAssignableFrom(f.getType()))
                    return new Census(false, "Beardifier has a field " + name + " of type " + f.getType().getName() + " this switch does not know", new MethodHandle[0]);
                f.setAccessible(true);
                slots.add(lookup.unreflectGetter(f).asType(MethodType.methodType(Object.class, Object.class)));
            }
            for (String mixin : mergedMixins(noiseChunk)) {
                if (mixin.startsWith("bons.furious.mixin.")) continue;
                byte[] bytes = classBytes(loader, mixin);
                if (bytes == null) return new Census(false, "the class file of NoiseChunk mixin " + mixin + " could not be read", new MethodHandle[0]);
                String text = withoutDebugInfo(bytes);
                for (String word : FILL_STATE)
                    if (text.contains(word)) return new Census(false, "NoiseChunk mixin " + mixin + " touches " + word, new MethodHandle[0]);
            }
            return new Census(true, (verified.isEmpty() ? "no other mod changes Beardifier" : verified.size() + " verified Beardifier mixins: "
                    + String.join(", ", verified)) + "; " + slots.size() + " adaptation slots checked per chunk", slots.toArray(new MethodHandle[0]));
        } catch (Throwable t) {
            return new Census(false, "the check failed (" + t + ")", new MethodHandle[0]);
        }
    }

    /** The mixins whose members Mixin merged into the class, from the @MixinMerged annotations it leaves behind. */
    static TreeSet<String> mergedMixins(Class<?> type) {
        TreeSet<String> out = new TreeSet<>();
        for (Method m : type.getDeclaredMethods()) {
            MixinMerged merged = m.getAnnotation(MixinMerged.class);
            if (merged != null) out.add(merged.mixin());
        }
        return out;
    }

    /**
     * The class file rewritten without debug information, as text: member names and descriptors, annotation values (the
     * injection targets, accessor names) and referenced members, but no local-variable names (a parameter merely named
     * like a field is not a reference to it).
     */
    static String withoutDebugInfo(byte[] bytes) {
        org.objectweb.asm.ClassReader reader = new org.objectweb.asm.ClassReader(bytes);
        org.objectweb.asm.ClassWriter writer = new org.objectweb.asm.ClassWriter(0);
        reader.accept(writer, org.objectweb.asm.ClassReader.SKIP_DEBUG);
        return new String(writer.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    private static byte[] classBytes(ClassLoader loader, String className) throws Exception {
        String resource = className.replace('.', '/') + ".class";
        ClassLoader l = loader != null ? loader : ClassLoader.getSystemClassLoader();
        try (InputStream in = l.getResourceAsStream(resource)) {
            return in == null ? null : in.readAllBytes();
        }
    }

    private static String sha256(ClassLoader loader, String className) throws Exception {
        byte[] bytes = classBytes(loader, className);
        if (bytes == null) return "unreadable";
        byte[] d = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : d) sb.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        return sb.toString();
    }
}
