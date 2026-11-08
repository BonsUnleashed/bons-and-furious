package bons.furious.patch.timm_c49;

import com.github.charlyb01.timm.music.BiomePlaylist;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.Music;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.apache.logging.log4j.LogManager;

/**
 * Bons and Furious switch timm_creative_biome_playlist (The Immersive Music Mod 1.1 for NeoForge 1.21, tested build; client;
 * since 1.0.49). Kind: change, used for a gameplay fix (off by default). Idea: Collections Of Optimizations
 * (timm.creativeBiomeMusic), idea text only.
 *
 * What TIMM does. When Minecraft picks creative music (getSituationalMusic at Musics.CREATIVE), TIMM's
 * MinecraftMixin.updateCreativeMusic answers with a song of its "creative" playlist (BiomePlaylist.getCreativeMusic), which
 * never sets BiomePlaylist.CURRENT_BIOME_EVENT. MusicManagerMixin then stores that stale value as the playing song's biome
 * event, and its biome-switch check fades the creative song out as soon as the current biome's playlist does not list the
 * stale event (a song from an earlier survival biome), after fade_delay seconds.
 *
 * What the switch changes. MixinSquared @TargetHandler + MixinExtras @WrapOperation on the getCreativeMusic call inside that
 * handler: TIMM's survival answer runs first, BiomePlaylist.getMusicSound(the biome the player stands in, the same random)
 * (which also sets CURRENT_BIOME_EVENT, to the chosen song or to "undefined" when the biome has no playlist); when it has no
 * song, TIMM's creative playlist answers as before. In creative mode TIMM then plays the current biome's songs as in
 * survival (and fades them out on biome changes as in survival), and a creative-playlist song is no longer faded out
 * because of a song played earlier in survival. Client only: nothing is saved or sent.
 *
 * -Dbons_and_furious.timmCreativeBiomePlaylist=false runs TIMM's creative answer only (emergency off; the config key decides
 * whether the mixin is applied at all).
 */
public final class TimmCreativePlaylist {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.timmCreativeBiomePlaylist", "true"));
    private static volatile boolean announced;

    private TimmCreativePlaylist() {
    }

    /** TIMM's updateCreativeMusic handler: its BiomePlaylist.getCreativeMusic(random) call (player != null there). */
    public static Music creativeMusic(Minecraft minecraft, RandomSource random, Operation<Music> original) {
        if (!enabled || minecraft == null) return original.call(random);
        LocalPlayer player = minecraft.player;
        if (player == null) return original.call(random);
        Level level = player.level();
        if (level == null) return original.call(random);
        Holder<Biome> biome = level.getBiome(player.blockPosition());
        Optional<ResourceKey<Biome>> key = biome.unwrapKey();
        if (key.isPresent()) {
            if (!announced) announce();
            Music biomeSong = BiomePlaylist.getMusicSound(key.get().location(), random);
            if (biomeSong != null) return biomeSong;
        }
        return original.call(random);
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LogManager.getLogger("Bons and Furious").info("Bons and Furious: timm_creative_biome_playlist applies (in creative mode The Immersive Music Mod plays the current biome's playlist, as in survival)");
    }
}
