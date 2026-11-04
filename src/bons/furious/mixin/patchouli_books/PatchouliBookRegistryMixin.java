package bons.furious.mixin.patchouli_books;

import bons.furious.patch.patchouli_books.PatchouliBooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vazkii.patchouli.common.book.BookRegistry;

/**
 * patchouli_book_item_prefilter (Patchouli 1.20.1-84.1-FORGE, client). Since 1.0.49.
 *
 * MixinExtras @WrapOperation of the books.put(id, new Book(...)) call in BookRegistry.loadBook, the only place Patchouli
 * writes its book map: the put runs unchanged, then PatchouliBooks.booksChanged() makes the prefilter re-read the map (a
 * replaced book keeps the map's size, so the size alone would not show it). Carries no Patchouli code.
 */
@Mixin(value = BookRegistry.class, remap = false)
public abstract class PatchouliBookRegistryMixin {
    @WrapOperation(method = "loadBook", remap = false, at = @At(value = "INVOKE",
            target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", remap = false))
    private Object bons$bookPut(Map<Object, Object> books, Object id, Object book, Operation<Object> original) {
        try {
            return original.call(books, id, book);
        } finally {
            PatchouliBooks.booksChanged();
        }
    }
}
