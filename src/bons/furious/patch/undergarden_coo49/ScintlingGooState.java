package bons.furious.patch.undergarden_coo49;

/**
 * undergarden_scintling_idle_goo_interval (The Undergarden 0.8.14): the per-scintling state ScintlingIdleGooMixin keeps
 * (position and tick of the last goo attempt).
 */
public interface ScintlingGooState {
    boolean bons$undergardenCoo49GooTried();

    double bons$undergardenCoo49GooX();

    double bons$undergardenCoo49GooY();

    double bons$undergardenCoo49GooZ();

    int bons$undergardenCoo49GooTick();

    void bons$undergardenCoo49GooAttempt(double x, double y, double z, int tick);
}
