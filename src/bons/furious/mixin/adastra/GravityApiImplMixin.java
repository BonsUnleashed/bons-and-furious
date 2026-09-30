package bons.furious.mixin.adastra;

import earth.terrarium.adastra.api.planets.Planet;
import earth.terrarium.adastra.api.planets.PlanetApi;
import earth.terrarium.adastra.common.systems.GravityApiImpl;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * adastra_gravity_primitive (Ad Astra 1.15.20).
 *
 * The dimension gravity lookup, reached for every entity every tick, boxed the planet's gravity into a Float (through
 * Optionull and a Planet::gravity function) only to unbox it again. It now reads the float directly: the planet's
 * gravity, or Earth's 9.807 for a dimension that is not an Ad Astra planet, divided by Earth's 9.807, as before.
 */
@Mixin(value = GravityApiImpl.class, remap = false)
public abstract class GravityApiImplMixin {
    /**
     * @author BonsUnleashed
     * @reason Gravity relative to Earth as a primitive float, without the Float boxing (a per-entity, per-tick path).
     */
    @Overwrite
    public float getGravity(ResourceKey<Level> level) {
        Planet planet = PlanetApi.API.getPlanet(level);
        return (planet != null ? planet.gravity() : 9.807f) / 9.807f;
    }
}
