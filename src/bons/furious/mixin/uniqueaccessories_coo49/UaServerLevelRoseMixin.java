package bons.furious.mixin.uniqueaccessories_coo49;

import bons.furious.patch.uniqueaccessories_coo49.UaRoseCandidates;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * uniqueaccessories_rose_cleanup_candidates (since 1.0.49): a field on every ServerLevel for its Rose of Temptation
 * candidate set (UaRoseCandidates.LevelHolder), so the set lives and goes with its level. Adds a field and two accessors;
 * no method of ServerLevel is changed.
 */
@Mixin(value = ServerLevel.class, remap = false)
public abstract class UaServerLevelRoseMixin implements UaRoseCandidates.LevelHolder {
    @Unique
    private Object bons$uniqueaccessoriesCoo49RoseCandidatesField;

    @Override
    public Object bons$uniqueaccessoriesCoo49RoseCandidates() {
        return this.bons$uniqueaccessoriesCoo49RoseCandidatesField;
    }

    @Override
    public void bons$uniqueaccessoriesCoo49SetRoseCandidates(Object set) {
        this.bons$uniqueaccessoriesCoo49RoseCandidatesField = set;
    }
}
