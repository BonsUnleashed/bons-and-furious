package bons.furious.mixin.terramity_look;

import bons.furious.patch.terramity_look.TerramityClientScans;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * terramity_client_scan_skip (Terramity 0.9.8; client side; since 1.0.45): the Malware and Phantom Amulet tick procedures
 * query every entity within 15 blocks and sort them to give the living ones an effect, which only happens on the server; on
 * a client level the query returns a new empty list (TerramityClientScans.auraTargets). Our own logic only.
 */
@Mixin(targets = {"net.mcreator.terramity.procedures.MalwareWhileBaubleIsEquippedTickProcedure",
        "net.mcreator.terramity.procedures.PhantomAmuletEquippedTickProcedure"}, remap = false)
public abstract class TerramityAuraScanMixin {
    @WrapOperation(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelAccessor;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            remap = false)
    private static List<Entity> bons$clientAura(LevelAccessor world, Class<Entity> type, AABB box, Predicate<? super Entity> test, Operation<List<Entity>> original) {
        return TerramityClientScans.auraTargets(world, type, box, test, original);
    }
}
