package bons.furious.mixin.enigmaticlegacy_coo49;

import bons.furious.patch.enigmaticlegacy_coo49.GuardianHeartGate;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Iterator;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * enigmaticlegacy_guardian_heart_idle_gate (Enigmatic Legacy 2.30.1, server side; Enigmatic Legacy's own licence,
 * treated as All Rights Reserved: no code of the mod is carried). Three MixinExtras @WrapOperation in
 * GuardianHeart.inventoryTick (m_6883_): the monster search (Level.getEntitiesOfClass, the method's only call of it)
 * returns a deferred list; the ability's loop (first List.iterator call) makes the search as shipped; the guardian loop
 * (third List.iterator call) uses that list or, when the ability did not run, a guardian-only search of the same box
 * (GuardianHeartGate). The second List.iterator call (the enraged monster's neighbours) is not touched.
 */
@Mixin(targets = "com.aizistral.enigmaticlegacy.items.GuardianHeart", remap = false)
public abstract class ElGuardianHeartGateMixin {
    @WrapOperation(method = "m_6883_(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;IZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;", ordinal = 0),
            require = 1, allow = 1)
    private List<?> bons$enigmaticlegacyCoo49HeartMonsters(Level level, Class<?> cls, AABB box, Operation<List<?>> original) {
        return GuardianHeartGate.monsters(level, cls, box, original);
    }

    @WrapOperation(method = "m_6883_(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;IZ)V",
            at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;", ordinal = 0),
            require = 1, allow = 1)
    private Iterator<?> bons$enigmaticlegacyCoo49HeartAbilityLoop(List<?> list, Operation<Iterator<?>> original) {
        return GuardianHeartGate.abilityLoop(list, original);
    }

    @WrapOperation(method = "m_6883_(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;IZ)V",
            at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;", ordinal = 2),
            require = 1, allow = 1)
    private Iterator<?> bons$enigmaticlegacyCoo49HeartGuardianLoop(List<?> list, Operation<Iterator<?>> original) {
        return GuardianHeartGate.guardianLoop(list, original);
    }
}
