package bons.furious.patch.timm_c49;

import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;

/**
 * Bons and Furious switch timm_stream_music (The Immersive Music Mod 1.1 for NeoForge 1.21, tested build; client; since
 * 1.0.49). Kind: fix (memory that only grows). Idea: Collections Of Optimizations (timm.streamMusic), idea text only.
 *
 * What TIMM ships. assets/timm/sounds.json defines its sound events over 147 OGG songs, all named "timm:music/...", and none
 * is marked "stream": true. Minecraft plays a sound that is not streamed by decoding the whole file
 * (SoundBufferLibrary.getCompleteBuffer on a background thread, the song starts when the decode is done) into one OpenAL
 * buffer that SoundBufferLibrary keeps until the next resource reload. So every TIMM song played once stays in the sound
 * device's memory as raw PCM (tens of megabytes per song) for the rest of the session. Minecraft's own music is streamed
 * ("stream": true in its sounds.json): decoded a second at a time into a few small rotating buffers.
 *
 * What the switch does. Sound.shouldStream() (read by SoundEngine.play to pick the streaming channel pool and the streaming
 * decode, and by the sound preparation) answers true for a sound whose location is in the "timm" namespace and whose path
 * starts with "music/", i.e. for TIMM's songs; every other sound answers as before. The decision is made once per sound
 * definition, at the end of Sound's constructor (TimmSoundStreamMixin sets the stream flag shouldStream() returns), so
 * shouldStream() stays the shipped field read. The song then plays through the same streaming path as Minecraft's own
 * music: the same decoded samples, in a streaming channel, posting NeoForge's PlayStreamingSourceEvent instead of
 * PlaySoundSourceEvent as for Minecraft's music; nothing is kept after the song ends, and a song starts after its first
 * second is decoded instead of the whole file.
 *
 * -Dbons_and_furious.timmStreamMusic=false answers as TIMM's sounds.json says again (emergency off).
 */
public final class TimmStreamMusic {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.timmStreamMusic", "true"));
    private static volatile boolean announced;

    private TimmStreamMusic() {
    }

    /** The end of Sound's constructor: the stream flag its definition gave it, and its location (Sound.location). */
    public static boolean shouldStream(boolean original, ResourceLocation location) {
        if (original || !enabled || location == null) return original;
        if (!"timm".equals(location.getNamespace()) || !location.getPath().startsWith("music/")) return false;
        if (!announced) announce();
        return true;
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LogManager.getLogger("Bons and Furious").info("Bons and Furious: timm_stream_music applies (The Immersive Music Mod's songs are streamed like Minecraft's own music instead of being kept whole in sound memory)");
    }
}
