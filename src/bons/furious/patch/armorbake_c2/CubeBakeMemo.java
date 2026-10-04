package bons.furious.patch.armorbake_c2;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDefinition;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.slf4j.Logger;

/**
 * Bons and Furious switch modernfix_cube_bake_memo (ModernFix 5.27.77 compact_entity_models on Minecraft 1.20.1, client).
 * No ModernFix code is carried here.
 *
 * ModernFix's compact_entity_models (on by default) wraps the cube construction in CubeDefinition.bake: it builds a
 * 15-element key (List.of with the texture offsets, the six origin/size floats, the three grow values, mirror, the two
 * texture scales times the texture size, and the visible-face set), looks it up in its never-cleared ConcurrentHashMap and
 * returns the cube it already made for an equal key. So, with ModernFix, every bake of the same CubeDefinition with the
 * same texture size returns the very same ModelPart.Cube - but the key costs 15 boxings, two arrays, a 15-element hash
 * and a 15-element compare per cube, on every bake (armour models that bake per render call: about 13 cubes per layer).
 *
 * Each CubeDefinition now remembers the cube ModernFix handed out for it, with the texture size and a snapshot of the
 * inputs that can change: the origin and size vectors (mutable JOML vectors; compared as Float.floatToIntBits, which is
 * how ModernFix's key compares them) and the visible faces (a copy of the set's contents; ModernFix's key holds the set
 * itself). The other inputs are final fields of immutable objects. While all of them are unchanged, the remembered cube is
 * the one ModernFix's map holds for the key, so it is returned without building the key. Any change, and any first bake,
 * goes through the original (and ModernFix) and is remembered again.
 *
 * Active only when ModernFix's CubeDefinitionMixin is merged into CubeDefinition and no other mod's mixin is (checked once,
 * from the @MixinMerged annotations): without ModernFix, vanilla makes a new cube on every bake, and a memo would change
 * that. ONE DOCUMENTED INTERNAL DIFFERENCE: if two threads bake equal cubes for the very first time at the same moment,
 * ModernFix makes two equal cubes and keeps the one put last; a CubeDefinition whose first bake made the other one keeps
 * handing out that one. Cubes are immutable geometry, so both render identically (model baking normally runs on the main
 * and render threads only). SHADOW MODE for rigs: -Dbons_and_furious.modernfixCubeBakeMemo.shadow=true runs the original
 * on every remembered bake, returns its cube and counts bakes where it is not the remembered object (WARN, at most 20).
 */
public final class CubeBakeMemo {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.modernfixCubeBakeMemo=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.modernfixCubeBakeMemo", "true"));
    /** Shadow mode (see the class comment). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.modernfixCubeBakeMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong();
    public static final AtomicLong SHADOW_MISMATCHES = new AtomicLong();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MERGED = "org.spongepowered.asm.mixin.transformer.meta.MixinMerged";
    private static final String MODERNFIX = "org.embeddedt.modernfix.common.mixin.perf.compact_entity_models.CubeDefinitionMixin";
    private static final String OURS = "bons.furious.mixin.armorbake_c2.CubeBakeMemoMixin";
    /** 0 = not checked, 1 = active, 2 = standing down. */
    private static volatile int state;

    private CubeBakeMemo() {
    }

    /** Implemented on CubeDefinition by the mixin: the remembered entry and the inputs that can change. */
    public interface Holder {
        Entry bons$cubeMemo();

        void bons$cubeMemo(Entry entry);

        Vector3f bons$origin();

        Vector3f bons$dimensions();

        Set<Direction> bons$visibleFaces();
    }

    /** One remembered cube with what it was made from. Final fields: published by one reference write. */
    public static final class Entry {
        final int w, h, ox, oy, oz, dx, dy, dz;
        final Set<Direction> faces;
        final ModelPart.Cube cube;

        Entry(int w, int h, Vector3f o, Vector3f d, Set<Direction> faces, ModelPart.Cube cube) {
            this.w = w;
            this.h = h;
            this.ox = Float.floatToIntBits(o.x);
            this.oy = Float.floatToIntBits(o.y);
            this.oz = Float.floatToIntBits(o.z);
            this.dx = Float.floatToIntBits(d.x);
            this.dy = Float.floatToIntBits(d.y);
            this.dz = Float.floatToIntBits(d.z);
            this.faces = faces;
            this.cube = cube;
        }

        boolean matches(int w, int h, Vector3f o, Vector3f d, Set<Direction> current) {
            return this.w == w && this.h == h && ox == Float.floatToIntBits(o.x) && oy == Float.floatToIntBits(o.y) && oz == Float.floatToIntBits(o.z)
                    && dx == Float.floatToIntBits(d.x) && dy == Float.floatToIntBits(d.y) && dz == Float.floatToIntBits(d.z) && current.equals(faces);
        }
    }

    /** The @WrapMethod body for CubeDefinition.bake(w, h). */
    public static ModelPart.Cube bake(Holder self, int w, int h, Operation<ModelPart.Cube> original) {
        if (!enabled || (state != 1 && !active())) return original.call(w, h);
        Vector3f o = self.bons$origin(), d = self.bons$dimensions();
        Set<Direction> faces = self.bons$visibleFaces();
        Entry e = self.bons$cubeMemo();
        if (e != null && o != null && d != null && faces != null && e.matches(w, h, o, d, faces)) {
            if (!SHADOW) return e.cube;
            ModelPart.Cube fresh = original.call(w, h);
            SHADOW_CHECKS.incrementAndGet();
            if (fresh != e.cube) {
                long n = SHADOW_MISMATCHES.incrementAndGet();
                if (n <= 20) LOGGER.warn("Bons and Furious: modernfix_cube_bake_memo SHADOW MISMATCH {}: ModernFix handed out another cube object for an unchanged cube definition", n);
            }
            return fresh;
        }
        ModelPart.Cube cube = original.call(w, h);
        if (o != null && d != null && faces != null && cube != null) {
            Set<Direction> snapshot;
            try {
                snapshot = faces.isEmpty() ? EnumSet.noneOf(Direction.class) : EnumSet.copyOf(faces);
            } catch (RuntimeException notEnumerable) {
                try {
                    snapshot = new java.util.HashSet<>(faces);       // a set an EnumSet cannot copy (e.g. holding null)
                } catch (RuntimeException unreadable) {
                    return cube;                                      // nothing remembered
                }
            }
            self.bons$cubeMemo(new Entry(w, h, o, d, snapshot, cube));
        }
        return cube;
    }

    /** True when ModernFix's cube dedup (and no mixin this switch was not tested with) is merged into CubeDefinition. */
    private static synchronized boolean active() {
        if (state != 0) return state == 1;
        boolean modernfix = false;
        Set<String> unknown = new TreeSet<>();
        try {
            for (Method m : CubeDefinition.class.getDeclaredMethods()) {
                for (Annotation a : m.getDeclaredAnnotations()) {
                    if (!a.annotationType().getName().equals(MERGED)) continue;
                    String mixin = String.valueOf(a.annotationType().getMethod("mixin").invoke(a));
                    if (mixin.equals(MODERNFIX)) modernfix = true;
                    else if (!mixin.equals(OURS)) unknown.add(mixin);
                }
            }
        } catch (Throwable t) {
            state = 2;
            LOGGER.warn("Bons and Furious: modernfix_cube_bake_memo stands down: could not list the mixins merged into CubeDefinition ({})", t.toString());
            return false;
        }
        if (!unknown.isEmpty()) {
            state = 2;
            LOGGER.warn("Bons and Furious: modernfix_cube_bake_memo stands down: CubeDefinition carries mixins it was not tested with ({})", String.join(", ", unknown));
            return false;
        }
        if (!modernfix) {
            state = 2;
            LOGGER.info("Bons and Furious: modernfix_cube_bake_memo stays off: ModernFix's compact_entity_models is not active, so cubes are not shared");
            return false;
        }
        state = 1;
        LOGGER.info(SHADOW ? "Bons and Furious: modernfix_cube_bake_memo SHADOW MODE: every remembered cube is checked against ModernFix's"
                : "Bons and Furious: modernfix_cube_bake_memo: cube definitions remember the cube ModernFix shares for them");
        return true;
    }
}
