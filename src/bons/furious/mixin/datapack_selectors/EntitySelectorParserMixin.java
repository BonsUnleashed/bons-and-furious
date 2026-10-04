package bons.furious.mixin.datapack_selectors;

import bons.furious.patch.datapack_selectors.SelectorParserState;
import bons.furious.patch.datapack_selectors.SelectorPrefilter;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.function.Predicate;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_selector_type_index (Minecraft 1.20.1 on Forge 47.4.16, both sides; acts where selectors are parsed):
 * parseSelector (m_121281_) remembers the predicate object "@e" sets
 * (Entity::isAlive, its only write of the predicate field); an option handler's filter is kept only while the parser's
 * predicate is still that object, i.e. when its predicate is the first one; getSelector (m_121230_) hands the filter to
 * the new selector when no single entity type is set (SelectorPrefilter.attach). Parse-time only; the parser's own
 * fields and its result are unchanged.
 */
@Mixin(value = EntitySelectorParser.class, remap = false)
public abstract class EntitySelectorParserMixin implements SelectorParserState {
    @Shadow
    private Predicate<Entity> f_121170_;
    @Shadow
    private EntityType<?> f_121185_;
    @Unique
    private Predicate<Entity> bons$base;
    @Unique
    private EntityTypeTest<Entity, Entity> bons$filter;

    @Inject(method = "m_121281_", require = 0, at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER,
            target = "Lnet/minecraft/commands/arguments/selector/EntitySelectorParser;f_121170_:Ljava/util/function/Predicate;"))
    private void bons$rememberBase(CallbackInfo ci) {
        this.bons$base = this.f_121170_;
    }

    @ModifyReturnValue(method = "m_121230_", require = 0, at = @At("RETURN"))
    private EntitySelector bons$attachPrefilter(EntitySelector selector) {
        return SelectorPrefilter.attach(selector, this.bons$filter, this.f_121185_);
    }

    @Override
    public void bons$offerFirstOption(EntityTypeTest<Entity, Entity> filter) {
        if (this.bons$filter == null && this.bons$base != null && this.f_121170_ == this.bons$base) {
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
