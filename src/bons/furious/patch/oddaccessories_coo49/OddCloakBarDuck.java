package bons.furious.patch.oddaccessories_coo49;

/**
 * oddaccessories_cloak_action_bar_interval: what OddCloakBarStateMixin adds to Player: the Fiberglass Cloak indicator last
 * sent to this player (0 none, OddScans.BAR_BUSY "(⭘)", OddScans.BAR_CLOAKED "(-)") and the game time it was sent at.
 */
public interface OddCloakBarDuck {
    byte bons$oddaccessoriesCoo49BarSite();

    long bons$oddaccessoriesCoo49BarTick();

    void bons$oddaccessoriesCoo49BarSent(byte site, long tick);
}
