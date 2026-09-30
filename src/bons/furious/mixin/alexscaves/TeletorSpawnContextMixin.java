package bons.furious.mixin.alexscaves;

import com.github.alexmodguy.alexscaves.server.entity.ACEntityRegistry;
import com.github.alexmodguy.alexscaves.server.entity.item.MagneticWeaponEntity;
import com.github.alexmodguy.alexscaves.server.entity.living.TeletorEntity;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * alexscaves_teletor_generation_context (Alex's Caves 2.0.2).
 *
 * TeletorEntity.finalizeSpawn drew four random numbers from the live level and added its floating weapon to the live
 * level, even when the spawn happened inside a world-generation region. On a C2ME worker thread that random read stops
 * generation with a ConcurrentModificationException. When the spawn is handed a WorldGenRegion, the random and the
 * weapon insertion now come from that region; otherwise nothing changes.
 */
@Mixin(value = TeletorEntity.class, remap = false)
public abstract class TeletorSpawnContextMixin extends Monster {
    protected TeletorSpawnContextMixin(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Shadow
    public abstract ItemStack createItemStack(RandomSource random);

    @Shadow
    public abstract Vec3 getWeaponPosition();

    @Shadow
    public abstract void setWeaponUUID(UUID uuid);

    /**
     * @author BonsUnleashed
     * @reason Take the random source and the weapon insertion from a generation region when spawning inside one.
     */
    @Overwrite
    public SpawnGroupData m_6518_(ServerLevelAccessor level, DifficultyInstance difficultyIn, MobSpawnType reason,
                                  SpawnGroupData spawnDataIn, CompoundTag dataTag) {
        MagneticWeaponEntity magneticWeapon = ACEntityRegistry.MAGNETIC_WEAPON.get().m_20615_(this.m_9236_());
        ItemStack stack = this.createItemStack(ac$spawnRandom(this.m_9236_(), level));
        float f = difficultyIn.m_19057_();
        if (ac$spawnRandom(this.m_9236_(), level).m_188501_() < 0.25F * (f + 0.5F)) {
            stack = EnchantmentHelper.m_220292_(ac$spawnRandom(this.m_9236_(), level), stack,
                    (int) (5.0F + f * ac$spawnRandom(this.m_9236_(), level).m_188503_(18)), false);
        }
        magneticWeapon.setItemStack(stack);
        magneticWeapon.m_146884_(this.getWeaponPosition());
        magneticWeapon.setControllerUUID(this.m_20148_());
        this.setWeaponUUID(magneticWeapon.m_20148_());
        ac$addSpawnEntity(this.m_9236_(), magneticWeapon, level);
        return super.m_6518_(level, difficultyIn, reason, spawnDataIn, dataTag);
    }

    /** The generation region's random when spawning inside one, else the level's random (as before). */
    @Unique
    private static RandomSource ac$spawnRandom(Level level, ServerLevelAccessor accessor) {
        if (accessor instanceof WorldGenRegion) {
            return ((WorldGenRegion) accessor).m_213780_();
        }
        return level.m_213780_();
    }

    /** Adds the weapon to the generation region when spawning inside one, else to the level (as before). */
    @Unique
    private static boolean ac$addSpawnEntity(Level level, Entity entity, ServerLevelAccessor accessor) {
        if (accessor instanceof WorldGenRegion) {
            return ((WorldGenRegion) accessor).m_7967_(entity);
        }
        return level.m_7967_(entity);
    }
}
