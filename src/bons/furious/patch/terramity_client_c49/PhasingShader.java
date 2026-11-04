package bons.furious.patch.terramity_client_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.eventbus.api.Event;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch terramity_phasing_shader_local_only (Terramity 0.9.8; client; since 1.0.49; a gameplay fix, off by
 * default). Idea: Collections Of Optimizations (terramity.fixPhasingShaderStomp), idea text only. Our own logic; no
 * Terramity code (All Rights Reserved, injectors only).
 *
 * Terramity's PhasingShaderProcedure runs on every player tick of the client, for every player in the client's world
 * (other players tick there too), and works on the client's own screen: when the ticking player has Phasing, Flipped
 * Gravity, Dizzy or the Conductite Scouter display and no post effect is active it loads one (invert, flip, wobble, scan),
 * and when that player has none of them and any post effect is active it shuts that effect down. So another player's
 * Phasing turns your screen inverted, another player without effects switches your own Terramity effect off every tick,
 * and the game's own post effects (the creeper, spider and enderman views of spectator mode) and other mods' effects are
 * switched off every tick while you have none of Terramity's states.
 *
 * What changes: on a client level the procedure acts only for this client's own player (another player's tick does
 * nothing), and its shut-down branch shuts down only the post effect Terramity's own load calls made (the last one is
 * recorded); any other active effect stays. The load branches are Terramity's own.
 *
 * -Dbons_and_furious.terramityPhasingShaderLocalOnly=false runs Terramity's procedure unchanged.
 */
public final class PhasingShader {
    public static final String KEY = "terramity_phasing_shader_local_only";
    public static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.terramityPhasingShaderLocalOnly", "true"));
    private static volatile boolean announced;
    /** The post effect Terramity's own load calls made last (client thread only). */
    static PostChain ours;

    private PhasingShader() {
    }

    /** The wrapped PhasingShaderProcedure.execute(event, world, entity) (called from Terramity's player tick handler). */
    public static void execute(Event event, LevelAccessor world, Entity entity, Operation<Void> original) {
        if (enabled && entity != null && world != null && world.m_5776_() && entity != Minecraft.m_91087_().f_91074_) {
            announce();
            return;
        }
        original.call(event, world, entity);
    }

    /** The wrapped GameRenderer.loadEffect of the procedure (its four effects): the chain it makes is remembered as Terramity's. */
    public static void load(GameRenderer renderer, ResourceLocation id, Operation<Void> original) {
        original.call(renderer, id);
        ours = renderer.m_109149_();
    }

    /** The wrapped GameRenderer.shutdownEffect of the procedure: only Terramity's own effect is shut down. */
    public static void shutdown(GameRenderer renderer, Operation<Void> original) {
        if (enabled && renderer.m_109149_() != ours) {
            announce();
            return;
        }
        ours = null;
        original.call(renderer);
    }

    private static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (Terramity's screen effects follow only this client's own player and leave other post effects alone)", KEY);
        }
    }
}
