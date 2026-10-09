package bons.furious.mixin.datapack_selectors;

import bons.furious.patch.datapack_selectors.TagParserState;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.function.Predicate;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_selector_tag_index (Minecraft 1.20.1, both sides; acts where selectors are parsed): inside the tag option
 * handler (EntitySelectorOptions m_121529_, whose predicate is m_175163_: getTags().contains(tag) / isEmpty() != negated)
 * the negation flag (shouldInvertValue) and the tag string (readUnquotedString) are noted on the parser as they are
 * produced (each returned unchanged), and before its addPredicate call the parser is offered the tag and told that the
 * predicate being added is a pure one. The predicate object and the addPredicate call are vanilla's, unchanged.
 */
@Mixin(value = EntitySelectorOptions.class, remap = false)
public abstract class EntitySelectorTagOptionMixin {
    @ModifyExpressionValue(method = "m_121529_", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/commands/arguments/selector/EntitySelectorParser;m_121330_()Z"))
    private static boolean bons$noteTagNegated(boolean negated, @Local(argsOnly = true) EntitySelectorParser parser) {
        if ((Object) parser instanceof TagParserState state) state.bons$noteTagOption(negated, null);
        return negated;
    }

    @ModifyExpressionValue(method = "m_121529_", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/mojang/brigadier/StringReader;readUnquotedString()Ljava/lang/String;"))
    private static String bons$noteTagValue(String tag, @Local(argsOnly = true) EntitySelectorParser parser) {
        if ((Object) parser instanceof TagParserState state) state.bons$noteTagOption(state.bons$tagNegated(), tag);
        return tag;
    }

    @WrapOperation(method = "m_121529_", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/commands/arguments/selector/EntitySelectorParser;m_121272_(Ljava/util/function/Predicate;)V"))
    private static void bons$offerTag(EntitySelectorParser parser, Predicate<Entity> predicate, Operation<Void> original) {
        if ((Object) parser instanceof TagParserState state) {
            state.bons$offerTag();
            state.bons$markPure();
        }
        original.call(parser, predicate);
    }
}
