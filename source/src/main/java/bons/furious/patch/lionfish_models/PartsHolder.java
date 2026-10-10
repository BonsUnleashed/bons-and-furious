package bons.furious.patch.lionfish_models;

import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedModelBox;

/**
 * lionfish_model_parts (since 1.0.36; 1.21.1 tested build lionfishapi-3.1): the per-model field
 * AdvancedEntityModelPartsMixin adds to Lionfish's AdvancedEntityModel (the kept part list; null until first used). Only
 * LionfishModelParts reads or writes it.
 *
 * Ported to 1.21.1: the second field of 1.0.36 (kept bones by name) is gone with the bone memo, which Lionfish 3.1's own
 * descendantCache replaces.
 */
public interface PartsHolder {
    Iterable<AdvancedModelBox> bons$keptParts();

    void bons$setKeptParts(Iterable<AdvancedModelBox> parts);
}
