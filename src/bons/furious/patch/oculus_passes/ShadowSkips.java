package bons.furious.patch.oculus_passes;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The decision of the four shadow-pass trade-off switches of Bons and Furious 1.0.49 (client, Oculus 1.8.0 tested), one
 * Site per switch: oculus_shadow_skip_banner_patterns (BannerPatternShadowMixin), oculus_shadow_skip_name_tags
 * (NameTagShadowMixin), oculus_shadow_skip_sign_text (SignTextShadowMixin), oculus_shadow_skip_item_glint
 * (ItemGlintShadowMixin). Each is a `change` switch, off by default: its
 * mixins apply only while the switch is on in bons_and_furious.properties; -Dbons_and_furious.<camelName>=false is the
 * emergency off at run time.
 *
 * skip() is true exactly while Oculus renders its shadow map (IrisPasses.shadowPass: ShadowRenderer.ACTIVE through
 * Oculus's public API IrisApi.isRenderingShadowPass). Outside the shadow pass, and without Oculus, it is false and the game
 * draws exactly as before.
 *
 * Shadow (verification) mode, -Dbons_and_furious.<camelName>.shadow=true: nothing is skipped; every decision counts in
 * shadowChecks (the ones that would skip also in skippable), and each decision is compared with an independent test, "is
 * this call inside ShadowRenderer.renderShadows" (a stack walk): a difference counts in shadowMismatches, the first 20 get a
 * WARN. A test run with a shader pack expects skippable > 0 while shadows render and shadowMismatches == 0.
 *
 * oculus_shadow_skip_item_glint (ItemGlintShadowMixin) asks skipOnRenderThread(): ItemStack.hasFoil is also called by other
 * threads (the integrated server's), and ShadowRenderer.ACTIVE is one static flag, so only the render thread's calls count
 * as inside the shadow pass.
 */
public final class ShadowSkips {
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

        /** True when the caller leaves its drawing out: the switch is on, Oculus renders its shadow map, no shadow mode. */
        public boolean skip() {
            if (!enabled) return false;
            return decide(IrisPasses.shadowPass());
        }

        /** As skip(), for callers on any thread: only the render thread's calls can be inside the shadow pass. */
        public boolean skipOnRenderThread() {
            if (!enabled) return false;
            return decide(IrisPasses.shadowPass() && RenderSystem.isOnRenderThread());
        }

        private boolean decide(boolean shadowPass) {
            if (shadow) {
                verify(shadowPass);
                return false;
            }
            if (shadowPass && !announced) {
                announced = true;
                IrisPasses.LOGGER.info("Bons and Furious: {} applies ({})", key, what);
            }
            return shadowPass;
        }

        private void verify(boolean shadowPass) {
            shadowChecks.incrementAndGet();
            if (shadowPass) skippable.incrementAndGet();
            boolean inside = IrisPasses.stackInShadowRenderer();
            if (inside != shadowPass) {
                long m = shadowMismatches.incrementAndGet();
                if (m <= 20)
                    IrisPasses.LOGGER.warn("Bons and Furious: {} shadow mismatch #{}: Oculus reports shadow pass {} but the call is {} ShadowRenderer.renderShadows",
                            key, m, shadowPass, inside ? "inside" : "outside");
            }
            if (!announced) {
                announced = true;
                IrisPasses.LOGGER.info("Bons and Furious: {} applies in shadow mode (counting only, nothing is skipped)", key);
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

    /** BannerRenderer.renderPatterns (m_112074_): the pattern list's size() in the loop condition; 0 ends the loop at once. */
    public static int patternCount(int size) {
        return BANNER_PATTERNS.skip() ? 0 : size;
    }
}
