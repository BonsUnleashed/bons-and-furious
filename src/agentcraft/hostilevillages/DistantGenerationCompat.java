package agentcraft.hostilevillages;

/** DH builds temporary terrain and deliberately does not persist spawned entities. */
public final class DistantGenerationCompat {
    private static final String DH_REGION =
            "com.seibel.distanthorizons.common.wrappers.worldGeneration.mimicObject.DhLitWorldGenRegion_forge";

    private DistantGenerationCompat() {}

    /** Name-based identification keeps Distant Horizons an optional dependency. */
    public static boolean isDistantGeneration(Object context) {
        if (context == null) return false;
        for (Class<?> type = context.getClass(); type != null; type = type.getSuperclass()) {
            if (DH_REGION.equals(type.getName())) return true;
        }
        return false;
    }
}
