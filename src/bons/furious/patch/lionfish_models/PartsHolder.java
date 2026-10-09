package bons.furious.patch.lionfish_models;

import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedModelBox;
import java.util.HashMap;
import java.util.Optional;

/**
 * lionfish_model_parts (since 1.0.36): the two per-model fields AdvancedEntityModelPartsMixin adds to Lionfish's
 * AdvancedEntityModel (the kept part list and the kept bones by name; both null until first used). Only
 * LionfishModelParts reads or writes them.
 */
public interface PartsHolder {
    Iterable<AdvancedModelBox> bons$keptParts();

    void bons$setKeptParts(Iterable<AdvancedModelBox> parts);

    HashMap<String, Optional<AdvancedModelBox>> bons$keptBones();

    void bons$setKeptBones(HashMap<String, Optional<AdvancedModelBox>> bones);
}