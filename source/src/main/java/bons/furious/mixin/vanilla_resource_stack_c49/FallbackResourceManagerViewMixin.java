package bons.furious.mixin.vanilla_resource_stack_c49;

import bons.furious.patch.vanilla_resource_stack_c49.RsStackViews;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * vanilla_resource_stack_miss_cache (Minecraft 1.21.1, client): read access to a FallbackResourceManager's pack entries
 * (fallbacks, public on NeoForge) and the per-namespace memo (missed ids, cacheable verdict) that MultiPackResourceStackMixin
 * keeps on it. No behaviour of the manager changes.
 */
@Mixin(value = FallbackResourceManager.class, remap = false)
public abstract class FallbackResourceManagerViewMixin implements RsStackViews.Fallback {
    @Shadow
    @Final
    public List<?> fallbacks;

    @Unique
    private volatile Set<ResourceLocation> bons$rsStackC49MissSet;

    @Unique
    private volatile int bons$rsStackC49VerdictBits;

    @Override
    public List<?> bons$rsStackC49Entries() {
        return this.fallbacks;
    }

    @Override
    public Set<ResourceLocation> bons$rsStackC49Misses() {
        Set<ResourceLocation> s = this.bons$rsStackC49MissSet;
        if (s == null) {
            synchronized (RsStackViews.class) {
                s = this.bons$rsStackC49MissSet;
                if (s == null) this.bons$rsStackC49MissSet = s = ConcurrentHashMap.newKeySet();
            }
        }
        return s;
    }

    @Override
    public int bons$rsStackC49Verdict() {
        return this.bons$rsStackC49VerdictBits;
    }

    @Override
    public void bons$rsStackC49SetVerdict(int verdict) {
        this.bons$rsStackC49VerdictBits = verdict;
    }
}
