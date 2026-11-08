package bons.furious.mixin.patchouli_books;

import bons.furious.patch.patchouli_books.PatchouliBooks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import vazkii.patchouli.common.book.Book;
import vazkii.patchouli.common.util.ItemStackUtil;

/**
 * patchouli_book_item_prefilter (Patchouli 1.21.1-93-NEOFORGE on Minecraft 1.21.1 / NeoForge 21.1.252, client). Since
 * 1.0.49.
 *
 * MixinExtras @WrapMethod of ItemStackUtil.getBookFromStack(ItemStack): PatchouliBooks answers it from an index of the
 * loaded books' items (same Book or null; see PatchouliBooks for why), or calls the original (ItemModBook stacks, the switch
 * off, shadow mode). Priority 1500: a wrapper still applies when another mod replaces this method at the default priority
 * and then wraps that replacement. Patchouli is CC BY-NC-SA 3.0: no Patchouli code is carried; the wrapper only decides
 * which books Patchouli's comparison is asked about.
 */
@Mixin(value = ItemStackUtil.class, priority = 1500, remap = false)
public abstract class PatchouliBookLookupMixin {
    @WrapMethod(method = "getBookFromStack(Lnet/minecraft/world/item/ItemStack;)Lvazkii/patchouli/common/book/Book;", remap = false)
    private static Book bons$bookFromStack(ItemStack stack, Operation<Book> original) {
        return PatchouliBooks.bookFromStack(stack, original);
    }
}
