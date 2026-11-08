package bons.furious.patch.touchnmotion_c49;

/**
 * Bons and Furious switches touchnmotion_ground_probe_direct and touchnmotion_pose_block_memo (Touch'n Motion 1.0.4 for
 * NeoForge 1.21.1, tested build; client; since 1.0.49): the render frame both work in. GameRenderer.render(DeltaTracker,
 * boolean) (once per frame, from Minecraft.runTick) opens and closes it (TnmProbeFrameMixin); TnmMemoFrameMixin only
 * marks that the memo switch is applied. Touch'n Motion probes the ground while players are drawn (RenderPlayerEvent.Pre /
 * Post, Entity Model Features' animation hooks), inside that call. Nothing in the client level and no player's position,
 * sneak flag or held item changes there: queued tasks (packets) and client ticks run before it in Minecraft.runTick, on
 * the same thread, and Touch'n Motion 1.0.4 itself sets no block and changes no entity's position, sneak flag or held
 * item. Outside an open frame, or on another thread, every probe runs as shipped.
 */
public final class TnmFrame {
    /** Changes at every open and close, so nothing kept for one frame is ever used in another. */
    static int frame;
    /** Inside GameRenderer.render (read by the wrappers first: a probe outside it is handed over at once). */
    public static boolean open;
    static Thread thread;
    /** The memo switch's frame mixin is applied (set at the start of every frame). */
    static boolean memoArmed;

    private TnmFrame() {
    }

    /** GameRenderer.render HEAD. */
    public static void open() {
        frame++;
        thread = Thread.currentThread();
        open = true;
    }

    /** GameRenderer.render RETURN: the frame's collision contexts and memo let go of the level and the players. */
    public static void close() {
        frame++;
        open = false;
        TnmProbe.release();
        TnmMemo.release();
    }

    /** GameRenderer.render HEAD, when the memo switch is applied. */
    public static void armMemo() {
        memoArmed = true;
    }

    /** True on the thread that opened the frame, while it is open. */
    static boolean here() {
        return open && Thread.currentThread() == thread;
    }
}
