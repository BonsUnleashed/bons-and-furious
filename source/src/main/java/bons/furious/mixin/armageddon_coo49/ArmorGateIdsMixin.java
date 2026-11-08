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
 * armageddon_progression_id_memo (Armageddon 3.3.0b for NeoForge 1.21.1; both sides; exact variant), the twelve procedures
 * whose private execute takes (Event, LevelAccessor, x, y, z, Entity): the eleven armour-tier gates and the enchanted-armour
 * gate (their PlayerTickEvent.Post handlers call it). MixinExtras @WrapOperation on every ResourceLocation.parse,
 * ItemTags.create, TagKey.create and ResourceLocation.toString call in that method: ProgressionIds hands out the kept result
 * (the same values; see its class comment). Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = {
        "net.mcreator.armageddonmod.procedures.BloodyArmorEquippedPreventionProcedureProcedure",
        "net.mcreator.armageddonmod.procedures.CALArmorPreventionProcedureProcedure",
        "net.mcreator.armageddonmod.procedures.DiamondArmorPreventionProcedureProcedure",
        "net.mcreator.armageddonmod.procedures.ElveniteArmorPreventionProcedureProcedure",
        "net.mcreator.armageddonmod.procedures.EmeraldArmorPreventionProcedure",
        "net.mcreator.armageddonmod.procedures.EnchantedArmorPreventionProcedure",
        "net.mcreator.armageddonmod.procedures.GoldArmorPreventionProcedrueProcedure",
        "net.mcreator.armageddonmod.procedures.HelioniteArmorEquippedPreventionProcedureProcedure",
        "net.mcreator.armageddonmod.procedures.IronArmorPreventionProcedureProcedure",
        "net.mcreator.armageddonmod.procedures.NetheriteArmorPreventionProcedureProcedure",
        "net.mcreator.armageddonmod.procedures.ShadowArmorEquippedPreventionProcedureProcedure",
        "net.mcreator.armageddonmod.procedures.VoideriteArmorPreventionProcedureProcedure"}, remap = false)
public abstract class ArmorGateIdsMixin {
    @WrapOperation(method = "execute(Lnet/neoforged/bus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/resources/ResourceLocation;parse(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"),
            require = 1)
    private static ResourceLocation bons$armageddonCoo49Parse(String id, Operation<ResourceLocation> original) {
        return ProgressionIds.parse(id, original);
    }

    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "execute(Lnet/neoforged/bus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/tags/ItemTags;create(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/tags/TagKey;"),
            require = 0, expect = 0)
    private static TagKey bons$armageddonCoo49ItemTag(ResourceLocation id, Operation<TagKey> original) {
        return ProgressionIds.itemTag(id, original);
    }

    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "execute(Lnet/neoforged/bus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/tags/TagKey;create(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/tags/TagKey;"),
            require = 0, expect = 0)
    private static TagKey bons$armageddonCoo49Tag(ResourceKey registry, ResourceLocation id, Operation<TagKey> original) {
        return ProgressionIds.tag(registry, id, original);
    }

    @WrapOperation(method = "execute(Lnet/neoforged/bus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/resources/ResourceLocation;toString()Ljava/lang/String;"),
            require = 0, expect = 0)
    private static String bons$armageddonCoo49IdString(ResourceLocation id, Operation<String> original) {
        return ProgressionIds.idString(id, original);
    }
}
