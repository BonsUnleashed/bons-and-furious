package bons.furious.patch.vanilla_lookup_loops;

import bons.furious.mixin.vanilla_lookup_loops.SingleValuePaletteAccess;
import com.mojang.logging.LogUtils;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.SingleValuePalette;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_single_value_section_get (Minecraft 1.21.1 on NeoForge 21.1.252, both sides): runtime
 * switch of PalettedContainerSingleValueMixin. No Mojang code here.
 *
 * What vanilla does. PalettedContainer.get(int) answers every block-state and biome lookup in a chunk section:
 * data.palette.valueFor(data.storage.get(index)). One shared call site serves every palette kind (single value, linear,
 * hash map, global, and other mods' palettes), so the valueFor call is an interface dispatch through the full type check
 * for most containers. Sections that hold one value (all air, all stone, open water, one biome) use SingleValuePalette,
 * whose valueFor returns its value when the value is set and the id is 0, and throws otherwise.
 *
 * What the switch does. At that one valueFor call (the storage read before it is unchanged, so its range check and
 * exception come first as before): when the palette is exactly SingleValuePalette, the id is 0 and its value is set, the
 * value is returned from the field directly; that is what SingleValuePalette.valueFor returns for those inputs, with no
 * side effect. Every other palette, a null value or another id makes the original call (same result, same exception).
 *
 * -Dbons_and_furious.singleValueSectionGet=false switches it off at run time.
 */
public final class SingleValueSections {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.singleValueSectionGet", "true"));
    private static volatile boolean announced;

    private SingleValueSections() {
    }

    /** The valueFor call of PalettedContainer.get. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Object valueFor(Palette palette, int id) {
        if (palette.getClass() == SingleValuePalette.class && id == 0 && enabled) {   // the class test first: other palettes pay one compare
            Object value = ((SingleValuePaletteAccess) palette).bons$value();
            if (value != null) {
                if (!announced) {
                    announced = true;
                    LOGGER.info("Bons and Furious: vanilla_single_value_section_get applies (single-value chunk sections answer lookups from their value)");
                }
                return value;
            }
        }
        return palette.valueFor(id);
    }
}
