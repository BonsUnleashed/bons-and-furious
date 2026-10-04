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
 * Bons and Furious switch worldgen_empty_beardifier_marker (Minecraft 1.20.1 world generation, server side; tested with
 * Forge 47.4.16 and the Beardifier mixins of Integrated API 1.5.1, YUNG's API 4.0.6, Moog's Structure Lib 3.3.1, Qliphoth
 * Awakening (fdbosses) 3.1.0.3, Lithostitched 1.4.11, Valhelsia Structures 1.1.2, L_Ender's Cataclysm 3.16, C2ME 0.2.0
 * alpha.12). SRG member names.
 *
 * What it costs. NoiseChunk caches cacheAllInCell(add(finalDensity, beardifier)) per cell. Beardifier inherits
 * SimpleFunction's fillArray, so every cell is filled by NoiseChunk.fillAllDirectly calling Beardifier.compute once per
 * block (196,608 calls in a 768-high chunk), and every mod that adds structure terrain adaptation adds its own work to
 * that call (Integrated API's handler: a callback object, a boxed double and its own two list walks). For a chunk with no
 * structure piece near it, every one of those calls returns +0.0.
 *
 * What the switch does. Right after NoiseBasedChunkGenerator.createNoiseChunk built the chunk's Beardifier (mark), and
 * only when that Beardifier provably adds nothing anywhere, it is flagged. A flagged Beardifier's fillArray (added by
 * BeardifierEmptyFillMixin) answers a NoiseChunk fill with Arrays.fill(+0.0) and leaves the NoiseChunk's in-cell position
 * and array index exactly where fillAllDirectly's loop leaves them. The object, its bounds (-inf..+inf), its compute and
 * every other mod's code stay as they are; any other caller of compute still gets the full original computation.
 *
 * "Provably adds nothing" (all must hold, else the chunk runs the original code):
 *  1. Armed once per run: every mixin that merged code into Beardifier is ours or one of VERIFIED, byte for byte (SHA-256
 *     of its class file), and so are the enhanced-adaptation helpers their compute handlers call; every instance field of
 *     Beardifier is vanilla's, ours, or an ObjectListIterator slot; and no other mod's NoiseChunk mixin mentions the fill
 *     state (fillAllDirectly, inCellX/Y/Z, arrayIndex, beardifier). Unknown code anywhere: the switch stands down (one INFO
 *     line) and nothing is flagged.
 *  2. Per chunk: the object is exactly a Beardifier (no subclass), vanilla's piece and junction lists are empty, and every
 *     ObjectListIterator slot other mods added (YUNG's/Integrated API's shared slot, Moog's slots) is null or empty.
 *     With the verified code that means: vanilla's loops add nothing; fdbosses' handler runs only inside the rigid loop;
 *     Lithostitched and Valhelsia only edit the lists; the one enhanced-adaptation compute handler that runs (the first
 *     applied: Integrated API in this pack, whose cancelling handlers also hide YUNG's and Moog's) walks its own non-null
 *     slot - set by its own forStructuresInChunk handler, which runs in the same order - and returns the density it was
 *     given. So compute returns +0.0 at every position, which is what the fill writes.
 *
 * -Dbons_and_furious.emptyBeardifierMarker=false runs the original fill (checked per fill).
 * -Dbons_and_furious.emptyBeardifierMarker.shadow=true (verification runs only) also runs the original loop for every
 * flagged fill and counts disagreements in the values or the NoiseChunk state (SHADOW_CHECKS / SHADOW_MISMATCHES).
 */
public final class EmptyBeardifiers {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.emptyBeardifierMarker", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.emptyBeardifierMarker.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Chunks whose beardifier was flagged / checked (diagnostics; the proof reads them). */
    public static final AtomicLong MARKED = new AtomicLong(), CHECKED = new AtomicLong();

    /** Other mods' Beardifier mixins whose code was read (research-worldgen.md, beardifier.md), by SHA-256 of the class file. */
    static final Map<String, String> VERIFIED = Map.ofEntries(
            Map.entry("com.craisinlord.integrated_api.mixins.structures.BeardifierMixin", "0fc710e9d13a5e9a322fef904bfae9dbbbbc1d611004cb9ad4f7d9fa55709000"),
            Map.entry("com.craisinlord.integrated_api.mixins.structures.BeardifierAccessor", "d85014fd3e7d5d0f9dafec383fe535ef3425376fc1fdb02b5a4a860b9d16b3ba"),
            Map.entry("com.yungnickyoung.minecraft.yungsapi.mixin.BeardifierMixin", "9a2dc9298662748b26ea2a5a42407ccfb70196e1d06953acabace4a73e5f505b"),
            Map.entry("com.yungnickyoung.minecraft.yungsapi.mixin.accessor.BeardifierAccessor", "aadd6b78f7ab50d1b7282f941c5e93e2a2b86ca354e15a905c002a1801e3a813"),
            Map.entry("com.finndog.moogs_structures.mixins.terrainadaptation.BeardifierMixin", "82b0df328e0b508cdeb6a0b819c6ff051826627bddaa5773d338f2373279eac2"),
            Map.entry("com.finndog.moogs_structures.mixins.terrainadaptation.BeardifierAccessor", "cf533d0a4738be877fca34ce914dcf609a1d186f7893e9347ba03281d7e1d0d4"),
            Map.entry("com.finderfeed.fdbosses.mixin.BeardifierMixin", "a73925f4e3d6877e3c825d43ca57f40fc136b2edd1dd716632008d22702d9a15"),
            Map.entry("dev.worldgen.lithostitched.mixin.common.BeardifierMixin", "e1ce84f67c9071b8d95ce09d68051a78b1e94790f8c81bcd35e84fe582e03a91"),
            Map.entry("com.stal111.valhelsia_structures.core.mixin.BeardifierMixin", "a79fb30ed4cc04863bd0acd55185eaea256ecbd12c0217563a4124b7119d05f3"),
            Map.entry("com.github.L_Ender.cataclysm.mixin.accessor.BeardifierAccessor", "b353ab3c21f00f7c4ba1f4302c5279d58c8362b63c6e34a2410f47ef0b9c889b"),
            Map.entry("com.ishland.c2me.base.mixin.access.IStructureWeightSampler", "184f5f578ed1dcc6e1d6a44b28c48fca074754b30e88583afee5ee538b4c2c1d"));

    /** The enhanced-adaptation helper each compute-handling mixin calls, with the SHA-256 of that helper's class file. */
    static final Map<String, String[]> HELPERS = Map.of(
            "com.craisinlord.integrated_api.mixins.structures.BeardifierMixin", new String[] {
                    "com.craisinlord.integrated_api.world.terrainadaptation.beardifier.EnhancedBeardifierHelper",
                    "7b08ae488ae6b964d3d48a94bed6bed8a5aea4dec4d823945c875d0511628fc8"},
            "com.yungnickyoung.minecraft.yungsapi.mixin.BeardifierMixin", new String[] {
                    "com.yungnickyoung.minecraft.yungsapi.world.structure.terrainadaptation.beardifier.EnhancedBeardifierHelper",
                    "73d01dffc67a1dbc2b579130a6464100eeea525a674a9a1dd5fa9b1feb231248"},
            "com.finndog.moogs_structures.mixins.terrainadaptation.BeardifierMixin", new String[] {
                    "com.finndog.moogs_structures.world.structures.terrainadaptation.beardifier.EnhancedBeardifierHelper",
                    "57164c706635b0377badd281fb34b096b03b0eb048b4485596f7b0039fa201e7"});

    /** Words that would mean another NoiseChunk mixin touches the fill state this switch reproduces (SRG and Mojang names). */
    private static final String[] FILL_STATE = {"m_207207_", "fillAllDirectly", "f_209153_", "inCellX", "f_209154_", "inCellY",
            "f_209155_", "inCellZ", "f_209158_", "arrayIndex", "f_209166_", "beardifier"};

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
            provider.m_207207_(reference, self);
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
                String want = VERIFIED.get(mixin);
                if (want == null) return new Census(false, "Beardifier carries code from " + mixin + ", which this switch has not verified", new MethodHandle[0]);
                String got = sha256(loader, mixin);
                if (!want.equals(got)) return new Census(false, mixin + " is not the verified build", new MethodHandle[0]);
                String[] helper = HELPERS.get(mixin);
                if (helper != null && !helper[1].equals(sha256(loader, helper[0])))
                    return new Census(false, helper[0] + " is not the verified build", new MethodHandle[0]);
                verified.add(mixin);
            }
            List<MethodHandle> slots = new ArrayList<>();
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            for (Field f : beardifier.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                String name = f.getName();
                if (name.equals("f_158065_") || name.equals("f_158066_") || name.startsWith("bons$")) continue;
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
     * injection targets, accessor names) and referenced members, but no local-variable names (BetterEnd 20.0.7's
     * NoiseChunk constructor hook names a parameter beardifierOrMarker; that is not a reference to the field).
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
