package bons.furious.patch.vanilla_resource_stack_c49;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;

/**
 * vanilla_resource_stack_miss_cache: read access our mixins add to the resource manager classes (fields Minecraft keeps
 * private or package-private), plus the per-namespace miss set and verdict our mixin keeps on FallbackResourceManager.
 */
public final class RsStackViews {
    private RsStackViews() {}

    /** FallbackResourceManager: its pack entries, and our per-namespace memo. */
    public interface Fallback {
        List<?> bons$rsStackC49Entries();

        Set<ResourceLocation> bons$rsStackC49Misses();

        /** Cached verdict: the entry count it was computed for (high bits) and 1 = cacheable / 2 = not (low 2 bits); 0 = none. */
        int bons$rsStackC49Verdict();

        void bons$rsStackC49SetVerdict(int verdict);
    }

    /** FallbackResourceManager$PackEntry (a package-private record): its pack and its filter. */
    public interface Entry {
        PackResources bons$rsStackC49Pack();

        Object bons$rsStackC49Filter();
    }

    /** net.minecraft.server.packs.PathPackResources: its root path. */
    public interface FolderPack {
        Path bons$rsStackC49Root();
    }

    /** net.minecraft.server.packs.VanillaPackResources: its root paths and per-type paths. */
    public interface VanillaPack {
        List<Path> bons$rsStackC49Roots();

        Map<PackType, List<Path>> bons$rsStackC49TypePaths();
    }
}
