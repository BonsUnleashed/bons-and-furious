package bons.furious.mixin.summonity_coo49;

import bons.furious.patch.summonity_coo49.DroneIds;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * summonity_drone_modifier_id_memo (Summonity 0.1.7.1, server side; All Rights Reserved: no code of the mod is carried).
 * MixinExtras @WrapMethod on SummonedCopperDroneEntity.modifierId(String): a pure function of the drone's UUID and the
 * bonus name (a name-based UUID of "<uuid>:<bonus>"), asked twice per drone per tick for "armor" and "speed". The two
 * answers are kept per drone with the UUID object they were made from and handed out again while getUUID() still returns
 * that object (DroneIds); any other bonus name, or a new UUID, runs the method as shipped.
 */
@Mixin(targets = "com.fevzi.summonity.entity.SummonedCopperDroneEntity", remap = false)
public abstract class SummonityDroneIdMixin {
    @Unique
    private UUID bons$summonityCoo49DroneFrom;
    @Unique
    private UUID bons$summonityCoo49DroneArmor;
    @Unique
    private UUID bons$summonityCoo49DroneSpeed;

    @WrapMethod(method = "modifierId(Ljava/lang/String;)Ljava/util/UUID;")
    private UUID bons$summonityCoo49DroneId(String bonus, Operation<UUID> original) {
        if (!DroneIds.enabled) return original.call(bonus);
        boolean armor = "armor".equals(bonus);
        if (!armor && !"speed".equals(bonus)) return original.call(bonus);
        UUID self = ((Entity) (Object) this).m_20148_();
        if (self != this.bons$summonityCoo49DroneFrom || self == null) {
            this.bons$summonityCoo49DroneFrom = self;
            this.bons$summonityCoo49DroneArmor = null;
            this.bons$summonityCoo49DroneSpeed = null;
        }
        UUID kept = armor ? this.bons$summonityCoo49DroneArmor : this.bons$summonityCoo49DroneSpeed;
        if (kept == null) {
            kept = original.call(bonus);
            if (self != null) {
                if (armor) this.bons$summonityCoo49DroneArmor = kept;
                else this.bons$summonityCoo49DroneSpeed = kept;
            }
            return kept;
        }
        DroneIds.reused(kept, bonus, original);
        return kept;
    }
}
