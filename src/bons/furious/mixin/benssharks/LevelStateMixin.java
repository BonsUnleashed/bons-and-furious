package bons.furious.mixin.benssharks;

import bons.furious.patch.benssharks.LevelState;
import bons.furious.patch.benssharks.LevelStateHolder;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * benssharks_tick_listeners (Ben's Sharks 1.3.0): one {@link LevelState} per Level, created on first use by that level's own
 * thread.
 */
@Mixin(value = Level.class, remap = false)
public abstract class LevelStateMixin implements LevelStateHolder {
    @Unique
    private LevelState bons$sharksStateField;

    @Override
    public LevelState bons$sharksState() {
        LevelState s = bons$sharksStateField;
        if (s == null) bons$sharksStateField = s = new LevelState();
        return s;
    }
}
