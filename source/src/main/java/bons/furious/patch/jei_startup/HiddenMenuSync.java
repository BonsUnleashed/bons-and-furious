package bons.furious.patch.jei_startup;

import java.util.concurrent.atomic.LongAdder;

/** Only JEI's dedicated simulation menus are marked. Never skip recipe calculations or Forge events. */
public final class HiddenMenuSync {
    public static volatile boolean enabled = !Boolean.getBoolean("bons_and_furious.jeiHiddenMenuSync.off");
    public static final LongAdder SKIPPED = new LongAdder();
    public interface Menu { void bons$jeiSimulationMenu(); }
    private HiddenMenuSync() {}
    public static void mark(Object menu) { if (menu instanceof Menu marked) marked.bons$jeiSimulationMenu(); }
}
