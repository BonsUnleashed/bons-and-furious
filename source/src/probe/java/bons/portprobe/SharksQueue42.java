package bons.portprobe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.mcreator.sharks.BenssharksMod;
import net.minecraft.server.MinecraftServer;

/** Actual server event-loop queue timing, including work enqueued by work. */
public final class SharksQueue42 {
    private static final List<String> observed = new ArrayList<>();
    private static int origin;
    public static void begin(MinecraftServer server) {
        observed.clear();
        origin = server.getTickCount();
        BenssharksMod.queueServerWork(1, () -> {
            observed.add("first:" + (server.getTickCount() - origin));
            BenssharksMod.queueServerWork(2, () -> observed.add("nested:" + (server.getTickCount() - origin)));
        });
        BenssharksMod.queueServerWork(3, () -> observed.add("third:" + (server.getTickCount() - origin)));
    }
    public static Map<String, Object> finish() {
        if (observed.size() != 3 || !observed.get(0).startsWith("first:"))
            throw new AssertionError("Queue work lost or duplicated: " + observed);
        return Map.of("passed", true, "events", List.copyOf(observed));
    }
}