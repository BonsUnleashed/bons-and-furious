package bons.furious.patch.fancymenu;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch fancymenu_render_thread_state (FancyMenu; 1.21.1 tested build: FancyMenu 3.9.14 for NeoForge
 * 1.21.1; custom licence - none of its code is carried; client).
 *
 * FancyMenu tracks its own copy of the render transform: its PoseStack mixin publishes the current scale, translation
 * and rotation through three static ThreadLocals (RenderScaleUtil, RenderTranslationUtil, RenderRotationUtil) on every
 * pushPose, popPose, scale, translate and mulPose of every PoseStack - model parts, block entities, items, the HUD -
 * which is thousands of ThreadLocal lookups and writes per frame, nearly all on the render thread.
 *
 * FancyMenuStateMixin makes each of the three static initialisers build this ThreadLocal instead of the one
 * ThreadLocal.withInitial returns. On the render thread it keeps that thread's value in a plain field: the first access
 * there takes over whatever the thread's ordinary ThreadLocal slot holds (or creates the initial value with the same
 * supplier, as get() would), and from then on get and set read and write the field. Every other thread uses the ordinary
 * ThreadLocal slot, untouched. So every thread sees exactly the values and objects it would see with FancyMenu's
 * ThreadLocal; only the render thread's lookup is cheaper. Only the render thread ever touches the field. Before
 * RenderSystem.initRenderThread no thread is the render thread, so every thread uses its slot until then and the render
 * thread's first access afterwards takes its slot's value over.
 *
 * Ported to 1.21.1: nothing changed. FancyMenu 3.9.14 for NeoForge 1.21.1 has the same three classes with the same
 * static ThreadLocal.withInitial initialisers and the same get/set use (only Minecraft member names differ), and
 * RenderSystem.isOnRenderThread is the same identity test of the thread set by initRenderThread.
 *
 * -Dbons_and_furious.fancymenuRenderThreadState=false (read once at start, not changeable while running: the render
 * thread's value must live in one place) makes these ordinary ThreadLocals.
 */
public final class RenderThreadLocal<T> extends ThreadLocal<T> {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.fancymenuRenderThreadState", "true"));
    private static volatile boolean announced;

    private final Supplier<? extends T> initial;
    private T renderValue;
    private boolean renderHeld;

    private RenderThreadLocal(Supplier<? extends T> initial) {
        this.initial = initial;
    }

    /** What the redirected ThreadLocal.withInitial(supplier) call returns. */
    public static <S> ThreadLocal<S> withInitial(Supplier<? extends S> supplier) {
        if (!ENABLED) {
            return ThreadLocal.withInitial(supplier);
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: fancymenu_render_thread_state applies (FancyMenu's render scale, translation and rotation are kept in plain fields on the render thread)");
        }
        return new RenderThreadLocal<>(java.util.Objects.requireNonNull(supplier));
    }

    @Override
    protected T initialValue() {
        return this.initial.get();
    }

    @Override
    public T get() {
        if (RenderSystem.isOnRenderThread()) {
            if (!this.renderHeld) {
                this.renderValue = super.get();
                this.renderHeld = true;
            }
            return this.renderValue;
        }
        return super.get();
    }

    @Override
    public void set(T value) {
        if (RenderSystem.isOnRenderThread()) {
            this.renderValue = value;
            this.renderHeld = true;
            return;
        }
        super.set(value);
    }

    @Override
    public void remove() {
        if (RenderSystem.isOnRenderThread()) {
            this.renderValue = null;
            this.renderHeld = false;
        }
        super.remove();
    }
}
