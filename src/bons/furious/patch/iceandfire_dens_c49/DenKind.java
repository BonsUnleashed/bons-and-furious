package bons.furious.patch.iceandfire_dens_c49;

import com.github.alexthe666.iceandfire.datagen.IafPlacedFeatures;
import com.github.alexthe666.iceandfire.entity.IafEntityRegistry;
import com.github.alexthe666.iceandfire.world.IafWorldRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** iceandfire_structure_dens (since 1.0.49): the three Ice and Fire dragon caves and what each one is made of. */
public enum DenKind {
    FIRE_CAVE("fire_cave"),
    ICE_CAVE("ice_cave"),
    LIGHTNING_CAVE("lightning_cave");

    public static final Codec<DenKind> CODEC = Codec.STRING.comapFlatMap(DenKind::read, k -> k.id);

    public final String id;

    DenKind(String id) {
        this.id = id;
    }

    static DataResult<DenKind> read(String id) {
        DenKind k = byId(id);
        return k != null ? DataResult.success(k) : DataResult.error(() -> "unknown Ice and Fire dragon cave kind " + id);
    }

    static DenKind byId(String id) {
        for (DenKind k : values()) if (k.id.equals(id)) return k;
        return null;
    }

    /** Ice and Fire's feature for this cave (its generator). */
    public Feature<NoneFeatureConfiguration> feature() {
        return switch (this) {
            case FIRE_CAVE -> IafWorldRegistry.FIRE_DRAGON_CAVE.get();
            case ICE_CAVE -> IafWorldRegistry.ICE_DRAGON_CAVE.get();
            case LIGHTNING_CAVE -> IafWorldRegistry.LIGHTNING_DRAGON_CAVE.get();
        };
    }

    /** Ice and Fire's placed feature (the biomes whose feature list holds it get this cave). */
    public ResourceKey<PlacedFeature> placed() {
        return switch (this) {
            case FIRE_CAVE -> IafPlacedFeatures.PLACED_FIRE_DRAGON_CAVE;
            case ICE_CAVE -> IafPlacedFeatures.PLACED_ICE_DRAGON_CAVE;
            case LIGHTNING_CAVE -> IafPlacedFeatures.PLACED_LIGHTNING_DRAGON_CAVE;
        };
    }

    /** The dragon the cave holds (Ice and Fire's getDragonType of the matching feature). */
    public EntityType<?> dragonType() {
        return switch (this) {
            case FIRE_CAVE -> IafEntityRegistry.FIRE_DRAGON.get();
            case ICE_CAVE -> IafEntityRegistry.ICE_DRAGON.get();
            case LIGHTNING_CAVE -> IafEntityRegistry.LIGHTNING_DRAGON.get();
        };
    }
}
