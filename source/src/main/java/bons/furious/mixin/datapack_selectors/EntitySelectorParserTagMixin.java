package bons.furious.mixin.datapack_selectors;

import bons.furious.patch.datapack_selectors.TagIndex;
import bons.furious.patch.datapack_selectors.TagParserState;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_selector_tag_index (Minecraft 1.21.1 with NeoForge 21.1.252, both sides; acts where selectors are parsed):
 * right after parseSelector's only predicate write (the "@e"/"@n" branch adds Entity::isAlive to the predicate list) the
 * list's content is remembered as the "pure" prefix when it is exactly that one object. Each addPredicate call extends the
 * pure prefix only when the type or tag option handler named that very predicate object as pure just before, and the list
 * is exactly the pure prefix plus that object afterwards; any other predicate (or any other change of the list) ends the
 * pure prefix for good. A tag option is kept when it is positive and not empty, the list is exactly the pure prefix when
 * the handler offers it, and its own predicate then extends the prefix. getSelector gives the kept tags to the new
 * selector (TagIndex.attach) when the list it copied still starts with the prefix as it stood after the last kept tag.
 * Parse-time only; the parser's own fields and its result are unchanged.
 *
 * Ported to 1.21.1: the parser keeps a List of predicates (vanilla 1.20.1 chained one Predicate field with and(), whose
 * identity told the pure prefix apart), so the prefix is a snapshot of the list's elements compared by identity, and a
 * tag counts only once its own predicate object is in the list right behind the pure prefix (1.20.1 kept it at the
 * offer). The base is taken after parseSelector's List.add, as vanilla_selector_type_index's parser mixin does; "@n" (new
 * in 1.21) adds the same Entity::isAlive and is covered by the same argument (its sort and limit run after the scan).
 */
@Mixin(value = EntitySelectorParser.class, remap = false)
public abstract class EntitySelectorParserTagMixin implements TagParserState {
    @Shadow
    @Final
    private List<Predicate<Entity>> predicates;
    /** The list's elements while every one of them is pure; null before parseSelector's base and once the prefix ended. */
    @Unique
    private Object[] bons$pure;
    /** The predicate object a handler marked pure for its next addPredicate call, and the tag it tests when kept. */
    @Unique
    private Object bons$pureNext;
    @Unique
    private String bons$tagNext;
    /** The pure prefix as it stood right after the last kept tag's predicate was added. */
    @Unique
    private Object[] bons$tagPrefix;
    @Unique
    private boolean bons$tagNegated;
    @Unique
    private String bons$tagValue;
    @Unique
    private ArrayList<String> bons$tags;

    @Inject(method = "parseSelector", require = 0, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private void bons$tagBase(CallbackInfo ci) {
        this.bons$pure = this.predicates.size() == 1 ? new Object[] {this.predicates.get(0)} : null;
    }

    @Inject(method = "addPredicate", require = 0, at = @At("RETURN"))
    private void bons$afterAddPredicate(Predicate<Entity> predicate, CallbackInfo ci) {
        Object expected = this.bons$pureNext;
        String tag = this.bons$tagNext;
        this.bons$pureNext = null;
        this.bons$tagNext = null;
        Object[] pure = this.bons$pure;
        if (pure == null) return;
        int n = pure.length;
        if (expected != null && expected == predicate && this.predicates.size() == n + 1 && this.predicates.get(n) == predicate
                && this.bons$startsWith(pure)) {
            Object[] grown = Arrays.copyOf(pure, n + 1);
            grown[n] = predicate;
            this.bons$pure = grown;
            if (tag != null) {
                if (this.bons$tags == null) this.bons$tags = new ArrayList<>(2);
                if (!this.bons$tags.contains(tag)) this.bons$tags.add(tag);
                this.bons$tagPrefix = grown;
            }
        } else {
            this.bons$pure = null;   // any other predicate, or any other change of the list, ends the pure prefix
        }
    }

    @ModifyReturnValue(method = "getSelector", require = 0, at = @At("RETURN"))
    private EntitySelector bons$attachTags(EntitySelector selector) {
        // the kept tags' predicates must still sit behind the same pure prefix in the list getSelector just copied
        if (this.bons$tags == null || this.bons$tagPrefix == null || !this.bons$startsWith(this.bons$tagPrefix)) return selector;
        return TagIndex.attach(selector, this.bons$tags);
    }

    @Unique
    private boolean bons$startsWith(Object[] prefix) {
        List<Predicate<Entity>> list = this.predicates;
        if (list.size() < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (list.get(i) != prefix[i]) return false;
        }
        return true;
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
    public void bons$offerTag(Predicate<?> predicate) {
        String tag = this.bons$tagValue;
        Object[] pure = this.bons$pure;
        boolean usable = !this.bons$tagNegated && tag != null && !tag.isEmpty() && pure != null
                && this.predicates.size() == pure.length && this.bons$startsWith(pure);
        this.bons$pureNext = predicate;
        this.bons$tagNext = usable ? tag : null;
        this.bons$tagValue = null;
    }

    @Override
    public void bons$markPure(Predicate<?> predicate) {
        this.bons$pureNext = predicate;
        this.bons$tagNext = null;
    }
}
