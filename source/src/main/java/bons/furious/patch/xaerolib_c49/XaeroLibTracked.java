package bons.furious.patch.xaerolib_c49;

/**
 * Bons and Furious (xaerolib_current_profile_memo, since 1.0.49): what XlConfigTrackMixin adds to XaeroLib's Config. A
 * Config is "quiet" (its changes do not count, see XaeroLibEpoch) only when it is the cache config of a redirector manager,
 * marked when that manager is built; every other Config counts. Member names carry the group.
 */
public interface XaeroLibTracked {
    void bons$xaerolibC49MarkQuiet();

    boolean bons$xaerolibC49Quiet();
}
