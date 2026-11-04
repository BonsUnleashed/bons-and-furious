package bons.furious.patch.lionfishapi_bulk_c49;

import net.irisshaders.iris.api.v0.IrisApi;

/**
 * lionfishapi_model_bulk_vertices, the Oculus side (loaded only when Oculus' API is present; see BulkVertices): with a shader
 * pack in use, entity buffers take Oculus' extended format and batched rendering, and the switch stands down.
 */
final class ShaderCheck {
    private ShaderCheck() {
    }

    static boolean shaderPackInUse() {
        try {
            return IrisApi.getInstance().isShaderPackInUse();
        } catch (Throwable unknown) {
            return true;                                   // cannot tell: Lionfish's own loop runs
        }
    }
}
