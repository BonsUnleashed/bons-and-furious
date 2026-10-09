package bons.furious.mixin.datapack_selectors;

import bons.furious.patch.datapack_selectors.SelectorFind;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * vanilla_selector_find_loop (Minecraft 1.20.1 on Forge 47.4.16; server side incl. the integrated server):
 * EntitySelector.findEntities (m_121160_) is `findEntitiesRaw(source).stream().filter(<its own lambda m_244752_>).toList()`.
 * With the switch on, the same raw list is walked in its iteration order and the class's own filter lambda is called once
 * per entity, as the sequential stream calls it; the kept entities become a list made by Stream.toList itself
 * (SelectorFind.toList: the raw list's own stream when nothing was dropped, else an array stream of the kept ones), so the
 * result has the same class (an unmodifiable, null-tolerant ImmutableCollections.ListN), content and order. Only the
 * filter stage of the pipeline (and its spined buffer) is gone. No Minecraft code: the filter is the class's own lambda.
 * Shadow mode (SelectorFind.SHADOW) also runs vanilla's pipeline over the same raw list and compares the two lists.
 */
@Mixin(value = EntitySelector.class, remap = false)
public abstract class EntitySelectorFindMixin {
    @Shadow
    private List<? extends Entity> m_245733_(CommandSourceStack source) throws CommandSyntaxException {
        throw new AssertionError();
    }

    @Shadow
    private static boolean m_244752_(CommandSourceStack source, Entity entity) {
        throw new AssertionError();
    }

    @WrapMethod(method = "m_121160_")
    private List<? extends Entity> bons$findWithoutFilterStage(CommandSourceStack source, Operation<List<? extends Entity>> original) throws CommandSyntaxException {
        if (!SelectorFind.enabled) return original.call(source);
        List<? extends Entity> raw = this.m_245733_(source);
        Entity[] kept = null;
        int k = 0, i = 0;
        for (Entity e : raw) {
            if (m_244752_(source, e)) {
                if (kept != null) kept[k] = e;
                k++;
            } else if (kept == null) {
                kept = new Entity[raw.size()];
                int j = 0;
                for (Entity before : raw) {   // the entities before this one were all kept
                    if (j == i) break;
                    kept[j++] = before;
                }
            }
            i++;
        }
        List<? extends Entity> result = SelectorFind.toList(raw, kept, k);
        if (SelectorFind.SHADOW) {
            SelectorFind.shadowCompare(result, raw.stream().filter(e -> m_244752_(source, e)).toList());
        }
        return result;
    }
}
