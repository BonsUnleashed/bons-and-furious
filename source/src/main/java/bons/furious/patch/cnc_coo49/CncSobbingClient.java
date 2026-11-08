package bons.furious.patch.cnc_coo49;

import net.minecraft.client.renderer.PostChain;

/**
 * cnc_sobbing_shader_own_only (Critters n' Crawlers 2.2.5 for Minecraft 1.21.1, client only: loaded only by
 * SobbingShaderOwnMixin, which is in the group's client mixin list): whether the active post effect is the mod's own cry
 * effect, by the name the post chain was loaded under (PostChain.getName = the location's text).
 */
public final class CncSobbingClient {
    /** The location SobbingShaderProcedure loads, as PostChain.getName reports it. */
    public static final String CRY = "cnc:shaders/post/cry.json";

    private CncSobbingClient() {
    }

    public static boolean isCry(PostChain chain) {
        return chain != null && CRY.equals(chain.getName());
    }
}
