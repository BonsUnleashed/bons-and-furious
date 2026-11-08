package bons.furious.patch.pickupnotifier_c49;

import com.mojang.blaze3d.systems.RenderSystem;
import fuzs.pickupnotifier.client.util.TransparencyBuffer;
import net.minecraft.client.gui.GuiGraphics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch pickupnotifier_opaque_sprite_direct (Pick Up Notifier 21.1.1 for Minecraft 1.21.1 / NeoForge,
 * MPL-2.0; client side; kind change, off by default). Ours; no Pick Up Notifier code is carried.
 *
 * What Pick Up Notifier does. Each visible pick-up entry draws its sprite (ItemDisplayEntry: the item and its count;
 * ExperienceDisplayEntry: the tinted experience orb) through TransparencyBuffer every frame: prepareExtraFramebuffer clears
 * a window-sized colour + depth render target and binds it, the sprite is drawn into it (GuiGraphics.renderItem and the
 * count text flush at once, the orb blit draws at once), preInject enables blending with the entry's fade as shader alpha,
 * and drawExtraFramebuffer binds the previous framebuffer and blits the whole window from the target (8.3 million fragments
 * at 4K) under an inverse-scale pose; postInject restores the shader colour. The buffer exists so a fading entry fades as a
 * whole.
 *
 * What the switch does (trade-off). For an entry that is not fading (fade 1.0: its sprite is drawn at full opacity), the
 * clear-and-bind is replaced by enabling blending, so the sprite is drawn straight into the current framebuffer with the
 * same pose, and the full-window blit is skipped; every other call is unchanged. A fading entry (fade below 1.0) keeps the
 * buffer. Opaque texels (most items) and the experience orb give the same pixels. What the player can notice: translucent
 * texels of a pick-up sprite (stained glass, ice, honey, slime) look as they do in the inventory instead of darker (the
 * buffer applies their alpha twice), and a sprite that overlaps another HUD element can be layered differently (it no
 * longer has a depth buffer of its own).
 *
 * -Dbons_and_furious.pickupnotifierOpaqueSpriteDirect=false switches it off at run time.
 */
public final class SpriteDirect {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.pickupnotifierOpaqueSpriteDirect", "true"));
    /** The current renderSprite call draws straight into the framebuffer (render thread only; set at each call's head). */
    private static boolean direct;
    /** Sprites drawn directly / through the buffer (render thread; for in-game checks). */
    public static long directSprites, bufferedSprites;
    private static boolean announced;

    private SpriteDirect() {}

    /** Head of renderSprite: decide for this call. */
    public static void begin(float fadeTime) {
        direct = enabled && fadeTime >= 1.0f;
        if (direct) directSprites++;
        else bufferedSprites++;
    }

    /** In place of TransparencyBuffer.prepareExtraFramebuffer(). */
    public static void prepare() {
        if (!direct) {
            TransparencyBuffer.prepareExtraFramebuffer();
            return;
        }
        RenderSystem.enableBlend();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: pickupnotifier_opaque_sprite_direct applies (pick-up sprites at full opacity are drawn without the window-sized buffer)");
        }
    }

    /** In place of TransparencyBuffer.drawExtraFramebuffer(guiGraphics). */
    public static void draw(GuiGraphics guiGraphics) {
        if (!direct) TransparencyBuffer.drawExtraFramebuffer(guiGraphics);
    }
}
