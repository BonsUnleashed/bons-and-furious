package bons.furious.patch.benssharks;

/** benssharks_tick_listeners: implemented by every Level through bons.furious.mixin.benssharks.LevelStateMixin. */
public interface LevelStateHolder {
    LevelState bons$sharksState();
}
