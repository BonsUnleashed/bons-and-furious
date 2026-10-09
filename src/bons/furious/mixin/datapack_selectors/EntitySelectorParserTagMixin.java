package bons.furious.mixin.datapack_selectors;

import bons.furious.patch.datapack_selectors.TagIndex;
import bons.furious.patch.datapack_selectors.TagParserState;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.ArrayList;
import java.util.function.Predicate;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.world.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_selector_tag_index (Minecraft 1.20.1 on Forge 47.4.16, both sides; acts where selectors are parsed): after
 * parseSelector (m_121281_) writes "@e"'s base predicate (Entity::isAlive, its only write of the predicate field) that
 * object is remembered as the last "pure" state; addPredicate (m_121272_) moves the pure state on only when the type or
 * tag option handler marked the call and the predicate field still held the pure state before it, so any other option's
 * predicate (or any other write of the field) ends the pure prefix. A tag option offered while the field holds the pure
 * state is kept (positive, non-empty only), and getSelector (m_121230_) gives the kept tags to the new selector
 * (TagIndex.attach). Parse-time only; the parser's own fields and its result are unchanged.
 */
@Mixin(value = EntitySelectorParser.class, remap = false)
public abstract class EntitySelectorParserTagMixin implements TagParserState {
    @Shadow
    private Predicate<Entity> f_121170_;
    @Unique
    private Predicate<Entity> bons$lastPure;
    @Unique
    private Predicate<Entity> bons$beforeAdd;
    @Unique
    private boolean bons$purePending;
    @Unique
    private boolean bons$tagNegated;
    @Unique
    private String bons$tagValue;
    @Unique
    private ArrayList<String> bons$tags;

    @Inject(method = "m_121281_", require = 0, at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER,
            target = "Lnet/minecraft/commands/arguments/selector/EntitySelectorParser;f_121170_:Ljava/util/function/Predicate;"))
    private void bons$tagBase(CallbackInfo ci) {
        this.bons$lastPure = this.f_121170_;
    }

    @Inject(method = "m_121272_", require = 0, at = @At("HEAD"))
    private void bons$beforeAddPredicate(Predicate<Entity> predicate, CallbackInfo ci) {
        this.bons$beforeAdd = this.f_121170_;
    }

    @Inject(method = "m_121272_", require = 0, at = @At("RETURN"))
    private void bons$afterAddPredicate(Predicate<Entity> predicate, CallbackInfo ci) {
        if (this.bons$purePending) {
            this.bons$purePending = false;
            if (this.bons$lastPure != null && this.bons$beforeAdd == this.bons$lastPure) this.bons$lastPure = this.f_121170_;
        }
        this.bons$beforeAdd = null;
    }

    @ModifyReturnValue(method = "m_121230_", require = 0, at = @At("RETURN"))
    private EntitySelector bons$attachTags(EntitySelector selector) {
        return TagIndex.attach(selector, this.bons$tags);
    }

    @Override
    public void bons$noteTagOption(boolean negated, String tag) {
        this.bons$tagNegated = negated;
        this.bons$tagValue = tag;
    }

    @Override
    public boolean bons$tagNegated() {
        return this.bons$tagNegated;
    }

    @Override
    public void bons$offerTag() {
        String tag = this.bons$tagValue;
        if (!this.bons$tagNegated && tag != null && !tag.isEmpty() && this.bons$lastPure != null && this.f_121170_ == this.bons$lastPure) {
            if (this.bons$tags == null) this.bons$tags = new ArrayList<>(2);
            if (!this.bons$tags.contains(tag)) this.bons$tags.add(tag);
        }
        this.bons$tagValue = null;
    }

    @Override
    public void bons$markPure() {
        this.bons$purePending = true;
    }
}
