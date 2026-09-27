package bons.pure.fixes;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.IoSupplier;

/** Small in-memory overlay; source functions remain in their owner's installed JAR. */
public final class InstalledFunctionPack extends PathPackResources {
    private final Map<ResourceLocation, byte[]> functions;

    public InstalledFunctionPack(String id, Path metadataRoot, Map<ResourceLocation, byte[]> functions) {
        super(id, metadataRoot, true);
        this.functions = Map.copyOf(functions);
    }

    @Override public IoSupplier<InputStream> m_214146_(PackType type, ResourceLocation location) {
        byte[] bytes = type == PackType.SERVER_DATA ? functions.get(location) : null;
        return bytes == null ? super.m_214146_(type, location) : () -> new ByteArrayInputStream(bytes);
    }

    @Override public Set<String> m_5698_(PackType type) {
        Set<String> result = new HashSet<>(super.m_5698_(type));
        if (type == PackType.SERVER_DATA) for (ResourceLocation id : functions.keySet()) result.add(id.m_135827_());
        return result;
    }

    @Override public void m_8031_(PackType type, String namespace, String prefix, PackResources.ResourceOutput output) {
        super.m_8031_(type, namespace, prefix, (id, supplier) -> {
            if (!functions.containsKey(id)) output.accept(id, supplier);
        });
        if (type != PackType.SERVER_DATA) return;
        functions.forEach((id, bytes) -> {
            if (id.m_135827_().equals(namespace) && (prefix.isEmpty() || id.m_135815_().startsWith(prefix + "/")))
                output.accept(id, () -> new ByteArrayInputStream(bytes));
        });
    }

    public static byte[] repair(String path, byte[] original) {
        String text = new String(original, StandardCharsets.UTF_8), old, replacement, comment;
        if (path.equals("data/scorched/functions/tick.mcfunction")) {
            old = " at @s run function scorched:mob/sandcrab/burrow_stay";
            replacement = " at @s if entity @p[distance=..192] run function scorched:mob/sandcrab/burrow_stay";
            comment = "# Bons Pure Optimizations (scorched_sandcrab_burrow_gate): buried sandcrabs are processed within 192 blocks of a player";
        } else if (path.equals("data/scorched/functions/mob/sandcrab/burrow_stay.mcfunction")) {
            old = "data merge entity @s {Invulnerable:1b}";
            replacement = "execute unless entity @s[tag=bons_pure_sandcrab_invulnerable] run " + old
                    + "\r\ntag @s add bons_pure_sandcrab_invulnerable";
            comment = "# Bons Pure Optimizations (scorched_sandcrab_burrow_gate): Invulnerable is merged once instead of every tick";
        } else throw new IllegalArgumentException("Unexpected function path");
        int at = text.indexOf(old);
        if (at < 0 || text.indexOf(old, at + old.length()) >= 0) throw new IllegalArgumentException("Function anchor mismatch");
        return (comment + "\r\n" + text.substring(0, at) + replacement + text.substring(at + old.length())).getBytes(StandardCharsets.UTF_8);
    }
}
