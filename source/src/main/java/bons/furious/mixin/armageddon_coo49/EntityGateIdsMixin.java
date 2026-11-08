package bons.furious.mixin.armageddon_coo49;

import bons.furious.patch.armageddon_coo49.ProgressionIds;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * armageddon_progression_id_memo (Armageddon 3.3.0b for NeoForge 1.21.1; both sides; exact variant), the three procedures
 * whose private execute takes (Event, LevelAccessor, Entity): the Hollow pickaxe tick, the mob health / damage scaling and
 * the Nether gate. The same wrappers as ArmorGateIdsMixin. Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = {
        "net.mcreator.armageddonmod.procedures.HollowPickaxeTickUpdateProcedure",
        "net.mcreator.armageddonmod.procedures.IncreaseLifeOfMobsAndDamgetestProcedureProcedure",
        "net.mcreator.armageddonmod.procedures.NetherPreventionProcedureProcedure"}, remap = false)
public abstract class EntityGateIdsMixin {
    @WrapOperation(method = "execute(Lnet/neoforged/bus/api/Event;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/resources/ResourceLocation;parse(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"),
            require = 1)
    private static ResourceLocation bons$armageddonCoo49Parse(String id, Operation<ResourceLocation> original) {
        return ProgressionIds.parse(id, original);
    }

    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "execute(Lnet/neoforged/bus/api/Event;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/tags/ItemTags;create(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/tags/TagKey;"),
            require = 0, expect = 0)
    private static TagKey bons$armageddonCoo49ItemTag(ResourceLocation id, Operation<TagKey> original) {
        return ProgressionIds.itemTag(id, original);
    }

    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "execute(Lnet/neoforged/bus/api/Event;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/tags/TagKey;create(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/tags/TagKey;"),
            require = 0, expect = 0)
    private static TagKey bons$armageddonCoo49Tag(ResourceKey registry, ResourceLocation id, Operation<TagKey> original) {
        return ProgressionIds.tag(registry, id, original);
    }

    @WrapOperation(method = "execute(Lnet/neoforged/bus/api/Event;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/resources/ResourceLocation;toString()Ljava/lang/String;"),
            require = 0, expect = 0)
    private static String bons$armageddonCoo49IdString(ResourceLocation id, Operation<String> original) {
        return ProgressionIds.idString(id, original);
    }
}
