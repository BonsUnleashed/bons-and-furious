package bons.furious.mixin.embeddium;

import bons.pure.optimizations.MappedUploads;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import me.jellysquid.mods.sodium.client.gl.arena.GlBufferArena;
import me.jellysquid.mods.sodium.client.gl.arena.PendingUpload;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * embeddium_direct_upload_preparation, region half (Embeddium 0.3.31+mc1.20.1).
 *
 * uploadMeshes and uploadResorts gave each arena uploads.stream().map(lambda), plus .filter(Objects::nonNull) for index
 * uploads, which the arena collected straight back into a LinkedList. At those three call sites the stream calls now
 * build nothing and only note the list, Embeddium's own mapping lambda and the null filter, and the arena call hands
 * them to ac$submit, which uses the arena's bons$uploadMapped (GlBufferArenaMappedUploadsMixin) or, for an arena
 * without it, the original stream. Redirects are used instead of an @Overwrite because both methods build
 * RenderRegionManager's private upload records, which code outside that class cannot name; the three pending fields
 * only live from the stream call to the arena call of one upload, on the render thread.
 */
@Mixin(value = RenderRegionManager.class, remap = false)
public abstract class RenderRegionManagerDirectUploadMixin {
    @Unique private List<?> bons$pendingUploads;
    @Unique private Function<?, ?> bons$pendingMapper;
    @Unique private boolean bons$pendingOmitNull;

    /** uploads.stream(): remember the list instead of streaming it. */
    @Redirect(method = {
            "uploadMeshes(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Lme/jellysquid/mods/sodium/client/render/chunk/region/RenderRegion;Ljava/util/Collection;)V",
            "uploadResorts(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Lme/jellysquid/mods/sodium/client/render/chunk/region/RenderRegion;Ljava/util/Collection;)V"},
            at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;stream()Ljava/util/stream/Stream;"), require = 3, allow = 3)
    private Stream<?> bons$deferStream(ArrayList<?> uploads) {
        this.bons$pendingUploads = uploads;
        this.bons$pendingOmitNull = false;
        return null;
    }

    /** .map(upload -> upload.vertexUpload / indexUpload): remember Embeddium's lambda. */
    @Redirect(method = {
            "uploadMeshes(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Lme/jellysquid/mods/sodium/client/render/chunk/region/RenderRegion;Ljava/util/Collection;)V",
            "uploadResorts(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Lme/jellysquid/mods/sodium/client/render/chunk/region/RenderRegion;Ljava/util/Collection;)V"},
            at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;map(Ljava/util/function/Function;)Ljava/util/stream/Stream;"),
            require = 3, allow = 3)
    private Stream<?> bons$deferMap(Stream<?> deferred, Function<?, ?> mapper) {
        this.bons$pendingMapper = mapper;
        return null;
    }

    /** .filter(Objects::nonNull), the only filter in both methods: drop null uploads. */
    @Redirect(method = {
            "uploadMeshes(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Lme/jellysquid/mods/sodium/client/render/chunk/region/RenderRegion;Ljava/util/Collection;)V",
            "uploadResorts(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Lme/jellysquid/mods/sodium/client/render/chunk/region/RenderRegion;Ljava/util/Collection;)V"},
            at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;"),
            require = 2, allow = 2)
    private Stream<?> bons$deferNonNullFilter(Stream<?> deferred, Predicate<?> nonNull) {
        this.bons$pendingOmitNull = true;
        return null;
    }

    /** arena.upload(commandList, stream): upload what the three calls above noted. */
    @Redirect(method = {
            "uploadMeshes(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Lme/jellysquid/mods/sodium/client/render/chunk/region/RenderRegion;Ljava/util/Collection;)V",
            "uploadResorts(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Lme/jellysquid/mods/sodium/client/render/chunk/region/RenderRegion;Ljava/util/Collection;)V"},
            at = @At(value = "INVOKE",
                    target = "Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferArena;upload(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Ljava/util/stream/Stream;)Z"),
            require = 3, allow = 3)
    private boolean bons$uploadDeferred(GlBufferArena arena, CommandList commandList, Stream<PendingUpload> deferred) {
        List<?> uploads = this.bons$pendingUploads;
        Function<?, ?> mapper = this.bons$pendingMapper;
        boolean omitNull = this.bons$pendingOmitNull;
        this.bons$pendingUploads = null;
        this.bons$pendingMapper = null;
        return ac$submit(arena, commandList, uploads, mapper, omitNull);
    }

    /** One arena upload: bons$uploadMapped when the arena has it, otherwise the original stream. */
    @Unique
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean ac$submit(GlBufferArena arena, CommandList commandList, List list, Function function, boolean omitNull) {
        if (arena instanceof MappedUploads mapped) {
            return mapped.bons$uploadMapped(commandList, list, function, omitNull);
        }
        Stream stream = list.stream().map(function);
        if (omitNull) {
            stream = stream.filter(Objects::nonNull);
        }
        return arena.upload(commandList, stream);
    }
}
