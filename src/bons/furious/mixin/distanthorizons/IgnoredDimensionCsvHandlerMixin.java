package bons.furious.mixin.distanthorizons;

import com.seibel.distanthorizons.core.config.eventHandlers.IgnoredDimensionCsvHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * distanthorizons_ignored_dimension_match (Distant Horizons 3.3.2).
 *
 * The ignored-dimension check runs for every chunk update (client and server) and before every render. It copied the
 * part of the dimension name after the first '@' into a new String before comparing it with each ignored name. The
 * suffix is now compared in place with an exact-length, case-insensitive regionMatches, which accepts exactly the
 * names equalsIgnoreCase accepted.
 */
@Mixin(value = IgnoredDimensionCsvHandler.class, remap = false)
public abstract class IgnoredDimensionCsvHandlerMixin {
    @Shadow
    private String[] dimensionNames;

    /**
     * @author BonsUnleashed
     * @reason Compare the dimension-name suffix in place instead of copying it for every check.
     */
    @Overwrite
    public boolean dimensionNameShouldBeIgnored(String dimName) {
        if (this.dimensionNames == null || this.dimensionNames.length == 0) {
            return false;
        }
        // indexOf returns -1 without an '@', so the suffix is then the whole name.
        int suffixStart = dimName.indexOf('@') + 1;
        int suffixLength = dimName.length() - suffixStart;
        for (int i = 0; i < this.dimensionNames.length; i++) {
            String ignoredName = this.dimensionNames[i];
            if (ignoredName != null && ignoredName.length() == suffixLength
                    && dimName.regionMatches(true, suffixStart, ignoredName, 0, suffixLength)) {
                return true;
            }
        }
        return false;
    }
}
