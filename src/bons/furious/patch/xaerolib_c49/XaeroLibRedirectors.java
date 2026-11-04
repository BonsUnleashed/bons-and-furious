package bons.furious.patch.xaerolib_c49;

/**
 * Bons and Furious (xaerolib_current_profile_memo / xaerolib_enforcement_read_memo, since 1.0.49): what XlRedirectorTrackMixin
 * adds to XaeroLib's OptionValueRedirectorManager (and so to ClientOptionValueRedirectorManager): whether a redirector is
 * registered for an option (a read of its redirectors map). Member names carry the group.
 */
public interface XaeroLibRedirectors {
    boolean bons$xaerolibC49HasRedirector(Object option);
}
