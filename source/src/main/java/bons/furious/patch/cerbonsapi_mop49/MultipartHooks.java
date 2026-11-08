package bons.furious.patch.cerbonsapi_mop49;

import com.cerbon.cerbons_api.api.multipart_entities.entity.MultipartAwareEntity;
import com.cerbon.cerbons_api.api.multipart_entities.entity.MultipartEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch cerbonsapi_multipart_hooks_lean (Cerbon's API; 1.21.1 tested build: Cerbon's API 1.3.0 for
 * NeoForge 1.21; both sides; since 1.0.49). Cerbon's API is LGPL-3.0; the two hooks below do what its EntityMixin's two
 * handlers do.
 *
 * Cerbon's API hooks Entity.getBoundingBox (a RETURN inject with a CallbackInfoReturnable) and Entity.setPosRaw (a TAIL
 * inject) for every entity of the game, to hand a compound box out for its multipart entities and to tell multipart-aware
 * entities they moved. getBoundingBox is the most called entity method (collision, movement, every entity search tests
 * each candidate's box): on every call that hook built its callback object and asked `this instanceof MultipartEntity`,
 * an interface test that a mob class (dozens of interfaces in a large pack) answers "no" only after scanning its whole
 * interface list. This switch leaves Cerbon's EntityMixin out and makes the same two calls itself: a MixinExtras
 * @ModifyReturnValue (no callback object) and a TAIL inject, both deciding from the entity's kind, which is worked out
 * with the same two instanceof tests once per entity and kept in the entity (an entity's class never changes). Same box,
 * same onSetPos calls, for every entity.
 */
public final class MultipartHooks {
    /** Runtime switch of the kept kind; -Dbons_and_furious.cerbonsapiMultipartHooksLean=false asks instanceof every call. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cerbonsapiMultipartHooksLean", "true"));

    /** Kinds kept in the entity: 0 not worked out yet, 1 neither, 2 MultipartEntity only, 3 MultipartAwareEntity (also a MultipartEntity). */
    public static final byte UNKNOWN = 0, PLAIN = 1, MULTIPART = 2, AWARE = 3;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private MultipartHooks() {
    }

    /** The kind of this entity object, from the same instanceof tests Cerbon's handlers make. */
    public static byte kindOf(Object entity) {
        if (!announced) announce();
        return entity instanceof MultipartAwareEntity ? AWARE : entity instanceof MultipartEntity ? MULTIPART : PLAIN;
    }

    private static void announce() {
        announced = true;
        LOGGER.info("Bons and Furious: cerbonsapi_multipart_hooks_lean: Cerbon's API entity hooks run without a callback object, each entity's kind kept");
    }
}
