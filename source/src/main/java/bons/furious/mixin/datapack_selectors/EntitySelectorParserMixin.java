package bons.furious.mixin.datapack_selectors;

import bons.furious.patch.datapack_selectors.SelectorParserState;
import bons.furious.patch.datapack_selectors.SelectorPrefilter;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_selector_type_index (Minecraft 1.21.1 with NeoForge 21.1.252, both sides; acts where selectors are parsed):
 * right after parseSelector's only predicate write (the "@e"/"@n" branch adds Entity::isAlive to the predicate list) the
 * parser remembers that object when it is the list's only element; an option handler's filter is kept only while the
 * list still holds just that object, i.e. when its predicate is the first one; getSelector hands the filter to the new
 * selector when no single entity type is set (SelectorPrefilter.attach). Parse-time only; the parser's own fields and
 * its result are unchanged.
 *
 * Ported to 1.21.1: the parser keeps a List of predicates (vanilla 1.20.1 chained one Predicate field with and()), so
 * the base is taken after parseSelector's List.add and "still first" is "the list is [base]".
 */
@Mixin(value = EntitySelectorParser.class, remap = false)
public abstract class EntitySelectorParserMixin implements SelectorParserState {
    @Shadow
    @Final
    private List<Predicate<Entity>> predicates;
    @Shadow
    private EntityType<?> type;
    @Unique
    private Predicate<Entity> bons$base;
    @Unique
    private EntityTypeTest<Entity, Entity> bons$filter;

    @Inject(method = "parseSelector", require = 0, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private void bons$rememberBase(CallbackInfo ci) {
        this.bons$base = this.predicates.size() == 1 ? this.predicates.get(0) : null;
    }

    @ModifyReturnValue(method = "getSelector", require = 0, at = @At("RETURN"))
    private EntitySelector bons$attachPrefilter(EntitySelector selector) {
        return SelectorPrefilter.attach(selector, this.bons$filter, this.type);
    }

    @Override
    public void bons$offerFirstOption(EntityTypeTest<Entity, Entity> filter) {
        if (this.bons$filter == null && this.bons$base != null && this.predicates.size() == 1 && this.predicates.get(0) == this.bons$base) {
            this.bons$filter = filter;
        }
    }

    @Unique
    private boolean bons$typeNegated;
    @Unique
    private TagKey<EntityType<?>> bons$typeTag;
    @Unique
    private EntityType<?> bons$typeSingle;

    @Override
    public void bons$noteTypeOption(boolean negated, TagKey<EntityType<?>> tag, EntityType<?> single) {
        this.bons$typeNegated = negated;
        this.bons$typeTag = tag;
        this.bons$typeSingle = single;
    }

    @Override
    public boolean bons$typeNegated() {
        return this.bons$typeNegated;
    }

    @Override
    public TagKey<EntityType<?>> bons$typeTag() {
        return this.bons$typeTag;
    }

    @Override
    public EntityType<?> bons$typeSingle() {
        return this.bons$typeSingle;
    }
}
