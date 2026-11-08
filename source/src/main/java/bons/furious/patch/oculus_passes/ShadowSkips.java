package bons.furious.patch.oculus_passes;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.util.concurrent.atomic.AtomicLong;
import net.irisshaders.iris.api.v0.IrisApi;
import org.slf4j.Logger;

/**
 * The decision of the four shadow-pass trade-off switches of Bons and Furious 1.0.49 on Minecraft 1.21.1 / NeoForge
 * (client; Iris 1.8.12+mc1.21.1 for NeoForge with Sodium 0.6.13, the target of the Oculus-prefixed switches on this
 * Minecraft version), one Site per switch: oculus_shadow_skip_banner_patterns (BannerPatternShadowMixin),
 * oculus_shadow_skip_name_tags (NameTagShadowMixin), oculus_shadow_skip_sign_text (SignTextShadowMixin),
 * oculus_shadow_skip_item_glint (ItemGlintShadowMixin). Each is a `change` switch, off by default: its mixins apply only
 * while the switch is on in bons_and_furious.properties; -Dbons_and_furious.<camelName>=false is the emergency off at run
 * time.
 *
 * skip() is true exactly while Iris renders its shadow map: shadowPass() is Iris's public API
 * IrisApi.getInstance().isRenderingShadowPass() (IrisApiInternal.INSTANCE, the IrisApiV0Impl.INSTANCE it looks up once),
 * which is ShadowRenderingState.areShadowsCurrentlyBeingRendered() = ShadowRenderer.ACTIVE, set at the start and cleared at
 * the end of ShadowRenderer.renderShadows. Reading it has no side effect (ShadowRenderer's static initialiser only sets
 * ACTIVE = false). Outside the shadow pass, and without Iris, it is false and the game draws exactly as before. Every Iris
 * reference sits in the nested class Api, which the JVM loads only when Iris is installed (the outer class's method
 * signatures hold no Iris type).
 *
 * Shadow (verification) mode, -Dbons_and_furious.<camelName>.shadow=true: nothing is skipped; every decision counts in
 * shadowChecks (the ones that would skip also in skippable), and each decision is compared with an independent test, "is
 * this call inside ShadowRenderer.renderShadows" (a stack walk): a difference counts in shadowMismatches, the first 20 get a
 * WARN. A test run with a shader pack expects skippable > 0 while shadows render and shadowMismatches == 0.
 *
 * oculus_shadow_skip_item_glint (ItemGlintShadowMixin) asks skipOnRenderThread(): ItemStack.hasFoil is also called by other
 * threads (the integrated server's), and ShadowRenderer.ACTIVE is one static flag, so only the render thread's calls count
 * as inside the shadow pass.
 *
 * oculus_shadow_skip_sign_text asks skipSignText(pose): ImmediatelyFast 1.6.14's experimental sign text cache (off by
 * default) fills its atlas by calling SignRenderer.renderSignText again with its own pose stack class
 * (NoSetTextAnglesMatrixStack); that call draws into IF's atlas, not into the shadow map, and leaving it out would cache an
 * empty slot that the normal view then shows, so a call with that pose stack is never skipped.
 */
public final class ShadowSkips {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean IRIS = present();
    /** ImmediatelyFast's pose stack class for the sign text atlas (compared by name; IF need not be installed). */
    private static final String IF_SIGN_ATLAS_POSE = "net.raphimc.immediatelyfast.feature.sign_text_buffering.NoSetTextAnglesMatrixStack";

    /** One per switch: its run-time flag, shadow flag and counters. */
    public static final class Site {
        public final String key;
        public volatile boolean enabled;
        public final boolean shadow;
        public final AtomicLong shadowChecks = new AtomicLong(), shadowMismatches = new AtomicLong(), skippable = new AtomicLong();
        private final String what;
        private volatile boolean announced;

        Site(String key, String camel, String what) {
            this.key = key;
            this.what = what;
            this.enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + camel, "true"));
            this.shadow = Boolean.getBoolean("bons_and_furious." + camel + ".shadow");
        }

        /** True when the caller leaves its drawing out: the switch is on, Iris renders its shadow map, no shadow mode. */
        public boolean skip() {
            if (!enabled) return false;
            return decide(shadowPass());
        }

        /** As skip(), for callers on any thread: only the render thread's calls can be inside the shadow pass. */
        public boolean skipOnRenderThread() {
            if (!enabled) return false;
            return decide(shadowPass() && RenderSystem.isOnRenderThread());
        }

        private boolean decide(boolean shadowPass) {
            if (shadow) {
                verify(shadowPass);
                return false;
            }
            if (shadowPass && !announced) {
                announced = true;
                LOGGER.info("Bons and Furious: {} applies ({})", key, what);
            }
            return shadowPass;
        }

        private void verify(boolean shadowPass) {
            shadowChecks.incrementAndGet();
            if (shadowPass) skippable.incrementAndGet();
            boolean inside = stackInShadowRenderer();
            if (inside != shadowPass) {
                long m = shadowMismatches.incrementAndGet();
                if (m <= 20)
                    LOGGER.warn("Bons and Furious: {} shadow mismatch #{}: Iris reports shadow pass {} but the call is {} ShadowRenderer.renderShadows",
                            key, m, shadowPass, inside ? "inside" : "outside");
            }
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: {} applies in shadow mode (counting only, nothing is skipped)", key);
            }
        }
    }

    public static final Site BANNER_PATTERNS = new Site("oculus_shadow_skip_banner_patterns", "oculusShadowSkipBannerPatterns",
            "banner and shield pattern layers are left out of the shadow map; the base cloth still casts it");
    public static final Site NAME_TAGS = new Site("oculus_shadow_skip_name_tags", "oculusShadowSkipNameTags",
            "name tags are left out of the shadow map");
    public static final Site SIGN_TEXT = new Site("oculus_shadow_skip_sign_text", "oculusShadowSkipSignText",
            "sign text is left out of the shadow map");
    public static final Site ITEM_GLINT = new Site("oculus_shadow_skip_item_glint", "oculusShadowSkipItemGlint",
            "the enchantment glint of items and armour is left out of the shadow map; the items themselves still cast it");

    private ShadowSkips() {
    }

    private static boolean present() {
        try {
            Class.forName("net.irisshaders.iris.api.v0.IrisApi", false, ShadowSkips.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** True while Iris renders its shadow map. False without Iris. */
    public static boolean shadowPass() {
        return IRIS && Api.shadowPass();
    }

    /** Verification (shadow modes) only: whether the calling thread is inside Iris's ShadowRenderer.renderShadows. */
    static boolean stackInShadowRenderer() {
        return StackWalker.getInstance().walk(frames -> frames.anyMatch(f ->
                f.getClassName().equals("net.irisshaders.iris.shadows.ShadowRenderer") && f.getMethodName().equals("renderShadows")));
    }

    /**
     * BannerRenderer.renderPatterns: the pattern list's size() in the loop condition; 0 ends the loop before the first
     * pattern layer.
     */
    public static int patternCount(int size) {
        return BANNER_PATTERNS.skip() ? 0 : size;
    }

    /** BannerRenderer.renderPatterns: whether the dyed base layer (the first renderPatternLayer call) is drawn. */
    public static boolean drawBaseLayer() {
        return !BANNER_PATTERNS.skip();
    }

    /** SignRenderer.renderSignText: whether this call leaves the sign's text out (never for ImmediatelyFast's atlas fill). */
    public static boolean skipSignText(Object pose) {
        Site s = SIGN_TEXT;
        if (!s.enabled || pose != null && IF_SIGN_ATLAS_POSE.equals(pose.getClass().getName())) return false;
        return s.skip();
    }

    /** Iris's public API, loaded only when Iris is installed. */
    static final class Api {
        private Api() {
        }

        static boolean shadowPass() {
            return IrisApi.getInstance().isRenderingShadowPass();
        }
    }
}
