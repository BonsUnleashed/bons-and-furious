package bons.furious.mixin.datapack_selectors;

import bons.furious.patch.datapack_selectors.SelectorParserState;
import bons.furious.patch.datapack_selectors.SelectorPrefilter;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.function.Predicate;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_selector_type_index (Minecraft 1.20.1, both sides; acts where selectors are parsed): inside the type option
 * handler (EntitySelectorOptions m_121533_) the values it computes are noted on the parser as they are produced (the
 * negation flag from shouldInvertValue, the TagKey from TagKey.create, the EntityType from the registry lookup's
 * orElseThrow; each returned unchanged), and before each of its two addPredicate calls (tag branch, id branch) the parser
 * is offered a TypeFirstOption with those values around the predicate being added. The predicate object and the
 * addPredicate call are vanilla's, unchanged.
 */
@Mixin(value = EntitySelectorOptions.class, remap = false)
public abstract class EntitySelectorTypeOptionMixin {
    @ModifyExpressionValue(method = "m_121533_", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/commands/arguments/selector/EntitySelectorParser;m_121330_()Z"))
    private static boolean bons$noteNegated(boolean negated, @Local(argsOnly = true) EntitySelectorParser parser) {
        if ((Object) parser instanceof SelectorParserState state) state.bons$noteTypeOption(negated, null, null);
        return negated;
    }

    @ModifyExpressionValue(method = "m_121533_", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/tags/TagKey;m_203882_(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/tags/TagKey;"))
    private static TagKey<EntityType<?>> bons$noteTag(TagKey<EntityType<?>> tag, @Local(argsOnly = true) EntitySelectorParser parser) {
        if ((Object) parser instanceof SelectorParserState state) state.bons$noteTypeOption(state.bons$typeNegated(), tag, null);
        return tag;
    }

    @ModifyExpressionValue(method = "m_121533_", require = 0, at = @At(value = "INVOKE",
            target = "Ljava/util/Optional;orElseThrow(Ljava/util/function/Supplier;)Ljava/lang/Object;"))
    private static Object bons$noteType(Object type, @Local(argsOnly = true) EntitySelectorParser parser) {
        if ((Object) parser instanceof SelectorParserState state && type instanceof EntityType<?> single) {
            state.bons$noteTypeOption(state.bons$typeNegated(), null, single);
        }
        return type;
    }

    @WrapOperation(method = "m_121533_", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/commands/arguments/selector/EntitySelectorParser;m_121272_(Ljava/util/function/Predicate;)V"))
    private static void bons$offerTypeOption(EntitySelectorParser parser, Predicate<Entity> predicate, Operation<Void> original) {
        if ((Object) parser instanceof SelectorParserState state && (state.bons$typeTag() != null) != (state.bons$typeSingle() != null)) {
            state.bons$offerFirstOption(new SelectorPrefilter.TypeFirstOption(predicate, state.bons$typeTag(), state.bons$typeSingle(),
                    state.bons$typeNegated()));
        }
        original.call(parser, predicate);
    }
}
