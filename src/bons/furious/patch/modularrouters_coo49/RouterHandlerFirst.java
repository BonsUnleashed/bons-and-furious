package bons.furious.patch.modularrouters_coo49;

import org.apache.logging.log4j.LogManager;

/**
 * Bons and Furious 1.0.49 switch modularrouters_cached_handler_first (Modular Routers 12.1.1+mc1.20.1, server; kind opt,
 * default on; helper of ModuleTargetHandlerMixin; no Modular Routers code carried).
 *
 * What Modular Routers does. ModuleTarget.getItemHandler() first resolves the target's world
 * (MiscUtil.getWorldForGlobalPos: ServerLifecycleHooks.getCurrentServer().getLevel(dimension)) and then asks
 * getItemHandlerFor(world), which returns the remembered item handler at once while it is present and only uses the world
 * argument to look the handler up again when it is not. Every module run of an item module pays the world lookup.
 *
 * What the switch does. While the remembered handler is present, the world lookup is skipped (the call answers null, which
 * getItemHandlerFor never reads in that case): the same handler object comes back, nothing else runs or changes. The
 * lookup itself is a plain map read with no side effect, and every caller of getItemHandler (the compiled item modules and
 * the filter settings message) runs on the server thread, where the server exists. Runtime flag
 * bons_and_furious.modularroutersCachedHandlerFirst.
 */
public final class RouterHandlerFirst {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.modularroutersCachedHandlerFirst", "true"));
    private static volatile boolean announced;

    private RouterHandlerFirst() {
    }

    /** Called when the switch first leaves the lookup out (one INFO line). */
    public static void acted() {
        if (announced) return;
        announced = true;
        LogManager.getLogger("Bons and Furious").info("Bons and Furious: modularrouters_cached_handler_first applies (a router module's remembered item handler is answered before its world is looked up)");
    }
}
