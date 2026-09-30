package bons.furious.patch.etf;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import traben.entity_texture_features.utils.ETFUtils2;

/**
 * The texture id Entity Texture Features derives from a sprite name in its Material.buffer hook: the name itself when
 * it already ends with ".png", otherwise {@code <namespace>:textures/<path>.png}. ETF computed it on every call (a
 * string build plus a newly validated ResourceLocation); it is a pure function of the name, so it is computed once per
 * sprite name and remembered.
 *
 * A name that passes through is answered with the caller's own ResourceLocation, exactly as before. A derived id is a
 * remembered instance equal to the one ETF would build (ETF only compares it with equals and uses it as a map key). A
 * derivation that throws is not remembered, so it throws again on the next call just as it did.
 */
public final class SpriteTextureIds {
    private static final ConcurrentHashMap<ResourceLocation, ResourceLocation> DERIVED = new ConcurrentHashMap<>();
    /** Marker value: the sprite name already ends with ".png" and is its own texture id. */
    private static final ResourceLocation SELF = new ResourceLocation("bons_and_furious", "etf_self");

    private SpriteTextureIds() {}

    public static ResourceLocation of(ResourceLocation rawId) {
        ResourceLocation known = DERIVED.get(rawId);
        if (known != null) {
            return known == SELF ? rawId : known;
        }
        if (rawId.toString().endsWith(".png")) {
            DERIVED.putIfAbsent(rawId, SELF);
            return rawId;
        }
        ResourceLocation derived = ETFUtils2.res(rawId.m_135827_(), "textures/" + rawId.m_135815_() + ".png");
        ResourceLocation raced = DERIVED.putIfAbsent(rawId, derived);
        return raced == null || raced == SELF ? derived : raced;
    }
}
