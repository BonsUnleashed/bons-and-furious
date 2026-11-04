package bons.furious.patch.create_coo49;

/**
 * create_idle_behaviour_tick_skip: implemented by Create's BlockEntityBehaviour (BehaviourIdleStateMixin). True when the
 * behaviour's runtime class overrides neither tick() nor lazyTick() (decided once, kept in a byte of the behaviour).
 */
public interface IdleBehaviourState {
    boolean bons$idleBehaviour();
}
