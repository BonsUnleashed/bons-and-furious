package bons.furious.patch.immediatelyfast;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Core shader files that are known to work with ImmediatelyFast's HUD batching, recognised by their exact bytes.
 *
 * ImmediatelyFast switches HUD batching off when any pack replaces one of the core shaders on its blacklist, because a
 * replacement might depend on how and when its draws are submitted. The one file that trips it in this pack is Call of
 * Yucutan 1.0.13's rendertype_entity_translucent_cull.fsh (it arrives through Forge's combined mod_resources pack, which
 * cannot carry ImmediatelyFast's compatibility metadata). It is vanilla's shader with one extra per-pixel branch: a
 * fragment whose alpha is 254/255 is drawn with its unlit texture colour (an emissive marker). It reads the same inputs
 * and uniforms as vanilla, in the same way, so batching treats it exactly like the vanilla shader. Any other content at
 * that path (another version of the mod, a resource pack override) is not recognised and ImmediatelyFast decides as before.
 */
public final class KnownCoreShaders {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** shader file id -> SHA-256 of the exact file contents known to be batching-safe. */
    private static final Map<String, Set<String>> KNOWN = Map.of(
            "minecraft:shaders/core/rendertype_entity_translucent_cull.fsh",
            Set.of("d85c135b47898228b977d25c1ca0a59495c9e5e2e05de60276ce6764e8ff2770"));   // Call of Yucutan 1.0.13
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    private KnownCoreShaders() {}

    /** The resource ImmediatelyFast asked for, or empty when it is a known batching-safe file (so it is not counted). */
    public static Optional<Resource> filter(ResourceLocation id, Optional<Resource> resource) {
        if (resource.isEmpty()) return resource;
        Set<String> known = KNOWN.get(id.toString());
        if (known == null) return resource;
        String sha;
        try (InputStream in = resource.get().open()) {
            sha = sha256(in.readAllBytes());
        } catch (Exception e) {
            return resource;
        }
        if (!known.contains(sha)) return resource;
        if (LOGGED.add(id + "|" + sha)) {
            LOGGER.info("Bons and Furious: immediatelyfast_known_core_shaders: {} from {} is a known batching-safe file; it does not switch HUD batching off",
                    id, resource.get().sourcePackId());
        }
        return Optional.empty();
    }

    private static String sha256(byte[] data) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : d) sb.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        return sb.toString();
    }
}
